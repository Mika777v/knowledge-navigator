package com.example.knavi.assistant.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 制度が対象かどうかの判定（ADR-0014 の決定 1）。LLM を使わず、条件と答えだけから決める。
 *
 * <ul>
 *   <li>すべての条件で、答えが当てはまる選択肢に入っていれば {@link Status#ELIGIBLE 対象}
 *   <li>1つでも外れていれば {@link Status#NOT_ELIGIBLE 対象外}（答えていない条件があっても）
 *   <li>外れた条件が無く、答えていない条件があれば {@link Status#UNKNOWN 要確認}
 *   <li>条件の無い制度は対象
 * </ul>
 */
public final class Eligibility {

    private Eligibility() {}

    public enum Status {
        ELIGIBLE,
        NOT_ELIGIBLE,
        UNKNOWN
    }

    /** @param allowedValues この答えなら当てはまる（選択肢の value） */
    public record Condition(String questionCode, Set<String> allowedValues) {}

    /**
     * @param unmet 外れた条件の質問のコード
     * @param unanswered 答えていない条件の質問のコード
     */
    public record Judgement(Status status, List<String> unmet, List<String> unanswered) {}

    /** @param answers 質問のコード → 選んだ value */
    public static Judgement judge(List<Condition> conditions, Map<String, String> answers) {
        List<String> unmet = new ArrayList<>();
        List<String> unanswered = new ArrayList<>();
        for (Condition condition : conditions) {
            String answer = answers.get(condition.questionCode());
            if (answer == null) {
                unanswered.add(condition.questionCode());
            } else if (!condition.allowedValues().contains(answer)) {
                unmet.add(condition.questionCode());
            }
        }
        Status status =
                !unmet.isEmpty() ? Status.NOT_ELIGIBLE : unanswered.isEmpty() ? Status.ELIGIBLE : Status.UNKNOWN;
        return new Judgement(status, List.copyOf(unmet), List.copyOf(unanswered));
    }
}
