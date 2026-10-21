-- =====================================================================
-- V2: 制度の根拠を「条」単位で持てるようにし、FAQ にコードを追加する（#33）
--
-- - program_basis: 1つの制度が同じ文書の複数の条を根拠にできるようにする
--   （例: 通勤手当 = 通勤手当規程 第4条（金額）と第7条（申請））
--   article_no を持たせ、文書の改定でセクションが作り直されても条番号で再リンクできるようにする
-- - faqs.code: seed の再投入やデータ移行で FAQ を一意に特定するためのコード
-- =====================================================================

ALTER TABLE program_basis DROP CONSTRAINT program_basis_pkey;
ALTER TABLE program_basis ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE program_basis ADD PRIMARY KEY (id);
ALTER TABLE program_basis ADD COLUMN article_no varchar(30);
ALTER TABLE program_basis ADD COLUMN sort_order int NOT NULL DEFAULT 0;
-- 条を指定しない根拠（文書全体）は1文書につき1件、条を指定する根拠は条ごとに1件
CREATE UNIQUE INDEX uq_program_basis_article
  ON program_basis (program_id, document_id, COALESCE(article_no, ''));
CREATE INDEX idx_program_basis_program ON program_basis (program_id);
COMMENT ON COLUMN program_basis.article_no IS '根拠の条番号（例: 第5条）。改定後は公開版の同じ条番号のセクションに再リンクする';

ALTER TABLE faqs ADD COLUMN code varchar(32);
UPDATE faqs SET code = 'FAQ-' || left(replace(id::text, '-', ''), 12) WHERE code IS NULL;
ALTER TABLE faqs ALTER COLUMN code SET NOT NULL;
ALTER TABLE faqs ADD CONSTRAINT uq_faqs_code UNIQUE (code);
