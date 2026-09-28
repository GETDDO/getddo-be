# ERD·설명

- [통합 스키마 DBML](schema.dbml): 현재 초기 스키마의 테이블·컬럼·관계를 나타내는 ERD. 생성 컬럼과 CHECK는 주석으로 설명하며, 실제 DB 적용 기준은 Flyway SQL이다.
- [초기 MySQL DDL](../../storage/db/src/main/resources/db/migration): V001~V011의 도메인별 `CREATE TABLE`에 컬럼과 PK·UNIQUE·CHECK·외래 키를 포함한다. 출석 기준일, 사용자별 하루 1회 UNIQUE와 미션 답변의 복합 외래 키도 초기 정의에 반영되어 있다.
- [출석·연속 출석·미션·게임 테이블 분리 설계](reward-tables.md): 논리 설계 초안 및 DDL 작성 전 미결정 항목.
