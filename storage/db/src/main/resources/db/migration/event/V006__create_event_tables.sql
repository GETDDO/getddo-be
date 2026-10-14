-- Source: docs/03-database/schema.dbml
-- GD-93: Initial schema; version numbers retained by user request.
-- Initial tables and foreign keys owned by event.

CREATE TABLE `events` (
  `id` binary(16) NOT NULL COMMENT '이벤트 ID',
  `title` varchar(200) NOT NULL COMMENT '이벤트 제목',
  `description` text NOT NULL COMMENT '상세 설명',
  `image_key` varchar(500) COMMENT '이미지 저장소 경로',
  `event_type` ENUM ('NO_TICKET', 'TICKET') NOT NULL COMMENT 'NO_TICKET=응모권 미사용 / TICKET=응모권 사용',
  `weighting_enabled` boolean NOT NULL DEFAULT 0 COMMENT '추첨 가중치 적용 여부',
  `max_tickets_per_user` int COMMENT '사용자별 이벤트 누적 상한',
  `starts_at` datetime(6) NOT NULL COMMENT '응모 시작 시각 UTC',
  `ends_at` datetime(6) NOT NULL COMMENT '응모 마감 시각 UTC',
  `status` ENUM ('SCHEDULED', 'OPEN', 'CLOSED', 'DRAW_CONFIRMED', 'PUBLISHED', 'CANCELED', 'REDRAWING', 'NO_ENTRANTS', 'NO_ELIGIBLE_ENTRANTS') NOT NULL COMMENT '이벤트 상태',
  `canceled_at` datetime(6) COMMENT '취소 시각 UTC',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각 UTC',
  `deleted_at` datetime(6) COMMENT '논리 삭제 시각 UTC',
  `membership_rule` ENUM ('excellent', 'vip', 'vvip') NOT NULL COMMENT 'excellent=우수 / vip=VIP / vvip=VVIP',
  CONSTRAINT `chk_event_ticket_limit` CHECK (max_tickets_per_user IS NULL OR max_tickets_per_user > 0),
  CONSTRAINT `chk_event_dates` CHECK (ends_at > starts_at),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event_prizes` (
  `id` binary(16) NOT NULL COMMENT '이벤트 경품 ID',
  `event_id` binary(16) NOT NULL COMMENT '연결 이벤트 ID',
  `prize_rank` int NOT NULL COMMENT '경품 등수',
  `name` varchar(200) NOT NULL COMMENT '경품명',
  `description` text COMMENT '상세 설명',
  `winner_count` int NOT NULL COMMENT '당첨 인원',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각 UTC',
  `image_key` varchar(500) COMMENT '경품 이미지 저장소 경로',
  CONSTRAINT `chk_event_prize_rank` CHECK (prize_rank >= 1),
  CONSTRAINT `chk_event_prize_winner_count` CHECK (winner_count > 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_event_prizes_1` (`event_id`, `prize_rank`),
  CONSTRAINT `fk_event_prizes_1` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event_participants` (
  `id` binary(16) NOT NULL COMMENT '이벤트 응모자 ID',
  `event_id` binary(16) NOT NULL COMMENT '연결 이벤트 ID',
  `user_id` binary(16) NOT NULL COMMENT '사용자 ID',
  `created_at` datetime(6) NOT NULL COMMENT '최초 응모 접수 완료·행 생성 시각 UTC',
  `used_ticket_count` bigint NOT NULL DEFAULT 0 COMMENT '사용자별 이벤트 누적 차감 응모권 수',
  CONSTRAINT `chk_participant_used` CHECK (used_ticket_count >= 0),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_event_participants_1` (`event_id`, `user_id`),
  UNIQUE KEY `uq_event_participants_2` (`id`, `event_id`, `user_id`),
  UNIQUE KEY `uq_event_participants_3` (`id`, `user_id`),
  CONSTRAINT `fk_event_participants_1` FOREIGN KEY (`event_id`) REFERENCES `events` (`id`),
  CONSTRAINT `fk_event_participants_2` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `event_entries` (
  `id` binary(16) NOT NULL COMMENT '클라이언트가 최초 요청 전에 발급한 응모 UUID. 같은 응모 재시도는 동일 ID, 새로운 추가 응모는 새 ID 사용. PK로 중복 저장 방지',
  `participant_id` binary(16) NOT NULL COMMENT '접수 완료된 응모의 이벤트 참여자 ID',
  `user_id` binary(16) NOT NULL COMMENT '사용자 ID',
  `requested_ticket_count` int NOT NULL COMMENT '요청한 응모권 차감 수량',
  `deducted_ticket_count` int NOT NULL DEFAULT 0 COMMENT '실제 차감 응모권 수량',
  `created_at` datetime(6) NOT NULL COMMENT '접수 완료된 응모의 처리 결과 기록 시각 UTC. 접수 성립·마감 판정은 차감과 함께 트랜잭션 확정 기준',
  CONSTRAINT `chk_entry_requested` CHECK (requested_ticket_count >= 0),
  CONSTRAINT `chk_entry_deducted` CHECK (deducted_ticket_count >= 0),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_event_entries_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `fk_event_entries_2` FOREIGN KEY (`participant_id`, `user_id`) REFERENCES `event_participants` (`id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `banners` (
  `id` binary(16) NOT NULL COMMENT '배너 ID',
  `image_key` varchar(500) NOT NULL COMMENT '이미지 저장소 경로',
  `display_order` int NOT NULL COMMENT '서비스 전체 노출 순서 1~5',
  `created_at` datetime(6) NOT NULL COMMENT '생성 시각 UTC',
  `updated_at` datetime(6) NOT NULL COMMENT '수정 시각 UTC',
  `event_url` varchar(500) COMMENT '이벤트 URL',
  CONSTRAINT `chk_banner_order` CHECK (display_order BETWEEN 1 AND 5),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_banners_1` (`display_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
