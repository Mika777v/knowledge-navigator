-- 同じ内容のファイルは1つだけ保存する（再アップロードは既存のものを使う。ADR-0010）
CREATE UNIQUE INDEX uq_stored_files_sha256 ON stored_files (sha256);
