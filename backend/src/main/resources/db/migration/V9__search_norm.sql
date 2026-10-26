-- 表記ゆれ・活用のちがいを吸収する検索列（ADR-0011・E12-T1）。Sudachi の正規化形を空白で区切って並べる
-- （例: 「慶弔見舞金はいつ振り込まれますか」→「慶弔見舞い金 は いつ 振り込む れる ます か」）。
-- 値は backend が保存時に作る。既存の行・辞書の版を変えたときは、backend の起動時に作り直す（app_settings の search.norm_dictionary）。
-- 辞書がない環境では空のまま（search_text だけで検索する）
ALTER TABLE programs ADD COLUMN search_norm text NOT NULL DEFAULT '';
ALTER TABLE sections ADD COLUMN search_norm text NOT NULL DEFAULT '';
ALTER TABLE faqs ADD COLUMN search_norm text NOT NULL DEFAULT '';

CREATE INDEX idx_programs_search_norm_bigm ON programs USING gin (search_norm gin_bigm_ops);
CREATE INDEX idx_sections_search_norm_bigm ON sections USING gin (search_norm gin_bigm_ops);
CREATE INDEX idx_faqs_search_norm_bigm ON faqs USING gin (search_norm gin_bigm_ops);
