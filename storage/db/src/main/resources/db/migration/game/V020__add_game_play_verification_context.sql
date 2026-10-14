-- 기존 플레이를 임의의 seed·규칙으로 보정하지 않는다. 과거 행은 네 컬럼 모두 NULL로 보존한다.
ALTER TABLE game_plays
  ADD COLUMN rules_snapshot JSON NULL COMMENT '시작 시점 공개 규칙 원본',
  ADD COLUMN play_token_hash VARCHAR(64) NULL COMMENT '서버 발급 플레이 토큰 SHA-256',
  ADD COLUMN generator_version VARCHAR(50) NULL COMMENT '문제 생성 알고리즘 버전',
  ADD COLUMN generation_seed BIGINT NULL COMMENT '0이 아닌 unsigned 32-bit 문제 생성값',
  ADD CONSTRAINT chk_game_play_context CHECK (
    (rules_snapshot IS NULL AND play_token_hash IS NULL AND generator_version IS NULL AND generation_seed IS NULL)
    OR
    (rules_snapshot IS NOT NULL AND JSON_TYPE(rules_snapshot) = 'OBJECT'
      AND play_token_hash IS NOT NULL AND CHAR_LENGTH(play_token_hash) = 64
      AND generator_version IS NOT NULL AND CHAR_LENGTH(generator_version) > 0
      AND generation_seed IS NOT NULL AND generation_seed BETWEEN 1 AND 4294967295)
  ),
  ADD CONSTRAINT chk_game_play_result CHECK (
    rules_snapshot IS NULL
    OR (completed_at IS NULL AND score IS NULL AND result_hash IS NULL)
    OR (completed_at IS NOT NULL AND completed_at >= created_at
      AND result_hash IS NOT NULL AND CHAR_LENGTH(result_hash) = 64
      AND (score IS NULL OR score >= 0))
  );
