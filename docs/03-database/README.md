# ERD·설명

- [통합 스키마 DBML](schema.dbml): 초기 MySQL Flyway 마이그레이션의 설계 원본. DBML의 출석 기준일 누락 등 확정 공용 규칙과 다른 부분은 후속 검토가 필요하다.
- 초기 MySQL DDL: `storage/db/src/main/resources/db/migration`의 V001~V012. V001~V011은 도메인별 테이블, V012는 모든 외래 키를 생성한다.
- [출석·연속 출석·미션·게임 테이블 분리 설계](reward-tables.md): 논리 설계 초안 및 DDL 작성 전 미결정 항목.
