-- 類義語辞書（E12-T2・PRD F-301）。グループの語はどれも同じ意味として検索を広げる（例: 育休 = 育児休業 = 育児休暇）
CREATE TABLE synonym_groups (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  -- 代表の語（管理画面の表示用。グループの語の1つ）
  canonical    varchar(100) NOT NULL,
  row_version  int NOT NULL DEFAULT 0,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE synonym_groups IS '類義語のグループ。質問にグループの語が含まれていたら、ほかの語でも検索する';

CREATE TABLE synonym_terms (
  group_id         uuid NOT NULL REFERENCES synonym_groups(id) ON DELETE CASCADE,
  term             varchar(100) NOT NULL,
  -- TextNormalizer と同じ正規化（NFKC・小文字）。1つの語は1つのグループにだけ入る
  normalized_term  varchar(100) NOT NULL UNIQUE,
  PRIMARY KEY (group_id, normalized_term)
);
