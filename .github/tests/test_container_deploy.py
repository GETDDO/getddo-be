"""Docker와 HTTP 경계만 대체해 실제 교체·복구 스크립트를 실행한다."""
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import tempfile
import time
import unittest


ROOT = Path(__file__).resolve().parents[2]
SHA = "1" * 40
NEW = "sha256:" + "a" * 64
OLD = "sha256:" + "b" * 64
NEW_PASSWORD = "synthetic-only-$quote\"#=password"
VALUES = {
    "SPRING_DATASOURCE_URL": "jdbc:mysql://db.example.test:3306/getddo?sslMode=VERIFY_IDENTITY",
    "SPRING_DATASOURCE_USERNAME": "getddo_app",
    "SPRING_DATASOURCE_PASSWORD": NEW_PASSWORD,
    "SPRING_JPA_HIBERNATE_DDL_AUTO": "validate", "SPRING_FLYWAY_ENABLED": "true",
    "CORS_ALLOWED_ORIGINS": "https://frontend.example.test", "AWS_REGION": "ap-northeast-2",
    "GETDDO_IMAGES_BUCKET": "test-getddo-images",
    "GETDDO_IMAGES_BASE_URL": "https://cdn.example.test/images/",
}
FAKE = r'''#!/usr/bin/env python3
import os,pathlib,sys
root=pathlib.Path(os.environ['FAKE_ROOT'])
args=sys.argv[1:]
active=root/'active'
name=pathlib.Path(sys.argv[0]).name
if name=='sleep':
    pass
elif name=='aws':
    assert args[:2]==['ssm','get-parameters'] and '--with-decryption' in args
    assert '--profile' not in args
    if os.environ.get('FAIL_PARAMETERS'):
        print(os.environ['NEW_PASSWORD'],file=sys.stderr)
        sys.exit(1)
    import json
    response=json.loads((root/'parameters.json').read_text())
    if os.environ.get('MISSING_PARAMETER'): response['Parameters'].pop()
    if os.environ.get('INVALID_PARAMETER_TYPE'): response['Parameters'][2]['Type']='String'
    print(json.dumps(response))
elif name=='mv':
    if os.environ.get('FAIL_ENV_RESTORE') and any(a.endswith('/previous.env') for a in args):
        sys.exit(1)
    os.execv('/usr/bin/mv',['/usr/bin/mv',*args])
elif name=='curl':
    current=active.read_text() if active.exists() else ''
    failed=os.environ.get('FAIL_ALL') or (current==os.environ['NEW'] and os.environ.get('FAIL_HEALTH'))
    print('503' if failed else '200',end='')
elif args[:2]==['image','load']:
    (root/'loaded').touch()
    sys.exit(1 if os.environ.get('FAIL_LOAD') else 0)
elif args[:2]==['image','inspect']:
    if '--format' not in args:
        print('{}')
    else:
        print(os.environ.get('IMAGE_ID',os.environ['NEW']) if args[3]=='{{.Id}}' else os.environ.get('REVISION',os.environ['SHA']))
elif args[0]=='inspect':
    print('true '+active.read_text())
elif args[0]=='compose':
    config=args[args.index('--file')+1]
    command=args[args.index('--file')+2:]
    image=os.environ['APP_IMAGE']
    if command[0]=='config':
        assert pathlib.Path(os.environ['APP_ENV_FILE']).is_file()
        sys.exit(1 if os.environ.get('FAIL_CONFIG') else 0)
    elif command[0]=='ps':
        if active.exists() or ('--all' in command and (root/'stopped').exists()): print('container-id')
    elif command[0]=='up':
        assert '--force-recreate' in command
        active.write_text('wrong-image' if image==os.environ['NEW'] and os.environ.get('WRONG_IMAGE') else image)
        (root/'active-env').write_text(pathlib.Path(os.environ['APP_ENV_FILE']).read_text())
        with (root/'changes').open('a') as output: output.write(image+' '+config+'\n')
        if image==os.environ['NEW'] and os.environ.get('HOLD_NEW_UP'):
            import time
            (root/'up-reached').touch()
            deadline=time.monotonic()+10
            while not (root/'continue').exists() and time.monotonic()<deadline: time.sleep(0.02)
        sys.exit(1 if image==os.environ['NEW'] and os.environ.get('FAIL_UP') else 0)
    elif command[0]=='stop':
        active.unlink(missing_ok=True)
    else: raise RuntimeError(args)
else: raise RuntimeError(args)
'''


class ContainerDeployTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.release = self.root / "releases/new"
        self.release.mkdir(parents=True)
        # 운영 경로만 임시 디렉터리로 치환한다. 실제 env 조회·교체·복구 코드는 그대로 실행한다.
        config = self.root / "config"
        config.mkdir()
        self.app_env = config / "app.env"
        self.old_env = "PREVIOUS_SETTING=unchanged\nSPRING_DATASOURCE_PASSWORD=synthetic-old-password\n"
        self.app_env.write_text(self.old_env)
        self.app_env.chmod(0o600)
        script = (ROOT / "infra/scripts/deploy-container.sh").read_text()
        (self.release / "deploy-container.sh").write_text(script.replace("/etc/getddo", str(config)))
        shutil.copyfile(ROOT / "infra/scripts/fetch-app-env.py", self.release / "fetch-app-env.py")
        response = {"Parameters": [
            {"Name": "/getddo/dev/app/" + key, "Value": value,
             "Type": "SecureString" if key == "SPRING_DATASOURCE_PASSWORD" else "String"}
            for key, value in VALUES.items()], "InvalidParameters": []}
        (self.root / "parameters.json").write_text(json.dumps(response))
        (self.release / "compose.deploy.yaml").write_text("services: {}\n")
        archive = self.release / "image.tar.gz"
        archive.write_bytes(gzip.compress(b"synthetic image"))
        digest = hashlib.sha256(archive.read_bytes()).hexdigest()
        (self.release / "image.tar.gz.sha256").write_text(digest + "  image.tar.gz\n")
        (self.release / "image-id.txt").write_text(NEW + "\n")
        binary = self.root / "bin"
        binary.mkdir()
        for name in ("docker", "curl", "sleep", "aws", "mv"):
            path = binary / name
            path.write_text(FAKE)
            path.chmod(0o755)
        self.env = dict(os.environ, PATH=str(binary) + ":" + os.environ["PATH"],
                        FAKE_ROOT=str(self.root), NEW=NEW, SHA=SHA, NEW_PASSWORD=NEW_PASSWORD)

    def previous(self):
        previous = self.root / "releases/old"
        previous.mkdir()
        (previous / "compose.deploy.yaml").write_text("services: {}\n")
        (previous / "image-id.txt").write_text(OLD + "\n")
        (self.root / "current").symlink_to(previous)
        (self.root / "active").write_text(OLD)
        (self.root / "active-env").write_text(self.old_env)
        return previous

    def deploy(self, **flags):
        return subprocess.run(["bash", str(self.release / "deploy-container.sh"), SHA, "ap-northeast-2"],
                              env=dict(self.env, **flags), capture_output=True, text=True, timeout=25)

    def test_success_promotes_healthy_release(self):
        self.previous()
        result = self.deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.root / "current").resolve(), self.release)
        self.assertEqual((self.root / "active").read_text(), NEW)
        self.assertIn("SPRING_DATASOURCE_PASSWORD=" + NEW_PASSWORD + "\n", self.app_env.read_text())
        self.assertEqual(self.app_env.stat().st_mode & 0o777, 0o600)
        self.assertEqual(list(self.app_env.parent.glob(".deploy-env.*")), [])
        self.assertNotIn(NEW_PASSWORD, result.stdout + result.stderr)

    def test_first_deploy_can_succeed(self):
        result = self.deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual((self.root / "current").resolve(), self.release)

    def test_health_or_partial_replace_failure_restores_previous_image_and_config(self):
        previous = self.previous()
        for flag in ("FAIL_HEALTH", "FAIL_UP", "WRONG_IMAGE"):
            with self.subTest(flag=flag):
                result = self.deploy(**{flag: "1"})
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual((self.root / "active").read_text(), OLD)
                self.assertEqual((self.root / "current").resolve(), previous)
                self.assertEqual((self.root / "changes").read_text().splitlines()[-1],
                                 OLD + " " + str(previous / "compose.deploy.yaml"))
                self.assertIn("ROLLBACK_OK", result.stdout)
                self.assertEqual(self.app_env.read_text(), self.old_env)
                self.assertEqual((self.root / "active-env").read_text(), self.old_env)
                self.assertEqual(list(self.app_env.parent.glob(".deploy-env.*")), [])

    def test_first_failed_deploy_stops_app(self):
        self.assertNotEqual(self.deploy(FAIL_HEALTH="1").returncode, 0)
        self.assertFalse((self.root / "active").exists())
        self.assertFalse((self.root / "current").exists())
        self.assertEqual(self.app_env.read_text(), self.old_env)

    def test_first_deploy_without_env_creates_and_on_failure_removes_new_env(self):
        self.app_env.unlink()
        result = self.deploy(FAIL_HEALTH="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.app_env.exists())
        self.assertFalse((self.root / "active").exists())

    def test_parameter_failures_preserve_app_env_and_do_not_leak_values(self):
        self.previous()
        for flag in ("FAIL_PARAMETERS", "MISSING_PARAMETER", "INVALID_PARAMETER_TYPE"):
            with self.subTest(flag=flag):
                result = self.deploy(**{flag: "1"})
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.app_env.read_text(), self.old_env)
                self.assertEqual((self.root / "active").read_text(), OLD)
                self.assertFalse((self.root / "changes").exists())
                self.assertEqual(list(self.app_env.parent.glob(".deploy-env.*")), [])
                self.assertNotIn(NEW_PASSWORD, result.stdout + result.stderr)

    def test_preflight_failures_preserve_running_app(self):
        self.previous()
        for flags in ({"FAIL_CONFIG": "1"}, {"FAIL_LOAD": "1"},
                      {"IMAGE_ID": OLD}, {"REVISION": "2" * 40}):
            with self.subTest(flags=flags):
                self.assertNotEqual(self.deploy(**flags).returncode, 0)
                self.assertEqual((self.root / "active").read_text(), OLD)
                self.assertFalse((self.root / "changes").exists())

    def test_corrupt_archive_is_rejected_before_load(self):
        self.previous()
        (self.release / "image.tar.gz").write_bytes(b"corrupt")
        self.assertNotEqual(self.deploy().returncode, 0)
        self.assertFalse((self.root / "loaded").exists())
        self.assertEqual((self.root / "active").read_text(), OLD)

    def test_failed_recovery_is_not_reported_as_success(self):
        self.previous()
        result = self.deploy(FAIL_ALL="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ROLLBACK_FAILED", result.stderr)
        self.assertNotIn("ROLLBACK_OK", result.stdout)

    def test_failed_env_restore_preserves_private_backup_for_manual_recovery(self):
        self.previous()
        result = self.deploy(FAIL_HEALTH="1", FAIL_ENV_RESTORE="1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ROLLBACK_FAILED", result.stderr)
        backups = list(self.app_env.parent.glob(".deploy-env.*/previous.env"))
        self.assertEqual(len(backups), 1)
        self.assertEqual(backups[0].read_text(), self.old_env)
        self.assertEqual(backups[0].stat().st_mode & 0o777, 0o600)
        self.assertEqual(backups[0].parent.stat().st_mode & 0o777, 0o700)
        self.assertNotIn("ROLLBACK_OK", result.stdout)

    def test_term_after_env_replacement_restores_image_and_env(self):
        self.previous()
        process = subprocess.Popen(
            ["bash", str(self.release / "deploy-container.sh"), SHA, "ap-northeast-2"],
            env=dict(self.env, HOLD_NEW_UP="1"), stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, start_new_session=True,
        )
        try:
            deadline = time.monotonic() + 10
            while not (self.root / "up-reached").exists() and time.monotonic() < deadline:
                self.assertIsNone(process.poll())
                time.sleep(0.02)
            self.assertTrue((self.root / "up-reached").exists())
            process.send_signal(signal.SIGTERM)
            (self.root / "continue").touch()
            output, errors = process.communicate(timeout=20)
            self.assertEqual(process.returncode, 143, errors)
            self.assertEqual(self.app_env.read_text(), self.old_env)
            self.assertEqual((self.root / "active").read_text(), OLD)
            self.assertIn("ROLLBACK_OK", output)
            self.assertEqual(list(self.app_env.parent.glob(".deploy-env.*")), [])
        finally:
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()

    def test_server_lock_blocks_another_deployment(self):
        self.previous()
        with (self.root / "deploy.lock").open("w") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            self.assertNotEqual(self.deploy().returncode, 0)
        self.assertFalse((self.root / "changes").exists())

    def test_unmanaged_existing_app_is_not_replaced(self):
        for running in (True, False):
            with self.subTest(running=running):
                (self.root / "active").unlink(missing_ok=True)
                (self.root / ("active" if running else "stopped")).write_text(OLD)
                self.assertNotEqual(self.deploy().returncode, 0)
                self.assertFalse((self.root / "changes").exists())


if __name__ == "__main__":
    unittest.main()
