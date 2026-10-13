-- GD-175: 같은 제출 ID(Idempotency-Key)의 재전송이 처음 요청과 같은 본문인지 판정하려고 처음 요청의 해시를 보존한다.
-- 이미 정답으로 완료한 퀴즈 문항의 답변은 저장하지 않으므로 저장된 답변만으로는 처음 요청을 복원할 수 없다.
-- 이 컬럼을 기록하기 전의 제출은 빈 문자열이라 어떤 요청과도 같지 않다.
ALTER TABLE `mission_submissions`
  ADD COLUMN `request_hash` char(64) NOT NULL DEFAULT '' COMMENT '처음 요청(사용자·미션·답변)의 SHA-256 16진수. 재전송 본문 비교용';
