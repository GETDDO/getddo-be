# ERD·설명

- [API·DB 대응과 스키마 검토](api-schema-mapping.md): 공용 API 초안과 현재 Flyway SQL의 대응 및 백엔드 저장 설계 검토 항목.
- [GD-36 추첨 선정 저장 설계 검토](drawing-selection-storage.md): 추첨 후보의 실제 차감 수량·가중치와 실행 스냅샷의 현재 스키마 대응.
- [통합 스키마 DBML](schema.dbml): 현재 스키마의 테이블·컬럼·관계를 나타내는 ERD. 생성 컬럼과 CHECK는 주석으로 설명하며, 실제 DB 적용 기준은 Flyway SQL이다.
- [MySQL 마이그레이션](../../storage/db/src/main/resources/db/migration): V001~V011은 41개 테이블의 초기 DDL이다. 이벤트·배너의 논리 삭제 시각, 출석 기준일, 사용자별 하루 1회 UNIQUE와 미션 답변의 복합 외래 키는 초기 정의에 반영되어 있다.
- V012는 이벤트 사용자·관리자 기본 목록 조회를 위한 `idx_events_list (deleted_at, created_at, id)` 인덱스를 추가한다. 기존 초기 DDL과 테이블·컬럼은 유지하며, 필터 없는 목록의 정렬과 삭제 제외 건수 조회를 지원한다.
- [출석·연속 출석·미션·게임 테이블 분리 설계](reward-tables.md): 논리 설계 초안 및 DDL 작성 전 미결정 항목.
