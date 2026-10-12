-- 과거 버전의 미충원 실행을 현재 데이터로 추정하여 채우지 않는다.
CREATE TABLE draw_publication_executions (
  publication_id binary(16) NOT NULL,
  draw_run_id binary(16) NOT NULL,
  event_id binary(16) NOT NULL,
  confirmation_id binary(16) NOT NULL,
  PRIMARY KEY (publication_id, draw_run_id),
  CONSTRAINT fk_publication_execution_version FOREIGN KEY (publication_id, event_id) REFERENCES draw_publications (id, event_id),
  CONSTRAINT fk_publication_execution_run FOREIGN KEY (draw_run_id, event_id) REFERENCES draw_runs (id, event_id),
  CONSTRAINT fk_publication_execution_confirmation FOREIGN KEY (confirmation_id, draw_run_id) REFERENCES draw_result_confirmations (id, draw_run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
