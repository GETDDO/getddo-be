"""실제 AWS 대신 CLI 응답만 대체해 비밀값 처리와 설정 파일 경계를 검증한다."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SECRET = "synthetic-only-$quote\"#=password"
VALUES = {
    "SPRING_DATASOURCE_URL": "jdbc:mysql://db.example.test:3306/getddo?sslMode=VERIFY_IDENTITY",
    "SPRING_DATASOURCE_USERNAME": "getddo_app", "SPRING_DATASOURCE_PASSWORD": SECRET,
    "SPRING_JPA_HIBERNATE_DDL_AUTO": "validate", "SPRING_FLYWAY_ENABLED": "true",
    "CORS_ALLOWED_ORIGINS": "https://frontend.example.test", "AWS_REGION": "ap-northeast-2",
    "GETDDO_IMAGES_BUCKET": "test-getddo-images",
    "GETDDO_IMAGES_BASE_URL": "https://cdn.example.test/images/",
}


class FetchAppEnvTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.output = self.root / "next.env"
        self.response = self.root / "response.json"
        self.document = {"Parameters": [
            {"Name": "/getddo/dev/app/" + key, "Value": value,
             "Type": "SecureString" if key == "SPRING_DATASOURCE_PASSWORD" else "String"}
            for key, value in VALUES.items()], "InvalidParameters": []}
        binary = self.root / "bin"
        binary.mkdir()
        cli = binary / "aws"
        cli.write_text("#!/usr/bin/env python3\nimport os,pathlib,sys\n"
                       "assert sys.argv[1:3]==['ssm','get-parameters']\n"
                       "assert '--with-decryption' in sys.argv and '--profile' not in sys.argv\n"
                       "if os.environ.get('AWS_FAILURE'):\n"
                       " print(os.environ['SYNTHETIC_SECRET'],file=sys.stderr)\n"
                       " sys.exit(1)\n"
                       "print(pathlib.Path(os.environ['RESPONSE_FILE']).read_text())\n")
        cli.chmod(0o755)
        self.env = dict(os.environ, PATH=str(binary) + ":" + os.environ["PATH"],
                        RESPONSE_FILE=str(self.response), SYNTHETIC_SECRET=SECRET)

    def run_fetch(self, raw=None, **environment):
        self.response.write_text(json.dumps(self.document) if raw is None else raw)
        return subprocess.run(["python3", str(ROOT / "infra/scripts/fetch-app-env.py"),
                               "--region", "ap-northeast-2", "--output", str(self.output)],
                              env=dict(self.env, **environment), capture_output=True, text=True, timeout=10)

    def test_complete_response_writes_literal_values_with_private_permissions(self):
        result = self.run_fetch()
        self.assertEqual(result.returncode, 0, result.stderr)
        lines = dict(line.split("=", 1) for line in self.output.read_text().splitlines())
        self.assertEqual(lines, VALUES)
        self.assertEqual(self.output.stat().st_mode & 0o777, 0o600)
        self.assertNotIn(SECRET, result.stdout + result.stderr)

    def test_missing_parameter_fails_without_output_file(self):
        self.document["Parameters"].pop()
        self.assertNotEqual(self.run_fetch().returncode, 0)
        self.assertFalse(self.output.exists())

    def test_invalid_parameter_result_fails_even_if_other_values_are_present(self):
        self.document["InvalidParameters"] = ["/getddo/dev/app/SPRING_DATASOURCE_URL"]
        self.assertNotEqual(self.run_fetch().returncode, 0)
        self.assertFalse(self.output.exists())

    def test_plaintext_password_parameter_is_rejected(self):
        self.document["Parameters"][2]["Type"] = "String"
        result = self.run_fetch()
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn(SECRET, result.stdout + result.stderr)
        self.assertFalse(self.output.exists())

    def test_duplicate_or_unrequested_names_are_rejected(self):
        self.document["Parameters"].append(dict(self.document["Parameters"][0]))
        self.assertNotEqual(self.run_fetch().returncode, 0)
        self.document["Parameters"][-1]["Name"] = "/getddo/another/app/SECRET"
        self.assertNotEqual(self.run_fetch().returncode, 0)
        self.assertFalse(self.output.exists())

    def test_env_injection_empty_and_invalid_values_are_rejected(self):
        invalid = {
            "SPRING_DATASOURCE_PASSWORD": ["", "value\nINJECTED=true", "value\r", "value\0"],
            "SPRING_DATASOURCE_URL": ["jdbc:mysql://db/getddo", "jdbc:mysql://db/getddo?sslMode=DISABLED"],
            "SPRING_JPA_HIBERNATE_DDL_AUTO": ["create"],
            "SPRING_FLYWAY_ENABLED": ["not-a-boolean"],
            "AWS_REGION": ["us-east-1"],
            "CORS_ALLOWED_ORIGINS": ["*", "https://frontend.example.test/"],
            "GETDDO_IMAGES_BUCKET": ["invalid bucket"],
            "GETDDO_IMAGES_BASE_URL": ["http://cdn.example.test/images/", "https://cdn.example.test/"],
        }
        for key, candidates in invalid.items():
            parameter = next(p for p in self.document["Parameters"] if p["Name"].endswith("/" + key))
            for value in candidates:
                with self.subTest(key=key, case=candidates.index(value)):
                    parameter["Value"] = value
                    result = self.run_fetch()
                    self.assertNotEqual(result.returncode, 0)
                    self.assertFalse(self.output.exists())
                    self.assertNotIn(SECRET, result.stdout + result.stderr)
            parameter["Value"] = VALUES[key]

    def test_aws_error_and_malformed_json_do_not_expose_response(self):
        for raw, environment in [(None, {"AWS_FAILURE": "1"}), (SECRET, {})]:
            with self.subTest(aws_error=bool(environment)):
                result = self.run_fetch(raw=raw, **environment)
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn(SECRET, result.stdout + result.stderr)
                self.assertFalse(self.output.exists())

    def test_existing_file_or_symlink_is_never_overwritten(self):
        self.output.write_text("existing configuration")
        self.assertNotEqual(self.run_fetch().returncode, 0)
        self.assertEqual(self.output.read_text(), "existing configuration")
        self.output.unlink()
        target = self.root / "existing.env"
        target.write_text("protected configuration")
        self.output.symlink_to(target)
        self.assertNotEqual(self.run_fetch().returncode, 0)
        self.assertEqual(target.read_text(), "protected configuration")


if __name__ == "__main__":
    unittest.main()
