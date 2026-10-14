# 플레이 검증 기준과 최초 결과 저장

관련 작업: [GD-170](https://ureca4.atlassian.net/browse/GD-170), 상위 [GD-154](https://ureca4.atlassian.net/browse/GD-154).
게임 도메인·정책은 [공용 게임 명세](https://github.com/GETDDO/getddo-spec/blob/main/02-domain/game.md), 입력·문제 생성 계약은 [색깔 게임 연동 계약](https://github.com/GETDDO/getddo-spec/blob/main/05-api/game-color-word-draft.md)을 따른다.

## 저장 범위

- `GamePlay.start`는 게임의 규칙 JSON·버전을 복사하고 토큰 SHA-256 해시·생성 버전·0이 아닌 unsigned 32-bit seed를 보존한다. UUID v7과 UTC 마이크로초 시작 시각은 공통 Entity가 발급한다.
- `find`와 `findForUpdate`는 플레이 ID·사용자 ID·게임 ID를 모두 조건으로 사용한다.
- `complete`는 호출자의 트랜잭션 안에서 행 잠금 후 최초 결과를 반영한다. 같은 입력 해시는 최초 점수·완료 시각을 유지하며 다른 해시는 거절한다.
- 신규 플레이는 완료 시각 없음=미완료, 완료 시각 있음·점수 있음=유효(0점 포함), 완료 시각 있음·점수 NULL=무효로 구분한다.

점수 계산, 토큰 원문 발급·검증, 답변 정규화·해시 계산, HTTP 상태·오류 변환은 후속 Service/API에서 구현한다. 저장소가 받은 점수는 서버 검증이 끝난 값이어야 한다.

## 트랜잭션 사용

후속 결과 Service는 `findForUpdate`를 첫 플레이 조회로 사용한다. 이미 완료됐다면 입력을 비교하고 최초 결과를 반환한다. 미완료일 때만 판정·통계·보상 처리를 실행하고 같은 트랜잭션으로 커밋한다. `complete`는 `Propagation.MANDATORY`이므로 트랜잭션 밖에서 단독 호출할 수 없다.

이번 PR은 플레이 결과 저장까지만 포함한다. 통계·보상 처리 및 경합 재시도는 후속 PR에서 연결한다. `PROCESSING`은 후속 API 응답 상태이며 영구 저장 상태로 추가하지 않았다.

## 마이그레이션과 과거 데이터

[V020](../../storage/db/src/main/resources/db/migration/game/V020__add_game_play_verification_context.sql)은 검증 기준 4개 컬럼과 두 CHECK 제약을 추가한다. 기존 V005는 변경하지 않는다. 과거 플레이에는 임의의 규칙·토큰·seed를 채우지 않고 네 컬럼을 모두 NULL로 유지한다. 검증 기준이 없는 과거 행은 조회할 수 있지만 새 판정에 사용하지 않는다.

새 검증 기준은 모두 갖추거나 모두 NULL이어야 한다. 신규 플레이의 완료 시각은 시작 이후이며, 결과 해시와 비음수 점수 또는 무효를 뜻하는 NULL을 보존한다. 과거 결과·보상 청구 값은 업그레이드 후에도 유지한다.

## 검증 방법

```bash
./gradlew :core:test --tests 'com.getddo.core.game.domain.GamePlayTest'
./gradlew :storage:db:integrationTest --tests 'com.getddo.db.game.*' --tests 'com.getddo.db.migration.MySqlMigrationTest'
./gradlew test build
```

실제 MySQL에서 규칙 원본 보존, 소유자 범위 조회, 최초 결과·변경 입력 처리, 롤백, 동시 제출 잠금, 과거 데이터 업그레이드를 검증한다. 공통 마이그레이션 검사는 고정 파일 수 대신 발견된 전체 마이그레이션 적용 여부를 확인한다.
