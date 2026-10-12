# 데이터베이스 — 응모권 · 일회성 미션 · 게임

담당: 윤태형. `core`는 여기 나오는 엔티티에 대응하는 순수 도메인 객체를, `storage:db`는 `@Entity`를 둔다(패키지 배치는 README 기준).

## 응모권 원장 (Source of Truth)

```sql
CREATE TABLE ticket_ledger (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    seq             BIGINT       NOT NULL,   -- 유저별 전역 순번(1부터)
    delta           INT          NOT NULL,   -- +적립 / -사용. 0 금지
    balance_after   INT          NOT NULL,   -- 이 행 반영 직후 잔액
    reason_code     VARCHAR(30)  NOT NULL,   -- ATTENDANCE|MISSION|STREAK_BONUS|GAME
                                             -- |ENTRY_USE|ENTRY_TOPUP|ADMIN_ADJUST|MONTHLY_RESET
    ref_type        VARCHAR(30)  NOT NULL,   -- ATTENDANCE|MISSION|GAME|ENTRY|EVENT|MANUAL
    ref_id          BIGINT       NULL,
    memo            VARCHAR(200) NULL,
    idempotency_key VARCHAR(160) NOT NULL,   -- 멱등성 최종 방어선 (전역 UNIQUE)
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_idem (idempotency_key),
    UNIQUE KEY uk_ledger_user_seq (user_id, seq),
    KEY idx_ledger_user_desc (user_id, id DESC),
    KEY idx_ledger_created (created_at),
    CONSTRAINT fk_ledger_user  FOREIGN KEY (user_id)  REFERENCES virtual_user(id),
    CONSTRAINT chk_ledger_delta   CHECK (delta <> 0),
    CONSTRAINT chk_ledger_balance CHECK (balance_after >= 0)
) ENGINE=InnoDB;

-- 원장 불변성 강제: 애플리케이션에 버그가 있어도 DB가 거부한다
DELIMITER $$
CREATE TRIGGER trg_ticket_ledger_block_update
BEFORE UPDATE ON ticket_ledger FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'ticket_ledger is append-only: UPDATE is forbidden';
END$$
DELIMITER ;

CREATE TABLE user_ticket_balance (
    user_id    BIGINT  NOT NULL,
    balance    INT     NOT NULL DEFAULT 0,
    last_seq   BIGINT  NOT NULL DEFAULT 0,
    version    BIGINT  NOT NULL DEFAULT 0,   -- 낙관적 락(우회 탐지용 안전망)
    PRIMARY KEY (user_id),
    CONSTRAINT fk_balance_user FOREIGN KEY (user_id) REFERENCES virtual_user(id)
) ENGINE=InnoDB;
```

**정합성 보장 원칙**
- 잔액 갱신은 `TicketLedgerAppender`(단일 진입점)를 통해서만 — 비관적 락(`SELECT ... FOR UPDATE`)으로 잔액 행을 잠그고, 원장 append → 잔액 갱신을 한 트랜잭션에서 처리(`Propagation.MANDATORY`, 호출자가 이미 트랜잭션을 열고 있어야 함).
- 잔액 행 지연 생성(lazy creation)은 `INSERT IGNORE`로 동시 INSERT 경합을 DB가 흡수.
- 중복 지급 방지는 DB UNIQUE 제약이 최종 방어선(애플리케이션의 빠른 실패 체크는 성능 목적일 뿐).
- 3중 대조 감사: `user_id` 단위로 `SUM(delta) == balance == 마지막 행의 balance_after`.

## 미션

```sql
CREATE TABLE mission (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    event_id          BIGINT       NULL,     -- NULL=플랫폼 전체(홈 노출), NOT NULL=그 이벤트 페이지 노출
    mission_scope_key BIGINT AS (COALESCE(event_id, 0)) STORED,
    code              VARCHAR(50)  NOT NULL,
    title             VARCHAR(100) NOT NULL,
    description       VARCHAR(300) NULL,
    mission_type      VARCHAR(20)  NOT NULL,  -- ACTION | QUIZ | SURVEY | REFERRAL | EXTERNAL_VERIFY
    repeat_cycle      VARCHAR(20)  NOT NULL,  -- DAILY | ONCE (REFERRAL은 항상 ONCE)
    reward_tickets    INT          NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    starts_at         DATETIME(6)  NULL,
    ends_at           DATETIME(6)  NULL,
    quiz_question     VARCHAR(300) NULL,      -- QUIZ 전용
    quiz_choices      JSON         NULL,
    quiz_answer_index INT          NULL,
    survey_questions  JSON         NULL,      -- SURVEY 전용
    external_check_code VARCHAR(50) NULL,     -- EXTERNAL_VERIFY 전용(화면 표시용, 판정에는 미관여)
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mission_scope_code (mission_scope_key, code),
    KEY idx_mission_event_active (event_id, active),
    CONSTRAINT fk_mission_event FOREIGN KEY (event_id) REFERENCES event(id),
    CONSTRAINT chk_mission_reward CHECK (reward_tickets > 0),
    CONSTRAINT chk_mission_type CHECK (mission_type IN
                            ('ACTION','QUIZ','SURVEY','REFERRAL','EXTERNAL_VERIFY')),
    CONSTRAINT chk_mission_requires_event CHECK (
        mission_type NOT IN ('REFERRAL','EXTERNAL_VERIFY') OR event_id IS NOT NULL),
    CONSTRAINT chk_mission_referral_once CHECK (
        mission_type <> 'REFERRAL' OR repeat_cycle = 'ONCE')
) ENGINE=InnoDB;

CREATE TABLE mission_completion (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    mission_id       BIGINT      NOT NULL,
    business_date    DATE        NOT NULL,   -- KST 영업일. ONCE 미션은 '1970-01-01' 센티넬
    response         JSON        NULL,       -- QUIZ 제출답/SURVEY 응답 보존(사후 감사용)
    ledger_id        BIGINT      NOT NULL,   -- 이 적립을 만든 원장 행
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_completion_natural (user_id, mission_id, business_date),
    KEY idx_completion_user_date (user_id, business_date),
    CONSTRAINT fk_completion_user    FOREIGN KEY (user_id)    REFERENCES virtual_user(id),
    CONSTRAINT fk_completion_mission FOREIGN KEY (mission_id) REFERENCES mission(id),
    CONSTRAINT fk_completion_ledger  FOREIGN KEY (ledger_id)  REFERENCES ticket_ledger(id)
) ENGINE=InnoDB;
```

> `mission_scope_key`가 생성 컬럼인 이유: `event_id`가 NULL인 행끼리는 MySQL UNIQUE 인덱스에서 서로 다르다고 취급되므로, `(event_id, code)`로는 플랫폼 전체 미션의 code 중복을 막을 수 없다. `COALESCE(event_id, 0)` STORED 컬럼으로 우회한다.
> `ONCE` 미션의 센티넬 트릭: 계정당 1회 미션은 `business_date`를 `1970-01-01`로 고정 저장한다. `DAILY`/`ONCE` 두 주기를 동일한 UNIQUE 제약 하나로 처리할 수 있다.
> **출석(`ATTENDANCE`)은 이 테이블에 없다** — 별도 팀원 담당 테이블. 이 도메인은 그쪽이 호출하는 원장 append 인터페이스만 제공한다.

## 게임

```sql
CREATE TABLE game (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(300) NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_game_code (code)
) ENGINE=InnoDB;

-- 플레이 횟수 무제한 — append-only. 최고점/누적점수는 이 로그에서 파생된다.
-- score는 서버(judge 로직)가 계산/확정한 값. success는 score와 별개의 지급 판정용 boolean.
CREATE TABLE game_play_result (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    game_id    BIGINT      NOT NULL,
    score      INT         NOT NULL,
    success    BOOLEAN     NOT NULL,
    played_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_play_user_game (user_id, game_id, played_at DESC),
    CONSTRAINT fk_play_user FOREIGN KEY (user_id) REFERENCES virtual_user(id),
    CONSTRAINT fk_play_game FOREIGN KEY (game_id) REFERENCES game(id),
    CONSTRAINT chk_play_score CHECK (score >= 0)
) ENGINE=InnoDB;

-- 유저·게임별 개인 최고점 + 누적 점수. game_play_result가 원본, 이 테이블은 조회용 캐시.
CREATE TABLE user_game_stat (
    user_id          BIGINT      NOT NULL,
    game_id          BIGINT      NOT NULL,
    best_score       INT         NOT NULL DEFAULT 0,
    cumulative_score BIGINT      NOT NULL DEFAULT 0,
    play_count       INT         NOT NULL DEFAULT 0,
    updated_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                      ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, game_id),
    CONSTRAINT fk_stat_user FOREIGN KEY (user_id) REFERENCES virtual_user(id),
    CONSTRAINT fk_stat_game FOREIGN KEY (game_id) REFERENCES game(id)
) ENGINE=InnoDB;
```

> 왜 `mission`/`mission_completion`을 재사용하지 않는가: 미션은 "완료=성공"이라는 1:1 구조(하루 1회 완료 시도)를 전제로 한다. 게임은 하루에 몇 번을 플레이해도 되고 그 기록을 전부 남겨야 하므로 "완료 여부"가 아니라 "매 시도의 결과"를 쌓는 구조가 필요하다 — 데이터 모양이 근본적으로 다르다.
> 하루 1장 상한은 `ticket_ledger.idempotency_key`(`"GAME:{userId}:{businessDate}"`, 게임ID 미포함)로 강제한다 — 게임 도메인이 새로 발명해야 하는 락 로직은 없다.

## 참고: 외부 참조

- `virtual_user(id)` — 가상 회원. 다른 팀원 도메인.
- `event(id)` — 이벤트. 다른 팀원 도메인. `mission.event_id`, `entry` 등이 참조.
