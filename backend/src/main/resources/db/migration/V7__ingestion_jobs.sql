-- PDF の取り込みジョブ（ADR-0010）。backend のワーカーが SELECT ... FOR UPDATE SKIP LOCKED で1件ずつ取り出す
CREATE TABLE ingestion_jobs (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  file_id            uuid NOT NULL REFERENCES stored_files(id),
  document_id        uuid NOT NULL REFERENCES documents(id),
  version_label      varchar(20) NOT NULL,
  effective_from     date NOT NULL,
  change_note        text,
  status             varchar(20) NOT NULL DEFAULT 'QUEUED',
  attempts           int NOT NULL DEFAULT 0,
  -- 次に実行してよい時刻（一時的な失敗の再試行を少し待つ）
  next_run_at        timestamptz NOT NULL DEFAULT now(),
  -- 失敗の理由（利用者に見せる文）
  error_message      text,
  result_version_id  uuid REFERENCES document_versions(id),
  created_by         uuid NOT NULL REFERENCES users(id),
  created_at         timestamptz NOT NULL DEFAULT now(),
  started_at         timestamptz,
  finished_at        timestamptz,
  CONSTRAINT ck_ingestion_jobs_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
  CONSTRAINT ck_ingestion_jobs_attempts CHECK (attempts >= 0)
);
CREATE INDEX idx_ingestion_jobs_queue ON ingestion_jobs (next_run_at) WHERE status = 'QUEUED';
CREATE INDEX idx_ingestion_jobs_document ON ingestion_jobs (document_id, created_at DESC);
COMMENT ON TABLE ingestion_jobs IS 'PDF の取り込みジョブ。成功すると result_version_id の下書きの版ができる';
