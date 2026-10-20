-- =====================================================================
-- 初期スキーマ（Phase1: 組織・文書・制度・FAQ・辞書・アクセス制御・監査・検索ログ）
-- 設計: docs/DOMAIN_MODEL.md §4-5
--
-- 方針
-- - 区分値は PostgreSQL の ENUM 型ではなく varchar + CHECK 制約で持つ
--   （値の追加が ALTER TYPE なしでできる、JPA / JDBC で扱いやすい）
-- - 主キーは uuid。アプリ側で UUIDv7 を採番し、DB の既定値は v4（gen_random_uuid）
-- - 拡張（pg_bigm / vector / pgcrypto）は DB イメージの初期化で作成済み。ここでは存在を保証するだけ
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_bigm;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------
-- Organization（組織）
-- ---------------------------------------------------------------------
CREATE TABLE departments (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code        varchar(32)  NOT NULL UNIQUE,
  name        varchar(100) NOT NULL,
  description text,
  parent_id   uuid REFERENCES departments(id),
  sort_order  int NOT NULL DEFAULT 0,
  is_active   boolean NOT NULL DEFAULT true,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_departments_not_self_parent CHECK (parent_id IS NULL OR parent_id <> id)
);
COMMENT ON TABLE departments IS '部署。文書・制度の所管と、編集権限の単位になる';

CREATE TABLE contacts (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code           varchar(32)  NOT NULL UNIQUE,
  department_id  uuid NOT NULL REFERENCES departments(id),
  name           varchar(100) NOT NULL,
  description    text,
  email          varchar(255),
  phone_ext      varchar(20),
  chat_url       varchar(500),
  office_hours   varchar(100),
  is_primary     boolean NOT NULL DEFAULT false,
  sort_order     int NOT NULL DEFAULT 0,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_contacts_has_channel CHECK (email IS NOT NULL OR phone_ext IS NOT NULL OR chat_url IS NOT NULL)
);
-- 部署の代表窓口は最大1つ（窓口を導出できないときのフォールバック先）
CREATE UNIQUE INDEX uq_contacts_primary_per_department ON contacts (department_id) WHERE is_primary;
CREATE INDEX idx_contacts_department ON contacts (department_id);
COMMENT ON TABLE contacts IS '問い合わせ窓口。検索結果には必ず1件以上の窓口を表示する';

CREATE TABLE users (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email            varchar(255) NOT NULL UNIQUE,
  display_name     varchar(100) NOT NULL,
  department_id    uuid REFERENCES departments(id),
  role             varchar(20) NOT NULL DEFAULT 'EMPLOYEE',
  employment_type  varchar(20) NOT NULL DEFAULT 'REGULAR',
  joined_on        date,
  password_hash    varchar(255),
  idp_subject      varchar(255) UNIQUE,
  is_active        boolean NOT NULL DEFAULT true,
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_users_role CHECK (role IN ('EMPLOYEE', 'MANAGER', 'CONTENT_EDITOR', 'SYSTEM_ADMIN')),
  CONSTRAINT ck_users_employment_type CHECK (employment_type IN ('REGULAR', 'CONTRACT', 'PART_TIME'))
);
COMMENT ON COLUMN users.password_hash IS 'MVP のローカル認証用（BCrypt）。SSO 移行後は NULL';
COMMENT ON COLUMN users.idp_subject IS '外部 IdP（OIDC）の subject。将来の SSO 用';

-- ---------------------------------------------------------------------
-- Content（文書・版・セクション）
-- ---------------------------------------------------------------------
CREATE TABLE categories (
  id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  parent_id  uuid REFERENCES categories(id),
  slug       varchar(100) NOT NULL UNIQUE,
  name       varchar(100) NOT NULL,
  sort_order int NOT NULL DEFAULT 0
);

CREATE TABLE tags (
  id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name      varchar(50) NOT NULL UNIQUE,
  tag_type  varchar(20) NOT NULL DEFAULT 'FREE',
  CONSTRAINT ck_tags_type CHECK (tag_type IN ('TARGET', 'TOPIC', 'FREE'))
);

CREATE TABLE documents (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code                 varchar(32) NOT NULL UNIQUE,
  title                varchar(200) NOT NULL,
  summary              text,
  document_type        varchar(20) NOT NULL,
  category_id          uuid NOT NULL REFERENCES categories(id),
  owner_department_id  uuid NOT NULL REFERENCES departments(id),
  visibility           varchar(20) NOT NULL DEFAULT 'INTERNAL',
  status               varchar(20) NOT NULL DEFAULT 'DRAFT',
  current_version_id   uuid,
  review_due_on        date,
  row_version          int NOT NULL DEFAULT 0,
  created_by           uuid REFERENCES users(id),
  updated_by           uuid REFERENCES users(id),
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_documents_type CHECK (document_type IN ('REGULATION', 'PROCEDURE', 'MANUAL', 'RULE', 'ORG_CHART')),
  CONSTRAINT ck_documents_visibility CHECK (visibility IN ('INTERNAL', 'MANAGERS', 'RESTRICTED', 'CONFIDENTIAL')),
  CONSTRAINT ck_documents_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  -- 公開中の文書は必ず公開版を指す
  CONSTRAINT ck_documents_published_has_version CHECK (status <> 'PUBLISHED' OR current_version_id IS NOT NULL)
);
CREATE INDEX idx_documents_category ON documents (category_id);
CREATE INDEX idx_documents_owner ON documents (owner_department_id);

CREATE TABLE document_tags (
  document_id uuid REFERENCES documents(id) ON DELETE CASCADE,
  tag_id      uuid REFERENCES tags(id) ON DELETE CASCADE,
  PRIMARY KEY (document_id, tag_id)
);

CREATE TABLE stored_files (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  storage_key    varchar(500) NOT NULL UNIQUE,
  original_name  varchar(255) NOT NULL,
  mime_type      varchar(100) NOT NULL,
  size_bytes     bigint NOT NULL CHECK (size_bytes >= 0),
  sha256         char(64) NOT NULL,
  uploaded_by    uuid REFERENCES users(id),
  created_at     timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE stored_files IS 'アップロードされた原本ファイル（Phase2 の PDF 取込で使用）';

CREATE TABLE document_versions (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_id     uuid NOT NULL REFERENCES documents(id),
  version_label   varchar(20) NOT NULL,
  effective_from  date NOT NULL,
  body_markdown   text,
  source_file_id  uuid REFERENCES stored_files(id),
  change_note     text,
  published_at    timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  UNIQUE (document_id, version_label),
  CONSTRAINT ck_document_versions_has_body CHECK (body_markdown IS NOT NULL OR source_file_id IS NOT NULL)
);
-- documents.current_version_id は、その文書自身の版を指す
ALTER TABLE document_versions ADD CONSTRAINT uq_document_versions_id_document UNIQUE (id, document_id);
ALTER TABLE documents ADD CONSTRAINT fk_documents_current_version
  FOREIGN KEY (current_version_id, id) REFERENCES document_versions (id, document_id)
  DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE sections (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  document_version_id  uuid NOT NULL REFERENCES document_versions(id) ON DELETE CASCADE,
  ordinal              int NOT NULL,
  path                 varchar(50),
  article_no           varchar(30),
  heading              varchar(200),
  content              text NOT NULL,
  page_from            int,
  page_to              int,
  search_text          text NOT NULL,
  UNIQUE (document_version_id, ordinal),
  CONSTRAINT ck_sections_pages CHECK (page_from IS NULL OR page_to IS NULL OR page_from <= page_to)
);
CREATE INDEX idx_sections_search_bigm ON sections USING gin (search_text gin_bigm_ops);
COMMENT ON TABLE sections IS '版を条・見出し単位に分割したもの。検索・引用・Embedding の最小単位';
COMMENT ON COLUMN sections.search_text IS '検索用に正規化したテキスト（NFKC・見出し連結）。原文は content';

-- ---------------------------------------------------------------------
-- Content（制度・きっかけ・FAQ）
-- ---------------------------------------------------------------------
CREATE TABLE programs (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code                 varchar(32) NOT NULL UNIQUE,
  name                 varchar(100) NOT NULL,
  name_kana            varchar(200),
  summary              text NOT NULL,
  eligibility          text,
  benefit              text,
  how_to_apply         text,
  deadline_note        varchar(200),
  required_documents   text,
  category_id          uuid REFERENCES categories(id),
  owner_department_id  uuid NOT NULL REFERENCES departments(id),
  visibility           varchar(20) NOT NULL DEFAULT 'INTERNAL',
  status               varchar(20) NOT NULL DEFAULT 'DRAFT',
  search_text          text NOT NULL,
  row_version          int NOT NULL DEFAULT 0,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_programs_visibility CHECK (visibility IN ('INTERNAL', 'MANAGERS', 'RESTRICTED', 'CONFIDENTIAL')),
  CONSTRAINT ck_programs_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
CREATE INDEX idx_programs_search_bigm ON programs USING gin (search_text gin_bigm_ops);
COMMENT ON TABLE programs IS '制度（祝金・休暇・手当・手続き）。根拠条文と窓口を持つ。ADR-0003';

CREATE TABLE program_basis (
  program_id   uuid REFERENCES programs(id) ON DELETE CASCADE,
  document_id  uuid NOT NULL REFERENCES documents(id),
  section_id   uuid REFERENCES sections(id) ON DELETE SET NULL,
  note         varchar(200),
  PRIMARY KEY (program_id, document_id)
);
COMMENT ON COLUMN program_basis.section_id IS '根拠の条。改定で旧版を指したら再リンク対象（document_id で辿れる）';

CREATE TABLE program_contacts (
  program_id  uuid REFERENCES programs(id) ON DELETE CASCADE,
  contact_id  uuid REFERENCES contacts(id),
  role        varchar(20) NOT NULL DEFAULT 'INQUIRY',
  sort_order  int NOT NULL DEFAULT 0,
  PRIMARY KEY (program_id, contact_id, role),
  CONSTRAINT ck_program_contacts_role CHECK (role IN ('APPLY', 'INQUIRY', 'PAYMENT'))
);

CREATE TABLE life_events (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  code        varchar(32) NOT NULL UNIQUE,
  name        varchar(50) NOT NULL,
  icon        varchar(20),
  description text,
  sort_order  int NOT NULL DEFAULT 0
);
COMMENT ON TABLE life_events IS 'きっかけ（結婚・出産・引越しなど利用者の状況）';

CREATE TABLE life_event_programs (
  life_event_id uuid REFERENCES life_events(id) ON DELETE CASCADE,
  program_id    uuid REFERENCES programs(id) ON DELETE CASCADE,
  relation      varchar(20) NOT NULL DEFAULT 'PRIMARY',
  sort_order    int NOT NULL DEFAULT 0,
  PRIMARY KEY (life_event_id, program_id),
  CONSTRAINT ck_life_event_programs_relation CHECK (relation IN ('PRIMARY', 'RELATED'))
);

CREATE TABLE faqs (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  question             text NOT NULL,
  answer_markdown      text NOT NULL,
  category_id          uuid REFERENCES categories(id),
  contact_id           uuid REFERENCES contacts(id),
  owner_department_id  uuid NOT NULL REFERENCES departments(id),
  visibility           varchar(20) NOT NULL DEFAULT 'INTERNAL',
  status               varchar(20) NOT NULL DEFAULT 'DRAFT',
  search_text          text NOT NULL,
  view_count           int NOT NULL DEFAULT 0,
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_faqs_visibility CHECK (visibility IN ('INTERNAL', 'MANAGERS', 'RESTRICTED', 'CONFIDENTIAL')),
  CONSTRAINT ck_faqs_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);
CREATE INDEX idx_faqs_search_bigm ON faqs USING gin (search_text gin_bigm_ops);

CREATE TABLE faq_links (
  faq_id       uuid REFERENCES faqs(id) ON DELETE CASCADE,
  target_type  varchar(20) NOT NULL,
  target_id    uuid NOT NULL,
  PRIMARY KEY (faq_id, target_type, target_id),
  CONSTRAINT ck_faq_links_target_type CHECK (target_type IN ('PROGRAM', 'DOCUMENT', 'SECTION'))
);

-- ---------------------------------------------------------------------
-- Navigation（辞書・設定）
-- ---------------------------------------------------------------------
CREATE TABLE navigation_keywords (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  term             varchar(100) NOT NULL,
  normalized_term  varchar(100) NOT NULL,
  target_type      varchar(20) NOT NULL,
  target_id        uuid NOT NULL,
  weight           real NOT NULL DEFAULT 1.0 CHECK (weight > 0),
  UNIQUE (normalized_term, target_type, target_id),
  CONSTRAINT ck_navigation_keywords_target_type
    CHECK (target_type IN ('PROGRAM', 'LIFE_EVENT', 'DOCUMENT', 'CONTACT', 'FAQ'))
);
CREATE INDEX idx_navigation_keywords_term ON navigation_keywords (normalized_term);
COMMENT ON TABLE navigation_keywords IS '語 → 制度・きっかけ等への直接マッピング（例: 入籍 → きっかけ「結婚」）';

CREATE TABLE app_settings (
  key         varchar(100) PRIMARY KEY,
  value       text NOT NULL,
  updated_at  timestamptz NOT NULL DEFAULT now()
);
COMMENT ON TABLE app_settings IS 'アプリ設定（例: navigation.default_contact_code = 総合窓口）';

-- ---------------------------------------------------------------------
-- Access / Audit（アクセス制御・監査）
-- ---------------------------------------------------------------------
CREATE TABLE access_rules (
  id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  resource_type   varchar(20) NOT NULL,
  resource_id     uuid NOT NULL,
  principal_type  varchar(20) NOT NULL,
  principal_id    varchar(64) NOT NULL,
  UNIQUE (resource_type, resource_id, principal_type, principal_id),
  CONSTRAINT ck_access_rules_resource_type CHECK (resource_type IN ('DOCUMENT', 'PROGRAM', 'FAQ')),
  CONSTRAINT ck_access_rules_principal_type CHECK (principal_type IN ('DEPARTMENT', 'ROLE', 'USER'))
);
CREATE INDEX idx_access_rules_resource ON access_rules (resource_type, resource_id);
COMMENT ON TABLE access_rules IS '公開範囲 RESTRICTED / CONFIDENTIAL の閲覧を許可する部署・ロール・ユーザー。ADR-0004';

CREATE TABLE audit_logs (
  id             bigserial PRIMARY KEY,
  actor_id       uuid,
  action         varchar(50) NOT NULL,
  resource_type  varchar(30),
  resource_id    uuid,
  before_data    jsonb,
  after_data     jsonb,
  ip_address     inet,
  occurred_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_occurred ON audit_logs (occurred_at);
COMMENT ON TABLE audit_logs IS '監査ログ（追記専用）。更新・削除はトリガーで禁止する';

-- 監査ログは追記専用（アプリのバグや誤操作でも書き換えられないようにする）
CREATE FUNCTION forbid_audit_log_modification() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'audit_logs is append-only (% is not allowed)', TG_OP;
END;
$$;
CREATE TRIGGER trg_audit_logs_append_only
  BEFORE UPDATE OR DELETE ON audit_logs
  FOR EACH ROW EXECUTE FUNCTION forbid_audit_log_modification();

-- ---------------------------------------------------------------------
-- Insight（検索ログ）
-- ---------------------------------------------------------------------
CREATE TABLE search_logs (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_hash         char(64),
  department_id     uuid,
  query             text NOT NULL,
  normalized_query  text NOT NULL,
  mode              varchar(10) NOT NULL DEFAULT 'KEYWORD',
  result_counts     jsonb NOT NULL,
  top_hits          jsonb,
  latency_ms        int,
  created_at        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT ck_search_logs_mode CHECK (mode IN ('KEYWORD', 'HYBRID', 'RAG'))
);
CREATE INDEX idx_search_logs_created ON search_logs (created_at);
COMMENT ON COLUMN search_logs.user_hash IS 'HMAC(user_id) による仮名化。個人を直接特定できる値は保存しない';

CREATE TABLE search_clicks (
  search_log_id  uuid REFERENCES search_logs(id) ON DELETE CASCADE,
  target_type    varchar(20) NOT NULL,
  target_id      uuid NOT NULL,
  rank           int NOT NULL,
  clicked_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_search_clicks_log ON search_clicks (search_log_id);

CREATE TABLE feedbacks (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  search_log_id  uuid REFERENCES search_logs(id) ON DELETE CASCADE,
  helpful        boolean NOT NULL,
  comment        text,
  created_at     timestamptz NOT NULL DEFAULT now()
);
