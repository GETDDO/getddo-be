# ERD·설명

- [추첨 ERD 리뷰 반영 SQL](drawing-erd-reviewed-erdcloud.sql): 추첨 7개·외부 참조 4개 테이블. 실패 이력 분리와 취소 테이블의 nullable 실행 FK로 여러 취소를 하나의 재추첨에 연결하는 구조를 포함한 ERDCloud용 초안. 실제 DB 적용용 마이그레이션이 아니다.
- [API·DB 대응과 스키마 검토](api-schema-mapping.md): 공용 API 초안과 현재 Flyway SQL의 대응 및 백엔드 저장 설계 검토 항목.
- [GD-36 추첨 선정 저장 설계 검토](drawing-selection-storage.md): 추첨 후보의 실제 차감 수량·가중치와 실행 스냅샷의 현재 스키마 대응.
- [통합 스키마 DBML](schema.dbml): 현재 스키마의 테이블·컬럼·관계를 나타내는 ERD. 생성 컬럼과 CHECK는 주석으로 설명하며, 실제 DB 적용 기준은 Flyway SQL이다.
- [MySQL 마이그레이션](../../storage/db/src/main/resources/db/migration): V001~V011은 41개 테이블의 초기 DDL이다. 이벤트·배너의 논리 삭제 시각, 출석 기준일, 사용자별 하루 1회 UNIQUE와 미션 답변의 복합 외래 키는 초기 정의에 반영되어 있다.
- [출석·연속 출석·미션·게임 테이블 분리 설계](reward-tables.md): 논리 설계 초안 및 DDL 작성 전 미결정 항목.
