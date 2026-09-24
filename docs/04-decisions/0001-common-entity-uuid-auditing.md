# ADR-0001: 공통 Entity의 UUID와 시간 필드

- 상태: 제안
- 작성자 / 날짜: Codex / 2026-09-24 (담당자 확인 전)
- 검토자: 미지정
- 관련 작업 기록 / PR: [GD-22](https://ureca4.atlassian.net/browse/GD-22), [UUID v7 선택과 성능 검증](https://github.com/GETDDO/getddo-be/wiki/MySQL-UUID-v4-v7)
- 대체한 ADR / 대체된 ADR: 해당 없음

## 배경

Entity의 식별자와 생성·수정 시각을 공통화한다. UUID 사용과 MySQL의 `BINARY(16)` 저장을 전제로 UUID 버전과 수정 시각의 공통화 범위를 결정한다. 시간 정책은 [getddo-spec](https://github.com/GETDDO/getddo-spec/blob/main/00-requirements/functional-requirements.md#공통-시간-기준과-화면-표시)을 따른다.

## 결정과 이유

### UUID v7을 선택한 이유

UUID v4도 인덱스를 사용할 수 있지만 무작위 값이라 새 키의 삽입 위치가 인덱스 전반에 흩어진다. v7은 앞부분에 밀리초 단위 시각을 담아 시간순에 가까운 정렬이 가능하다. UUID를 유지하면서 삽입 위치의 분산을 줄이는 효과를 기대해 v7을 선택한다. [RFC 9562](https://www.rfc-editor.org/rfc/rfc9562.html#section-2.1)

MySQL InnoDB는 PK를 기준으로 행 데이터를 구성하므로 PK의 삽입 특성이 중요하다. [InnoDB 인덱스](https://dev.mysql.com/doc/refman/8.4/en/innodb-index-types.html)

MySQL 8.4.10에서 조건별 6회 실측한 삽입·commit 중앙값은 10만 행에서 v4 **5.121초 → v7 4.216초**, 50만 행에서 **163.652초 → 19.856초**였다. 페이지 분할도 v7이 적었다. 다만 단일 작성자·1,000행 배치·128 MiB buffer pool 조건이며, 50만 행은 인덱스가 버퍼 풀보다 큰 경우다. 운영 성능 개선 배율로 일반화하지 않는다. 전체 표본·환경·캡처는 [실험 기록](https://github.com/GETDDO/getddo-be/wiki/MySQL-UUID-v4-v7)에 남긴다.

ID는 JPA 영속화 시 Hibernate로 생성하고 MySQL 기본 매핑으로 `BINARY(16)`에 저장한다. 저장 전에는 null이며 이후 변경하지 않는다.

### updated_at을 분리한 이유

생성 시각만 필요한 기록에 수정 시각 컬럼과 갱신 규칙까지 강제하지 않기 위해 두 클래스로 나눈다.

| 클래스 | 공통 필드 | 사용 기준 |
| --- | --- | --- |
| `BaseEntity` | `id`, `createdAt` | 생성 시각만 필요한 Entity |
| `BaseUpdatableEntity` | 상속한 필드 + `updatedAt` | 마지막 수정 시각도 필요한 Entity |

두 클래스는 `storage:db`의 `com.getddo.db.common.entity`에 둔다. 시간 타입은 `Instant`이며 `JpaAuditingConfig`가 기존 공통 `Clock`을 연결한다. 수정 시각이 있는 Entity는 최초 저장 시 `createdAt`과 `updatedAt`이 같다.

## 검토한 대안

UUID 사용을 전제로 v4와 v7을 비교했다.

| 선택지 | 특징과 판단 |
| --- | --- |
| UUID v4 | 무작위 UUID. 인덱스 사용은 가능하지만 삽입 위치가 분산되어 선택하지 않았다. |
| UUID v7 | 시간순에 가까운 UUID. UUID 사용 조건을 유지하면서 인덱스 삽입 위치의 분산을 줄이기 위해 선택했다. |

`updated_at`을 `BaseEntity` 하나에 모두 넣는 대안은 구조가 단순하지만, 생성 시각만 필요한 Entity에도 불필요한 컬럼이 생겨 선택하지 않았다.

## 영향과 재검토 조건

- UUID v7은 업무 처리 순서를 보장하지 않는다. 업무 시각은 별도의 `createdAt`·`updatedAt`으로 관리한다.
- `BaseEntity` 상속만으로 수정·삭제가 금지되지는 않는다. 이력의 변경 제한은 각 도메인에서 처리한다. bulk JPQL·native SQL에는 자동 시간 기록이 적용되지 않는다.
- JPA 영속화·Auditing 테스트는 H2 기반이다. 별도 MySQL JDBC 삽입 실험을 완료했지만, 애플리케이션의 실제 MySQL UUID/FK 매핑·시각 정밀도는 추가 검증이 필요하다.
- MySQL 버전·연결 설정과 Flyway SQL은 이번 작업 범위에서 제외한다. DB 변경이나 저장 전 ID가 필요한 요구사항이 생기면 재검토한다.
