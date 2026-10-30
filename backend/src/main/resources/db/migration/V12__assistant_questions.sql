-- AI アシスタントの確認質問と、制度の条件（ADR-0014 の決定 1・E15-T2）
-- - 判定は LLM を使わず、この表の値から決まった手順で行う（対象・対象外・要確認）
-- - データはシード（data/seed/assistant_questions.yaml と programs.yaml の conditions）で入れる。管理画面は作らない
CREATE TABLE assistant_questions (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  -- EMPLOYMENT_TYPE など。条件・API はこのコードで指す
  code        varchar(40) NOT NULL UNIQUE,
  text        varchar(200) NOT NULL,
  -- 選択肢 [{"value": "REGULAR", "label": "正社員"}, ...]（表示の順）
  choices     jsonb NOT NULL,
  -- 利用者の属性から初めに選んでおく値の出どころ（EMPLOYMENT_TYPE = users.employment_type）。無ければ NULL
  profile_attribute varchar(40),
  sort_order  int NOT NULL DEFAULT 0,
  CONSTRAINT ck_assistant_questions_choices CHECK (jsonb_typeof(choices) = 'array' AND jsonb_array_length(choices) >= 2),
  CONSTRAINT ck_assistant_questions_profile CHECK (profile_attribute IS NULL OR profile_attribute IN ('EMPLOYMENT_TYPE'))
);
COMMENT ON TABLE assistant_questions IS 'AI アシスタントの確認質問（ADR-0014）。選択肢から選んで答える';

CREATE TABLE program_conditions (
  program_id      uuid NOT NULL REFERENCES programs(id) ON DELETE CASCADE,
  question_id     uuid NOT NULL REFERENCES assistant_questions(id) ON DELETE CASCADE,
  -- この答えなら条件に当てはまる（選択肢の value）
  allowed_values  text[] NOT NULL,
  PRIMARY KEY (program_id, question_id),
  CONSTRAINT ck_program_conditions_values CHECK (cardinality(allowed_values) >= 1)
);
CREATE INDEX idx_program_conditions_question ON program_conditions (question_id);
COMMENT ON TABLE program_conditions IS '制度の対象者の条件（ADR-0014）。すべての条件に当てはまれば対象、1つでも外れれば対象外、答えていなければ要確認';
