"""AWS CLI 경계를 대체해 설정·파일 전달·최종 결과 판정을 확인한다."""
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tarfile
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("deploy_ssm", ROOT / "infra/scripts/deploy-ssm.py")
deploy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(deploy)
ENV = {"AWS_REGION": "ap-northeast-2", "AWS_ROLE_ARN": "arn:aws:iam::123456789012:role/getddo-cd",
       "DEPLOY_BUCKET": "getddo-deploy-files", "EC2_INSTANCE_ID": "i-0123456789abcdef0",
       "SOURCE_SHA": "1" * 40, "GITHUB_REPOSITORY": "Uplus-GETDDO/getddo-be",
       "GITHUB_RUN_ID": "42", "GITHUB_RUN_ATTEMPT": "2", "RUNNER_TEMP": "/tmp"}


class SsmDeployTest(unittest.TestCase):
    def test_missing_configuration_fails_before_any_external_request(self):
        with patch.dict(deploy.os.environ, {}, clear=True), patch.object(deploy, "command") as external:
            with self.assertRaisesRegex(ValueError, "AWS_REGION"):
                deploy.main()
            external.assert_not_called()

    def test_state_bucket_and_shell_input_are_rejected(self):
        for bucket in ("getddo-tfstate-123456789012", "getddo-terraform-state", "name; touch /tmp/invalid"):
            with self.subTest(bucket=bucket), self.assertRaises(ValueError):
                deploy.settings(dict(ENV, DEPLOY_BUCKET=bucket))

    def test_old_main_commit_fails_before_upload(self):
        with patch.object(deploy, "command", return_value="2" * 40):
            with self.assertRaisesRegex(RuntimeError, "main"):
                deploy.verify_main(deploy.settings(ENV))

    def test_check_only_never_uploads_or_sends_a_command(self):
        with patch.dict(deploy.os.environ, ENV, clear=True), patch.object(deploy, "verify_main"), \
             patch.object(deploy, "aws") as aws:
            deploy.main(check_only=True)
            aws.assert_not_called()

    def test_archive_contains_only_deployment_files_and_has_valid_checksum(self):
        with tempfile.TemporaryDirectory() as folder:
            temporary = Path(folder)
            artifact = temporary / "artifact"
            artifact.mkdir()
            for name in ("image.tar.gz", "image.tar.gz.sha256", "image-id.txt", "app.env"):
                (artifact / name).write_text("synthetic")
            destination = temporary / "release.tar"
            digest = deploy.bundle(ROOT, artifact, destination)
            self.assertEqual(digest, hashlib.sha256(destination.read_bytes()).hexdigest())
            with tarfile.open(destination) as archive:
                self.assertEqual(set(archive.getnames()), {"compose.deploy.yaml", "deploy-container.sh",
                                 "fetch-app-env.py", "image.tar.gz", "image.tar.gz.sha256", "image-id.txt"})

    def test_server_bootstrap_executes_only_after_download_checksum_and_extraction(self):
        for corrupt in (False, True):
            with self.subTest(corrupt=corrupt), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                artifact = root / "artifact"
                artifact.mkdir()
                for name in ("image.tar.gz", "image.tar.gz.sha256", "image-id.txt"):
                    (artifact / name).write_text("synthetic")
                archive = root / "input.tar"
                checksum = deploy.bundle(ROOT, artifact, archive)
                if corrupt:
                    archive.write_bytes(b"damaged")
                binary = root / "bin"
                binary.mkdir()
                (binary / "aws").write_text("#!/usr/bin/env python3\nimport os,shutil,sys\n"
                    "assert sys.argv[1:3]==['s3api','get-object']\n"
                    "assert sys.argv[sys.argv.index('--expected-bucket-owner')+1]=='123456789012'\n"
                    "shutil.copyfile(os.environ['INPUT_ARCHIVE'],'release.tar')\n")
                (binary / "bash").write_text("#!/usr/bin/env python3\nimport os,pathlib\n"
                    "assert pathlib.Path('compose.deploy.yaml').exists()\n"
                    "pathlib.Path(os.environ['BOOTSTRAP_MARKER']).touch()\n")
                for name in ("aws", "bash"):
                    (binary / name).chmod(0o755)
                commands = deploy.parameters(deploy.settings(ENV), "releases/test/release.tar", checksum)["commands"][0]
                # 서버의 고정 경로만 임시 폴더로 치환한다. 실제 셸·tar·체크섬 도구를 실행한다.
                commands = commands.replace("/opt/getddo", str(root / "server"))
                env = dict(os.environ, PATH=str(binary) + ":" + os.environ["PATH"],
                           INPUT_ARCHIVE=str(archive), BOOTSTRAP_MARKER=str(root / "executed"))
                result = subprocess.run(["/bin/bash", "-c", commands], env=env,
                                        capture_output=True, text=True, timeout=10)
                self.assertEqual(result.returncode == 0, not corrupt, result.stderr)
                self.assertEqual((root / "executed").exists(), not corrupt)

    def test_upload_and_ssm_request_use_unique_owner_scoped_release(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            (root / "container-image").mkdir()
            for name in ("image.tar.gz", "image.tar.gz.sha256", "image-id.txt"):
                (root / "container-image" / name).write_text("synthetic")
            responses = [{}, {"Command": {"CommandId": "command-42"}}, {"Status": "Success", "ResponseCode": 0}]
            with patch.dict(deploy.os.environ, dict(ENV, RUNNER_TEMP=folder), clear=True), \
                 patch.object(deploy, "verify_main"), patch.object(deploy, "aws", side_effect=responses) as aws:
                deploy.main()
            upload = aws.call_args_list[0].args
            self.assertEqual(upload[:2], ("s3api", "put-object"))
            self.assertEqual(upload[upload.index("--key") + 1], "releases/" + "1" * 40 + "/42-2/release.tar")
            self.assertEqual(upload[upload.index("--if-none-match") + 1], "*")
            self.assertEqual(upload[upload.index("--expected-bucket-owner") + 1], "123456789012")
            self.assertEqual(upload[upload.index("--server-side-encryption") + 1], "AES256")
            send = aws.call_args_list[1].args
            self.assertEqual(send[:2], ("ssm", "send-command"))
            self.assertEqual(send[send.index("--instance-ids") + 1], "i-0123456789abcdef0")

    def test_server_receives_region_but_no_parameter_values(self):
        script = deploy.parameters(deploy.settings(ENV), "releases/example", "a" * 64)["commands"][0]
        self.assertIn("exec bash deploy-container.sh " + "1" * 40 + " ap-northeast-2", script)
        self.assertNotIn("SPRING_DATASOURCE_PASSWORD", script)
        self.assertNotIn("get-parameters", script)

    def test_remote_success_requires_zero_exit_code(self):
        with patch.object(deploy, "aws", return_value={"Status": "Success", "ResponseCode": 1}):
            with self.assertRaises(RuntimeError):
                deploy.wait_for_command("command", "instance", 30)

    def test_terminal_failures_propagate(self):
        for status in ("Failed", "TimedOut", "Cancelled"):
            with self.subTest(status=status), patch.object(deploy, "aws", return_value={"Status": status}):
                with self.assertRaises(RuntimeError):
                    deploy.wait_for_command("command", "instance", 30)

    def test_wait_handles_eventual_consistency_then_success(self):
        missing = subprocess.CalledProcessError(254, "aws", stderr="InvocationDoesNotExist")
        replies = [missing, {"Status": "InProgress"}, {"Status": "Success", "ResponseCode": 0}]
        with patch.object(deploy, "aws", side_effect=replies), patch.object(deploy.time, "sleep"):
            deploy.wait_for_command("command", "instance", 30)

    def test_permission_error_is_not_hidden_as_eventual_consistency(self):
        denied = subprocess.CalledProcessError(254, "aws", stderr="AccessDeniedException")
        with patch.object(deploy, "aws", side_effect=denied):
            with self.assertRaises(subprocess.CalledProcessError):
                deploy.wait_for_command("command", "instance", 30)

    def test_wait_timeout_reports_uncertain_remote_state(self):
        with patch.object(deploy, "aws", return_value={"Status": "InProgress"}), \
             patch.object(deploy.time, "monotonic", side_effect=[0, 0, 31]), patch.object(deploy.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "원격 명령 종료"):
                deploy.wait_for_command("command", "instance", 30)


if __name__ == "__main__":
    unittest.main()
