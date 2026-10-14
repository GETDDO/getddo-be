"""PR 제목 회귀 검증: python3 .github/tests/test_pr_title.py"""

import os
from pathlib import Path
import re
import subprocess
import sys
import textwrap


CASES = [
    ("docs/2026-10-10", "[docs] 2026-10-10 공용 워크로그", 0),
    ("docs/2026-10-10-worklog", "[docs] 공용 워크로그", 0),
    ("docs/2026-10-10-워크로그", "[docs] 공용 워크로그", 0),
    ("docs/2026-10-10-team_worklog", "[docs] 공용 워크로그", 0),
    ("docs/2026-10-10", "[docs] GD-93 공용 워크로그", 0),
    ("docs/2024-02-29", "[docs] 윤년 워크로그", 0),
    ("docs/2026-10-10", "[docs] $(false) `false`", 0),
    ("feat/GD-84", "[feat] GD-84 추첨 구현", 0),
    ("docs/GD-93", "[docs] GD-93 문서 수정", 0),
    ("fix/GD-96", "[fix] GD-96 오류 수정", 0),
    ("feat/GD-84", "[feat] 추첨 구현", 1),
    ("feat/GD-84", "[docs] 공용 워크로그", 1),
    ("docs/GD-93", "[docs] 문서 수정", 1),
    ("docs/2026-10-10-", "[docs] 공용 워크로그", 1),
    ("docs/2026-10-10--", "[docs] 공용 워크로그", 1),
    ("docs/2026-10-10extra", "[docs] 공용 워크로그", 1),
    ("docs/2026-10-10/worklog", "[docs] 공용 워크로그", 1),
    ("docs/2026-10-10-worklog/more", "[docs] 공용 워크로그", 1),
    ("docs/2026-13-10", "[docs] 공용 워크로그", 1),
    ("docs/2026-02-29", "[docs] 공용 워크로그", 1),
    ("docs/2026-1-10", "[docs] 공용 워크로그", 1),
    ("docs/0000-10-10", "[docs] 공용 워크로그", 1),
    ("feat/docs/2026-10-10", "[docs] 공용 워크로그", 1),
    ("docs/2026-10-10", "[feat] 기능 구현", 1),
    ("docs/2026-10-10", "[docs] ", 1),
    ("docs/2026-10-10", "워크로그", 1),
    ("docs/2026-10-10", "[docs] 워크로그\n다른 제목", 1),
    ("feat/GD-84", "[feat] GD-0 설명", 1),
    ("feat/GD-84", "[feat] GD-084 설명", 1),
    ("feat/GD-84", "[feat] GD-84 ", 1),
]

for branch in ["docs/2026-10-10", "docs/2026-10-10-worklog"]:
    CASES.extend([
        (branch, "[docs] GD-0 작업 설명", 1),
        (branch, "[docs] GD-093 작업 설명", 1),
        (branch, "[docs] GD-93", 1),
        (branch, "[docs] GD-93 ", 1),
        (branch, "[docs] GD-abc 작업 설명", 1),
        (branch, "[docs] GD-93 작업 설명", 0),
        (branch, "[docs] GD-1 작업 설명", 0),
        (branch, "[docs] work GD-93", 0),
        (branch, "[docs] 워크로그에 GD-93 관련 내용 정리", 0),
    ])


def main():
    workflow_path = Path(__file__).resolve().parents[1] / "workflows/pr-title.yml"
    workflow = workflow_path.read_text(encoding="utf-8")
    # 검증 규칙을 복제하지 않고 워크플로의 Python 블록을 그대로 실행한다.
    match = re.search(r"(?ms)^([ \t]*)python3 - <<'PY'\n(.*?)^\1PY[ \t]*$", workflow)
    if match is None:
        print("워크플로의 Python 검증 블록을 찾을 수 없습니다.", file=sys.stderr)
        return 1
    script = textwrap.dedent(match.group(2))

    failures = 0
    for branch, title, expected in CASES:
        result = subprocess.run(
            [sys.executable, "-c", script],
            env={**os.environ, "PR_HEAD_REF": branch, "PR_TITLE": title},
            capture_output=True,
            text=True,
            timeout=10,
        )
        if result.returncode != expected:
            failures += 1
            print(f"FAIL branch={branch!r}, title={title!r}: expected={expected}, actual={result.returncode}")
            print(result.stdout + result.stderr)

    print(f"{len(CASES) - failures}/{len(CASES)} title checks passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
