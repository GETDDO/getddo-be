# ERD·설명

추첨 후보 재사용·결과 복합 FK와 선정·공개 분리의 근거는 [ADR-0003](../04-decisions/0003-drawing-snapshots-and-publications.md)을 참고한다. 최신 DBML에는 공개 버전별 포함 결과와 재추첨 관리자 확인 이력을 반영했다. GD-93에서 초기 Flyway SQL에 반영했으며, 실제 DB 적용과 서비스 코드의 호환성 수정은 후속 작업이다.

- [API·DB 대응과 스키마 검토](api-schema-mapping.md): 공용 API 초안과 현재 Flyway SQL의 대응 및 백엔드 저장 설계 검토 항목.
- [GD-36 추첨 선정 저장 설계 검토](drawing-selection-storage.md): 추첨 후보의 실제 차감 수량·가중치와 실행 스냅샷의 현재 스키마 대응.
- [통합 스키마 DBML](schema.dbml): 사용자 최종 SQL을 기준으로 최신화하고 티켓·추첨 근거 제약을 보완한 44개 테이블의 초기 스키마 기준. UNIQUE는 indexes, FK는 Ref, CHECK는 checks 블록으로 정의한다. MySQL 생성식은 DBML 주석에 기록한다.
- [MySQL 마이그레이션](../../storage/db/src/main/resources/db/migration): V001~V011에 DBML의 44개 테이블과 UNIQUE·FK·CHECK를 반영했다. 사용자 요청에 따라 초기 세팅으로 기존 번호를 유지했다. 적용 이력이 있는 DB에는 체크섬 충돌이 발생할 수 있으므로 적용 여부 확인이 필요하다. 기존 지갑·원장 기반 코드와 변경된 컬럼을 사용하는 코드는 담당자가 후속 수정해야 한다.
- V012는 이벤트 사용자·관리자 기본 목록 조회를 위한 `idx_events_list (deleted_at, created_at, id)` 인덱스를 추가한다. 기존 초기 DDL과 테이블·컬럼은 유지하며, 필터 없는 목록의 정렬과 삭제 제외 건수 조회를 지원한다.
- [출석·연속 출석·미션·게임 테이블 분리 설계](reward-tables.md): 논리 설계 초안 및 DDL 작성 전 미결정 항목.

티켓·추첨 SQL 초안은 사용자 요청으로 정리했다. 티켓 전용·전체 SQL은 Git 커밋 `850f3ba`에, 기존 추첨 SQL은 삭제 이전 Git 이력에 보존되어 있다. 최신 설계 기준은 `schema.dbml`이다.
