# 2026-09-26 초기 Flyway 스키마

- 작성: Codex (담당자 확인 전)
- 상태: 검토 대기
- 대상: [통합 DBML](../03-database/schema.dbml), `storage/db/src/main/resources/db/migration`

## 오늘 한 일

- 사용자 승인에 따라 기존 빈 V001~V011 파일에 담당 폴더별 41개 테이블의 MySQL DDL을 작성했다.
- V012를 추가해 104개 참조를 외래 키로 적용했다. 도메인 간 참조와 순환 참조가 있어 모든 테이블 생성 뒤에 적용한다.
- DBML의 인덱스, 22개 CHECK, `open_guard`·`active_guard` 생성 컬럼을 SQL에 반영했다.
- API의 H2 컨텍스트 테스트에서 MySQL 전용 Flyway DDL을 실행하지 않도록 해당 테스트의 Flyway 설정을 조정했다.

## 고민과 판단

- 현재 DBML을 그대로 변환하라는 사용자 선택에 따라 `attendances`에 KST 출석 기준일과 사용자·기준일 UNIQUE를 추가하지 않았다. 이 누락은 확정된 [출석 규칙](https://github.com/GETDDO/getddo-spec/blob/main/02-domain/attendance.md)과 맞지 않아 후속 설계·마이그레이션이 필요하다.
- V001~V011은 Git에 등록된 빈 파일이었다. 사용자는 해당 파일 수정과 적용된 DB가 없음을 확인했다.

## 확인 결과 / 막힌 점

- DBML과 SQL의 41개 테이블·컬럼 집합, 보조 인덱스 51개, 22개 CHECK, 104개 FK 수를 정적 대조했다. FK가 참조하는 부모 키의 PK/UNIQUE도 확인했다.
- `git diff --check`와 YAML 구조 검사를 수행했다.
- `./gradlew test --no-daemon`, `./gradlew build --no-daemon`은 이 WSL의 Java 부재로 시작되지 않았다. `docker compose config --quiet`도 Docker Desktop WSL 연동이 비활성화되어 시작되지 않았다. 실제 MySQL 마이그레이션은 실행하지 못했다.
- 일반 실행에서는 DNS 오류가 있었지만 권한 상승 후 원격을 조회해 `getddo-be`의 `dev`와 `getddo-spec`의 `main`이 각각 로컬 HEAD와 같음을 확인했다.

## 다음 할 일

- Docker 연동이 가능한 환경에서 빈 MySQL 8.4 DB에 V001~V012를 적용하고 모든 제약·생성 컬럼을 확인한다.
- 출석 기준일과 중복 방지 등 확정 공용 규칙과 DBML의 차이를 담당자와 정리한 뒤 새 버전 마이그레이션으로 반영한다.
