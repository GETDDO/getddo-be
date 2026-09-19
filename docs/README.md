# 작업과 결정 기록

AI가 실제 대화·코드 변경·검증 결과로 초안을 작성하고, 담당자가 확인한 뒤 코드와 같은 PR에 포함합니다. 작은 수정은 PR 설명만으로 충분합니다.

## 문서 위치

공용 요구사항, 도메인 규칙 및 협업 규칙은 [getddo-spec](https://github.com/GETDDO/getddo-spec)에서만 관리합니다. 이 폴더에는 백엔드 구현과 작업 기록을 남깁니다.

- [공용 요구사항](https://github.com/GETDDO/getddo-spec/blob/main/00-requirements/README.md)
- [공용 도메인 규칙](https://github.com/GETDDO/getddo-spec/blob/main/02-domain/README.md)
- [공용 협업 규칙](https://github.com/GETDDO/getddo-spec/blob/main/01-conventions/README.md)

```text
docs/
├── README.md                    기록 작성 방법
├── 01-conventions/
│   ├── README.md
│   └── code-style.md
├── 02-api/
│   └── README.md
├── 03-database/
│   └── README.md
├── 04-decisions/                중요한 설계 결정과 이유(ADR)
│   └── README.md
├── 05-worklogs/                 작업 과정·선택 이유·검증 결과
│   ├── README.md
│   └── 2026-09-17-requirements-refinement.md
├── 06-runbook/
│   └── README.md
└── templates/
    ├── worklog.md               작업 기록 양식
    └── adr.md                   설계 결정 양식
```

## 무엇을 기록하나요?

| 기록 | 작성 기준 | 파일명 예시 |
| --- | --- | --- |
| PR 설명 | 작은 수정의 목적·변경·검증 | 별도 파일 불필요 |
| 작업 기록 | 해결 과정이나 검증 결과를 다음 작업에서 참고할 필요가 있을 때 | `05-worklogs/GT-21-response.md` |
| ADR | 여러 작업에 영향을 주거나 바꾸기 어려운 설계 결정 | `04-decisions/0001-decision-title.md` |

작업 기록은 Jira 키 `GT-번호`와 짧은 영문 설명을 사용합니다. ADR 번호는 기존 파일을 확인해 다음 번호를 사용합니다. 위 파일명은 예시이며 실제 이슈나 결정이 아닙니다.

## 작성 순서

1. [작업 기록 양식](templates/worklog.md)을 복사해 `05-worklogs/`에 초안을 작성합니다.
2. 중요한 결정이 있으면 [ADR 양식](templates/adr.md)을 사용하고 작업 기록과 서로 연결합니다.
3. 담당자가 선택 이유·실제 실행 결과·미확인 내용을 확인합니다. AI 초안은 검토 대기, ADR은 제안 상태로 시작합니다.
4. 코드와 기록을 같은 브랜치·PR에 포함하고 PR 설명에 문서의 GitHub URL을 넣습니다. 로컬 경로는 사용하지 않습니다.

실행하지 않은 테스트, 실제로 검토하지 않은 대안, 확인되지 않은 이유는 만들어 쓰지 않습니다. 명령에는 실행 시점의 기준 커밋 또는 변경 범위를 함께 적고, 비밀번호·토큰·개인정보는 기록하지 않습니다. 테스트가 실패했다면 실패한 상태 그대로 기록합니다.

ADR을 대체할 때는 새 ADR을 작성하고 이전 ADR에 ‘대체됨’ 상태와 새 문서 링크를 남깁니다. 과거 기록은 당시 맥락이며, 다음 작업에서 현재 코드·정책과 달라졌는지 확인합니다.

## AI에게 요청할 문장

```text
이번 작업을 docs/templates/worklog.md 양식으로 정리해줘.
초안은 docs/05-worklogs/GT-번호-작업명.md에 저장해줘.
대화, 실제 diff, 테스트 실행 결과를 근거로
해결 과정·선택 이유·실제로 검토한 대안·검증 결과·남은 일을 적어줘.
확인되지 않은 이유나 결과를 만들지 말고 미확인·미실행을 구분해줘.
지금 떠올린 대안을 당시 검토한 대안처럼 쓰지 마.
중요한 결정은 ADR 분리 후보로 알려줘.
담당자 검토 전에는 완료·채택으로 표시하지 마.
PR 설명에 넣을 문서 링크도 정리해줘.
```
