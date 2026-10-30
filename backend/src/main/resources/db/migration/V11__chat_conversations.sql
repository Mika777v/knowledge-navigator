-- AI アシスタントの会話の履歴（ADR-0014 の決定 3・E15-T1）
-- - 見られるのは本人だけ。API は常にログイン中の利用者の ID で絞る（管理者も他の人の履歴は見ない）
-- - 保持期間（knavi.assistant.retention-days）は最後に話した日（last_message_at）から数え、過ぎた会話は毎日の処理で消す
-- - 引用した条は ID だけを残し、表示するときにその時点の権限で読み直す（本文・題名はコピーしない）
CREATE TABLE chat_sessions (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id          uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  -- 最初の質問（一覧の見出し）
  title            varchar(200) NOT NULL,
  last_message_at  timestamptz NOT NULL DEFAULT now(),
  created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_chat_sessions_user ON chat_sessions (user_id, last_message_at DESC);
CREATE INDEX idx_chat_sessions_last_message ON chat_sessions (last_message_at);
COMMENT ON TABLE chat_sessions IS 'AI アシスタントの会話（ADR-0014）。本人だけが見られ、保持期間を過ぎたら消す';

CREATE TABLE chat_messages (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id          uuid NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
  -- 会話の中の順番（1 から）
  seq                 int NOT NULL,
  role                varchar(10) NOT NULL,
  content             text NOT NULL,
  -- ASSISTANT のときだけ。回答文を出せなかったら false と理由（AssistantUnanswerable の reason）
  answerable          boolean,
  unanswerable_reason varchar(40),
  model               varchar(100),
  created_at          timestamptz NOT NULL DEFAULT now(),
  UNIQUE (session_id, seq),
  CONSTRAINT ck_chat_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
  CONSTRAINT ck_chat_messages_seq CHECK (seq >= 1)
);

CREATE TABLE chat_citations (
  message_id  uuid NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
  -- 回答文の [n] の n
  ref         int NOT NULL,
  -- 条が消えたら NULL にし、「見られない根拠」として数える
  section_id  uuid REFERENCES sections(id) ON DELETE SET NULL,
  PRIMARY KEY (message_id, ref),
  CONSTRAINT ck_chat_citations_ref CHECK (ref >= 1)
);
