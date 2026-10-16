# 매일 오전 8시 dev → main 자동 릴리스

- 상태: 검토 대기 · Codex 작성 · 담당자 확인 전
- 관련: GD-115
- 실행: 한국 시간 매일 오전 8시(UTC 23:00). GitHub 예약 실행은 지연되거나 누락될 수 있어 정각 실행을 보장하지 않는다.

## 동작

기본 브랜치인 dev에 [예약 워크플로](../../.github/workflows/daily-release.yml)가 반영되고 활성화되면 [daily-release.py](../../infra/scripts/daily-release.py)가 다음 순서로 처리한다.

1. 최신 dev·main 커밋을 조회한다. main에 이미 포함된 dev라면 건너뛴다.
2. 그 dev 커밋의 가장 최근 push Build가 완료·성공이고, `빌드·테스트`와 `이미지 / Docker 이미지` 작업도 성공인지 확인한다. CI가 없거나 실패·진행 중이면 오늘 배포를 건너뛴다. 과거 커밋의 성공이나 PR CI 성공으로 대체하지 않는다.
3. 같은 저장소의 dev → main PR을 재사용하거나 생성한다. 새 PR은 `[chore] GD-115 매일 dev 변경 main 반영` 제목을 사용한다.
4. 초안·종료된 PR, 다른 head·base·저장소·커밋, 충돌 또는 병합 가능 여부 미확인은 건너뛴다. GitHub의 병합 가능 여부 계산은 최대 1분만 기다린다.
5. dev·main이 조회한 상태 그대로인지, dev CI가 여전히 성공인지 다시 확인한다. merge API에도 검증한 dev SHA를 전달해 다른 head 커밋의 병합을 막는다.
6. 전용 GitHub App으로 merge commit을 만든다. 해당 main push가 기존 Build를 실행하고, 성공하면 [Container CD](container-cd.md)가 서버에 배포한다.

PR CI·봇 리뷰 완료를 기다리는 흐름은 아니다. 사용자가 승인한 정기 릴리스는 검증된 dev를 리뷰 승인 없이 main에 반영하며, main의 새 병합 커밋은 다시 Build를 통과해야 배포된다. 새 필수 Parameter Store 값과 호환되는 DB 마이그레이션은 dev 병합 전에 준비해야 한다. API 병합 직전 최종 조회와 병합 사이의 main 변경을 원자적으로 잠그지는 못하므로 main의 Build와 CD는 계속 최종 배포 관문으로 유지한다.

## 한 번 필요한 GitHub 설정

현재 GitHub 규칙 `protect-main-dev`는 main·dev에 리뷰 2개, 대화 해결, 삭제·강제 push 방지 등을 적용하고 있으며 예외 주체가 없다. 이 상태에서는 자동 병합을 할 수 없다. 일반 GITHUB_TOKEN으로 병합하면 후속 main push Build가 실행되지 않으므로 전용 App을 사용한다.

### 전용 GitHub App

조직 관리자 권한으로 App을 생성하고 `Uplus-GETDDO/getddo-be` 저장소 하나에만 설치한다. 서버·AWS 권한은 필요 없다.

- 이름 예: `getddo-daily-release`
- Homepage URL: 저장소 주소
- Webhook: 비활성화. 예약 워크플로가 호출하므로 별도 서버는 필요 없다.
- Repository permissions: Contents **Read and write**, Pull requests **Read and write**, Actions **Read-only**, Workflows **Read and write**. Metadata는 기본 읽기 권한이다.
- Workflows 쓰기 권한은 workflow 파일이 포함된 dev 변경도 main에 병합하기 위해 필요하다.
- App ID를 확인하고 private key를 생성한다. 키 원문은 채팅·Git·PR에 올리지 않는다.

### Environment와 변수

전용 `daily-release` Environment를 사용한다. Deployment branches and tags는 **Selected branches and tags → Branch: dev**만 허용한다. 자동 실행을 위해 Required reviewers나 Wait timer를 설정하지 않는다. production 환경의 기존 설정은 유지한다.

| 위치 | 이름 | 값 |
| --- | --- | --- |
| Repository Actions Variable | DAILY_RELEASE_ENABLED | 준비 중 `false`, 모든 설정을 마치고 검사한 뒤 `true` |
| daily-release Environment Variable | DAILY_RELEASE_APP_ID | 전용 App의 숫자 App ID |
| daily-release Environment Secret | DAILY_RELEASE_APP_PRIVATE_KEY | 생성한 PEM private key 전체 |

토큰 발급은 현재 저장소로 제한하고 작업 종료 시 토큰을 폐기한다. `daily-release` 환경의 dev 제한으로 다른 ref에서 이 키를 사용하는 실행을 막는다. 워크플로도 dev ref에서만 실행한다.

### 보호 규칙 분리

기존 규칙 전체에 App을 예외로 추가하면 dev의 리뷰와 삭제·강제 push 방지까지 우회하게 된다. 다음처럼 분리한다. 기존 보호를 먼저 제거하지 않고 추가 규칙부터 만든 뒤 기존 규칙을 조정한다.

1. **dev 보호:** 기존과 같은 규칙을 dev에 유지한다. 리뷰 2개·대화 해결·삭제·강제 push 방지와 상태 검사 설정을 그대로 복사하며 App 예외를 두지 않는다.
2. **main 공통 보호:** 기존 규칙 중 pull_request 외의 규칙을 main에 적용한다. App 예외를 두지 않아 삭제·강제 push 방지와 설정된 상태 검사 조건을 계속 적용한다.
3. **main PR 리뷰:** 기존 pull_request 규칙을 main에 적용하고 전용 App만 **For pull requests only** 예외로 지정한다. 사람의 PR은 기존 리뷰 2개·대화 해결 요구를 유지한다.

GitHub 규칙의 App 예외는 PR의 head=dev 조건까지 제한하지 못한다. 전용 App을 다른 자동화에 재사용하지 않고 키를 위 환경에만 보관한다. 코드의 대상은 dev → main으로 고정한다. 현재 필수 상태 검사 목록은 비어 있으며, 예약 스크립트가 dev Build의 실제 작업 성공을 검사한다. 필수 상태 검사를 추가하면 App에도 적용되므로 해당 PR의 검사가 완료될 때까지 merge API가 거절할 수 있다.

## 검증과 활성화

1. 기능 PR을 리뷰·CI 통과 후 dev에 병합한다. 예약 실행은 기본 브랜치에 파일이 있어야 시작한다.
2. App 설치·환경 변수·Secret·보호 규칙 분리를 준비한다.
3. `DAILY_RELEASE_ENABLED=true`로 설정한다.
4. Actions → Daily Release → Run workflow → **dev**, `dry_run=true`로 실행한다. PR 생성·병합 없이 조회만 한다. 수동 실행은 기본값이 dry-run이다.
5. 준비된 dev 변경이 있을 때 수동 실행의 dry_run을 false로 바꾸거나 다음 오전 8시 실행을 확인한다. dev 변경이 없으면 정상적으로 건너뛴다.
6. `RELEASE_MERGED`에 기록된 main 커밋의 Build·Container CD와 서버 실행 이미지·HTTPS 응답을 확인한다. 예약 워크플로 성공 자체가 서버 배포 성공을 의미하지 않는다.

`RELEASE_SKIPPED`는 변경 없음·CI 미완료·충돌·브랜치 변경 등에 따른 건너뛰기다. `RELEASE_FAILED`는 API·인증·보호 규칙 등의 점검이 필요한 실패이며 작업을 실패로 표시한다. GitHub Actions 알림 설정에서 이 저장소의 실패 실행을 구독한다. Slack·메일 전송이나 CloudWatch 알람은 이 작업에 추가하지 않는다.

중지할 때는 `DAILY_RELEASE_ENABLED=false`로 바꾼다. 진행 중 실행은 Actions에서 별도로 취소해야 한다. 이미 병합된 main의 Build·Container CD를 자동 취소하는 스위치는 아니다. main 수동 병합은 기존처럼 즉시 배포를 시작한다. 배포 실패 복구는 기존 CD 절차를 따르며 main 커밋·DB·Parameter Store 원격 값의 자동 롤백을 뜻하지 않는다.

## 참고

- [GitHub 예약 실행과 기본 브랜치](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule)
- [GITHUB_TOKEN의 후속 실행 제한](https://docs.github.com/en/actions/concepts/security/github_token)
- [공식 GitHub App 토큰 액션](https://github.com/actions/create-github-app-token)
