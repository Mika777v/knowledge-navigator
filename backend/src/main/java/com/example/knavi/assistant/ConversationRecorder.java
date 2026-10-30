package com.example.knavi.assistant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 会話の履歴の記録（根拠付き回答の SSE から呼ぶ公開 API。ADR-0014 の決定 3） */
public interface ConversationRecorder {

    /** 会話がこの利用者のもので、保持期間を過ぎていないか。続きを話す前に確かめる */
    boolean isOwnedBy(UUID conversationId, UUID userId);

    /**
     * 質問と回答を会話に足す。会話の ID が null なら新しい会話を作る。
     * 記録に失敗しても回答は返し終わっているため、例外は投げずに空を返す。
     *
     * @return 会話の ID
     */
    Optional<UUID> record(UUID userId, UUID conversationId, String question, Reply reply);

    /**
     * 回答。回答文を出せたときは answer と citations、出せなかったときは unanswerableReason を持つ。
     *
     * @param model 回答文を作ったモデル（出せなかったときは null）
     */
    record Reply(String answer, List<Citation> citations, String model, String unanswerableReason) {

        public static Reply answered(String answer, List<Citation> citations, String model) {
            return new Reply(answer, List.copyOf(citations), model, null);
        }

        public static Reply unanswerable(String reason) {
            return new Reply(null, List.of(), null, reason);
        }

        public boolean answerable() {
            return unanswerableReason == null;
        }
    }

    /** @param ref 回答文の [n] の n */
    record Citation(int ref, UUID sectionId) {}
}
