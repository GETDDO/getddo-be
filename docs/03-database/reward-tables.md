# 출석·연속 출석·미션·게임 테이블 분리 설계

- 상태: 논리 설계 초안. 실제 DDL·마이그레이션·API 구현은 수행하지 않았다.
- 기준: 공용 명세의 출석·미션·게임·응모권 규칙 및 ADR-008(미결정 정책 최종 확정).
- 확정 범위: 출석·연속 출석·미션(퀴즈·설문)·게임 테이블 분리. 아래 이름·컬럼·정책 이력 구조는 구현 제안이다.
- DB 제품·연결 설정 및 사용자·응모권 참조 테이블이 아직 없으므로 자료형·외래 키·실제 마이그레이션은 구현 단계에서 정한다.

## 테이블 구성

| 영역 | 테이블 제안 | 주요 데이터 | 중복 방지 기준 |
| --- | --- | --- | --- |
| 기본 출석 | attendance | id, user_id, attendance_date, attended_at, reward_ticket_count | 사용자·UTC 출석 기준일 유일 |
| 연속 출석 상태 | attendance_streak | id, user_id, attendance_month, last_attendance_date, consecutive_days | 사용자·UTC 기준월 유일 |
| 연속 출석 단계 달성 | attendance_streak_reward | id, streak_id, milestone_days, reward_ticket_count, earned_at | 월별 상태·단계 유일 |
| 출석 정책 이력 | attendance_policy | id, effective_date, daily_reward_count | 적용일 유일; 다음 일일 기준일부터 적용 |
| 연속 출석 단계 정책 | attendance_streak_policy | id, effective_month, milestone_days, reward_ticket_count | 적용월·단계 일수 유일; 다음 달부터 적용 |
| 미션 공통 정의 | mission | id, type(QUIZ/SURVEY), title, description, reward_ticket_count, starts_at, ends_at, created_by | 서비스 공통, 이벤트 외래 키 없음 |
| 퀴즈 정의 | mission_quiz | mission_id, question, correct_answer | 미션별 정의; 복수 문항·선택지 형식은 상세 설계 |
| 설문 정의 | mission_survey | mission_id, 문항·필수 여부 | 문항 형식에 따라 하위 테이블 상세화 |
| 미션 달성 | mission_completion | id, user_id, mission_id, received_at, completed_at | 사용자·미션 유일 |
| 설문 응답 | mission_survey_response | id, completion_id, question_id, answer | 달성 건·문항별 응답 보존 |
| 게임 정의 | game | id, name, 규칙 | 미션과 별도 관리 |
| 게임 플레이 | game_play | id, user_id, game_id, validation_status, score, played_at | 서버 발급 플레이 ID별 결과 확정 1회 |
| 게임 일일 보상 | game_daily_reward | id, user_id, game_id, reward_date, play_id | 사용자·게임·UTC 보상 기준일 유일 |

최종 결정에 따라 미션의 `repeat_policy`, 반복 달성용 `completion_key`, 재달성을 위한 `mission_content` 버전 테이블 제안을 제거했다. 관리자가 매번 새 미션을 수동 등록하며 기존 미션을 반복 활성화하지 않는다. 사용자·미션 유일 제약으로 완료와 보상을 한 번만 반영한다.

출석 누락으로 연속 일수가 초기화돼도 이미 지급한 월별 단계 이력은 유지한다. 적용일·적용월 정책을 보존해 관리자 수정이 진행 중인 기준일·기준월을 바꾸지 않게 한다. 단계는 1~28일 범위에서 중복 없이 오름차순으로 검증한다. 초기 단계·보상은 7/14/28일 및 1/3/7장이다.

## 달성·지급 정합성

- 퀴즈 오답에는 완료·지급 기록을 생성하지 않는다. 운영 기간 내 오답 재도전은 총횟수 제한이 없으며, 사용자별 요청 빈도 제한만 별도로 적용한다.
- 미션의 시작 이상·종료 미만에 서버가 받은 제출을 인정한다. `received_at`은 신뢰할 수 있는 서버 시각으로 기록하고 클라이언트 입력값을 사용하지 않는다. 검증·완료가 종료 후여도 도착 시각과 완료 조건으로 판정한다.
- 설문은 필수 문항 검증 후 최종 제출을 완료로 기록하며, 재제출로 기존 응답을 덮어쓰거나 새 보상을 생성하지 않는다.
- 각 출석·단계 달성·미션 달성·게임 일일 보상 ID를 응모권 지급 근거에 연결하고 지급 근거별 유일 제약으로 중복을 방지한다.
- 달성·지급 이력·잔액은 같은 트랜잭션에서 성공하거나 롤백한다. 잠금 방식과 실제 SQL은 DB 확정 후 설계한다.
- 출석·게임 일일 보상은 00:00 UTC(09:00 KST)를 기준으로 한다.
- 게임 재제출은 처리 중이면 처리 중으로 안내하고, 완료됐으면 최초 확정 결과를 반환한다. 점수·누적 횟수·보상을 추가하지 않는다.

## 응모·반환·만료 연계

- 이벤트 응모 완료는 응모 기록·차감·차감 이력이 DB 트랜잭션으로 확정된 시점이다. 미션의 서버 도착 시각 기준과 다르다.
- 만료 전 차감·접수 완료된 응모만 인정하며, 이후 월이 바뀌어도 완료된 응모를 무효화하지 않는다.
- 취소와 대상 응모권 반환을 함께 완료한다. 반환이 실패했는데 취소만 완료된 것으로 응답하지 않는다. 동일 원본 차감 건의 중복 반환을 방지한다.
- 일반 지급분은 지급 UTC 기준월 다음 달 1일 00:00 UTC에 만료한다. 반환분은 반환 UTC 기준월의 다음 달까지 유효하며 그 다음 달 1일 00:00 UTC에 만료한다.
- 지급·반환 건별 잔여 수량·만료일·차감 출처를 보존한다. 만료일이 다른 잔액을 하나의 월 잔액으로 덮어쓰지 않는다. 별도의 차감 우선순위는 이번 결정으로 추가하지 않았다.

## 남은 구현 설계

1. DB 제품·버전과 사용자·응모권 테이블의 키 구조 및 외래 키.
2. 퀴즈·설문 문항·선택지·응답 자료형.
3. 게임 담당자가 공유할 게임 규칙·검증 방식·결과 데이터 형식.
4. 출석 정책 이력의 물리 구조, 동시성 제어 및 취소·반환 트랜잭션 설계.
