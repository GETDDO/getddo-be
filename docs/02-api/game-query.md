# 게임 목록·상세 조회 구현 — GD-155

공용 계약은 [게임 API의 G01/G02 조회 계약](https://github.com/Uplus-GETDDO/getddo-spec/blob/main/05-api/game.md#g01g02-조회-계약--2026-10-15-사용자-선택)이 원본이다. 2026-10-15 사용자가 선택한 조회 정책은 [spec 커밋 `190d401`](https://github.com/Uplus-GETDDO/getddo-spec/commit/190d4019fbb574c15ec2f48a527844af0bfd48f6)으로 원격 main에 반영했다. 실제 FE 연결·운영 DB 적용 완료와는 구분한다.

## 실제 적용 항목과 구현

| 적용 항목 | 코드·검증 근거 | 현재 검증 상태 |
| --- | --- | --- |
| G01/G02 공통 USER/ADMIN 문맥, 선택 멤버십 | GameController의 CurrentUser, 공통 resolver 재사용, MVC/통합 헤더 검사 | 사용자 관련 테스트 성공 확인 |
| 활성 전체 목록·code 순·빈 배열 | GameQueryService.findGames → 기존 GameRepository.findActive/JPA 정렬, MySQL 역순 등록 테스트 | 기존 DB 조회 재사용, 사용자 통합 테스트 성공 확인 |
| 목록은 시작 지원 여부와 독립 | 색깔·타꼬런 목록 테스트, 타꼬런 상세 409 검사 | 사용자 관련 테스트 성공 확인 |
| 없는/비활성 상세 동일 404 | Repository Optional.filter와 GAME-002, 단위/MVC/MySQL 검사 | 사용자 관련 테스트 성공 확인 |
| 미지원 코드/버전 상세 409 | 게임별 handler 지원 확인, 조회 전용 GAME-004 | 사용자 관련 테스트 성공 확인 |
| 색깔 공개 규칙 5개·원본 JSON 비노출 | 기존 ColorWordPlayStartHandler, GameRulesResponse/ColorWordRulesResponse 재사용 | 내부 필드 포함 fixture 검사, 사용자 테스트 성공 확인 |
| 실제 DB UUID·code·name·nullable description·ruleVersion | summary/detail DTO의 명시 필드 변환, DB 메타데이터 변경 후 조회 검사 | 사용자 관련 테스트 성공 확인 |
| UTC serverTime | Controller의 TimeProvider, MVC와 통합 테스트 고정 Clock | 사용자 관련 테스트 성공 확인 |
| 조회가 시작/보상을 만들지 않음 | 읽기 전용 Service, 발급/저장 서비스 의존 없음, 실제 조회 후 game_plays 0건 단언 | 사용자 관련 테스트 성공 확인 |
| 설정 오류·DB 장애는 공통 500 | 기존 안전한 파싱 예외·GlobalExceptionHandler, MVC/MySQL 검사 | 사용자 관련 테스트 성공 확인 |

## 책임과 재사용 이유

`GameController`는 사용자 문맥과 UUID를 해석하고 공통 봉투·HTTP DTO로 응답한다. 서비스가 비활성 숨김·미지원 상세 정책을 판단하며 Controller에서 JSON을 파싱하지 않는다. `GameDetailResult`는 core 조회 결과이며 HTTP DTO나 Entity가 아니다.

`GameQueryService`의 두 조회 메서드는 readOnly 트랜잭션을 사용한다. 기존 `findActive()`가 활성 필터와 code 정렬을 이미 제공해 Repository·DB Entity·migration은 변경하지 않는다. 상세는 하나의 조회에서 얻은 메타데이터·규칙을 같은 결과로 전달한다.

색깔 공개 규칙 파싱은 기존 시작 처리기의 `supports`·`prepare`를 재사용한다. 이 메서드는 규칙·생성 버전을 준비하는 순수 처리이며 토큰/seed 발급·플레이 저장은 `GamePlayStartService`에 있어 조회에서 호출되지 않는다. 규칙 검증과 안전한 오류 진단을 복제하지 않기 위한 재사용이다. 중복 등록은 임의 선택하지 않고 서버 오류로 거절한다.

공개 rules 응답도 기존 DTO 매핑을 사용한다. 타꼬런 추가 시 해당 공개 규칙·시작 계약과 handler/DTO 매핑을 함께 확인해야 하며 이번 조회가 타꼬런 상세·플레이 지원을 완성하지 않는다. 현재 목록에 표시되는 미지원 게임은 상세 409를 FE에서 처리해야 한다. 비활성 계정에 대한 새 조회 차단 규칙을 추가하지 않고 기존 R 조회 문맥을 유지한다.

## 테스트 실행

작업 기준은 dev `412de2e7`이다. `./gradlew :core:compileTestJava :api:compileTestJava :api:compileIntegrationTestJava --offline` 임시 폴더에서 성공(16초)했고, 최종 `getddo-be-game-query` 폴더에서도 성공(2분 22초)했다. 기존 미매핑 expire·트랜잭션 어노테이션 클래스패스 경고는 남아 있다. Codex는 사용자 실행 방침에 따라 로컬 test/build를 실행하지 않았다. 사용자가 단위/MVC·MySQL 통합 테스트 성공을 확인했다. 작업 폴더의 GameQueryApiIntegrationTest XML 보고서도 tests=4, failures/errors/skipped=0이다. 단위/MVC 성공은 사용자 확인을 근거로 하며 로컬 전체 test/build·FE 연결 성공은 별도 확인 전이다.

```bash
./gradlew :core:test --tests 'com.getddo.core.game.service.GameQueryServiceTest' :api:test --tests 'com.getddo.api.game.controller.GameControllerTest'
./gradlew :api:integrationTest --tests 'com.getddo.api.game.GameQueryApiIntegrationTest'
./gradlew test build --init-script gradle/test-limits.init.gradle --max-workers=1 --no-parallel
```

통합/전체 검증에는 Docker가 필요하다. `GameQueryApiIntegrationTest`는 실제 DB 게임 UUID로 목록→상세→시작 경로도 검사하며 실제 FE 실행을 대신하지 않는다.

## 남은 확인

- 최종 전체 test/build와 최신 PR CI 결과 확인. 사용자 확인한 단위/MVC·MySQL 통합 테스트 성공을 전체 빌드로 확대하지 않는다.
- 사용자 테스트 성공·PR 공유 요청에 따라 backend PR을 생성한다. spec 조회 계약은 위 커밋으로 원격 반영했다.
- FE가 실제 UUID와 code를 사용하고 미지원 상세 409·비활성 404를 처리하는지 확인.
- 운영 DB의 게임 행 등록·규칙·Flyway 적용은 미확인. 자동 등록이나 보정은 추가하지 않았다.
