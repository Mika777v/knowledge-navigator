package com.example.knavi.assistant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.knavi.assistant.domain.Eligibility.Condition;
import com.example.knavi.assistant.domain.Eligibility.Judgement;
import com.example.knavi.assistant.domain.Eligibility.Status;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 対象・対象外・要確認の判定（ADR-0014 の決定 1）。雇用形態 × 勤続のすべての組み合わせを確かめる */
class EligibilityTests {

    static final Condition REGULAR_OR_CONTRACT = new Condition("EMPLOYMENT_TYPE", Set.of("REGULAR", "CONTRACT"));
    static final Condition TENURE = new Condition("TENURE_6M", Set.of("YES"));

    @Test
    void programWithoutConditionsIsEligible() {
        assertThat(Eligibility.judge(List.of(), Map.of()).status()).isEqualTo(Status.ELIGIBLE);
    }

    @Test
    void judgesEveryCombinationOfEmploymentTypeAndTenure() {
        List<Condition> conditions = List.of(REGULAR_OR_CONTRACT, TENURE);
        record Case(String employment, String tenure, Status expected) {}
        List<Case> cases = List.of(
                new Case("REGULAR", "YES", Status.ELIGIBLE),
                new Case("CONTRACT", "YES", Status.ELIGIBLE),
                new Case("PART_TIME", "YES", Status.NOT_ELIGIBLE),
                new Case("REGULAR", "NO", Status.NOT_ELIGIBLE),
                new Case("PART_TIME", "NO", Status.NOT_ELIGIBLE),
                new Case("REGULAR", null, Status.UNKNOWN),
                new Case(null, "YES", Status.UNKNOWN),
                new Case(null, null, Status.UNKNOWN),
                // 外れた条件があれば、答えていない条件があっても対象外
                new Case("PART_TIME", null, Status.NOT_ELIGIBLE),
                new Case(null, "NO", Status.NOT_ELIGIBLE));
        for (Case c : cases) {
            Map<String, String> answers = new java.util.HashMap<>();
            if (c.employment() != null) {
                answers.put("EMPLOYMENT_TYPE", c.employment());
            }
            if (c.tenure() != null) {
                answers.put("TENURE_6M", c.tenure());
            }
            assertThat(Eligibility.judge(conditions, answers).status())
                    .as("%s / %s", c.employment(), c.tenure())
                    .isEqualTo(c.expected());
        }
    }

    @Test
    void reportsWhichConditionsWereUnmetOrUnanswered() {
        Judgement judgement =
                Eligibility.judge(List.of(REGULAR_OR_CONTRACT, TENURE), Map.of("EMPLOYMENT_TYPE", "PART_TIME"));

        assertThat(judgement.unmet()).containsExactly("EMPLOYMENT_TYPE");
        assertThat(judgement.unanswered()).containsExactly("TENURE_6M");
    }
}
