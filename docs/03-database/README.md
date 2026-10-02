# ERD·설명

[전체 ERD 이미지](../assets/erd.png) · [ERDCloud](https://www.erdcloud.com/d/vuDvgmGQcvHf6f6g9) · [프로젝트 README](../../README.md#erd)

## 도메인별 ERD

현재 DBML과 Flyway SQL의 41개 테이블을 11개 도메인으로 나눴습니다. 각 페이지에서 테이블 전체 컬럼 캡처, SQL, 다른 도메인 참조와 이전·다음 페이지를 확인할 수 있습니다.

| 도메인 | 테이블 수 | 내용 |
| --- | ---: | --- |
| [사용자](erd/user.md#테이블-이미지) | 1 | 사용자 정보·역할·멤버십 |
| [이벤트·응모](erd/event.md#테이블-이미지) | 5 | 이벤트·경품·응모자·응모 요청·배너 |
| [출석](erd/attendance.md#테이블-이미지) | 5 | 일일·연속 출석과 보상 기록 |
| [미션](erd/mission.md#테이블-이미지) | 10 | 퀴즈·설문·제출·보상 기록 |
| [게임](erd/game.md#테이블-이미지) | 4 | 게임·플레이·통계·보상 기록 |
| [보상 정책](erd/reward.md#테이블-이미지) | 1 | 출석·미션·게임의 보상 정책 |
| [응모권](erd/ticket.md#테이블-이미지) | 5 | 지갑·원장·배분·반환·회수 |
| [어뷰징 검토](erd/abuse.md#테이블-이미지) | 1 | 의심 행위 탐지와 관리자 검토 |
| [추첨·발표](erd/drawing.md#테이블-이미지) | 6 | 추첨 실행·후보·결과·당첨·발표 |
| [알림](erd/notification.md#테이블-이미지) | 2 | 알림 생성 작업과 사용자 알림 |
| [감사 로그](erd/audit.md#테이블-이미지) | 1 | 변경 행위와 변경 전후 데이터 |

## 설계·스키마 문서
- [API·DB 대응과 스키마 검토](api-schema-mapping.md): 공용 API 초안과 현재 Flyway SQL의 대응 및 백엔드 저장 설계 검토 항목.
- [GD-36 추첨 선정 저장 설계 검토](drawing-selection-storage.md): 추첨 후보의 실제 차감 수량·가중치와 실행 스냅샷의 현재 스키마 대응.
- [통합 스키마 DBML](schema.dbml): 현재 스키마의 테이블·컬럼·관계를 나타내는 ERD. 생성 컬럼과 CHECK는 주석으로 설명하며, 실제 DB 적용 기준은 Flyway SQL이다.
- [MySQL 마이그레이션](../../storage/db/src/main/resources/db/migration): V001~V011은 41개 테이블의 초기 DDL이다. 이벤트·배너의 논리 삭제 시각, 출석 기준일, 사용자별 하루 1회 UNIQUE와 미션 답변의 복합 외래 키는 초기 정의에 반영되어 있다.
- [출석·연속 출석·미션·게임 테이블 분리 설계](reward-tables.md): 논리 설계 초안 및 DDL 작성 전 미결정 항목.
