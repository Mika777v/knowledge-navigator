"""metrics.py の単体テスト（python3 -m unittest discover scripts/eval）"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from metrics import evaluate_question, regressions, summarize, summary_table  # noqa: E402


def response(programs=(), documents=(), faqs=(), contacts=()):
    def block(codes):
        return [{"code": c} for c in codes]

    return {
        "programs": block(programs),
        "documents": block(documents),
        "faqs": block(faqs),
        "contacts": block(contacts),
    }


class EvaluateQuestionTest(unittest.TestCase):
    def test_best_rank_across_blocks(self):
        q = {"id": "Q1", "q": "結婚", "expect": {"programs": ["P-B"], "faqs": ["F-A"], "contacts": ["C-1"]}}
        r = evaluate_question(q, response(programs=["P-X", "P-Y", "P-B"], faqs=["F-Z", "F-A"], contacts=["C-1"]))
        self.assertFalse(r.hit1)
        self.assertTrue(r.hit5)
        self.assertEqual(r.reciprocal_rank, 0.5)
        self.assertTrue(r.contact_hit)

    def test_miss_beyond_top5(self):
        q = {"id": "Q1", "q": "x", "expect": {"programs": ["P-6"]}}
        r = evaluate_question(q, response(programs=["P-1", "P-2", "P-3", "P-4", "P-5", "P-6"], contacts=["C"]))
        self.assertFalse(r.hit5)
        self.assertEqual(r.reciprocal_rank, 1 / 6)

    def test_contact_only_question_counts_as_ranked_hit(self):
        q = {"id": "Q50", "q": "社員食堂", "expect": {"contacts": ["HR-REP"]}, "fallback": True, "tags": ["no-answer"]}
        r = evaluate_question(q, response(contacts=["HR-REP"]))
        self.assertTrue(r.hit1 and r.hit5)
        self.assertTrue(r.no_answer)
        self.assertTrue(r.zero_hit)


class SummarizeTest(unittest.TestCase):
    def test_zero_hit_rate_excludes_no_answer_questions(self):
        answered = evaluate_question({"id": "A", "q": "a", "expect": {"programs": ["P"]}}, response(programs=["P"]))
        empty = evaluate_question({"id": "B", "q": "b", "expect": {"programs": ["P"]}}, response())
        no_answer = evaluate_question(
            {"id": "C", "q": "c", "expect": {"contacts": ["HR-REP"]}, "tags": ["no-answer"]}, response(contacts=["HR-REP"])
        )
        s = summarize([answered, empty, no_answer])
        self.assertEqual(s["zero_hit_rate"], 0.5)
        self.assertEqual(s["contact_accuracy"], 1.0)
        self.assertEqual(s["hit5"], round(2 / 3, 4))

    def test_metric_without_target_questions_is_none(self):
        r = evaluate_question({"id": "A", "q": "a", "expect": {"documents": ["D"]}}, response(documents=["D"]))
        self.assertIsNone(summarize([r])["contact_accuracy"])
        self.assertEqual(regressions(summarize([r]), {"contact_accuracy": 1.0}), [])

    def test_regressions(self):
        self.assertEqual(regressions({"hit5": 0.9, "zero_hit_rate": 0.1}, {"hit5": 0.9, "zero_hit_rate": 0.1}), [])
        worse = regressions({"hit5": 0.8, "zero_hit_rate": 0.2}, {"hit5": 0.9, "zero_hit_rate": 0.1})
        self.assertEqual(len(worse), 2)



class SummaryTableTest(unittest.TestCase):
    def test_marks_changes_against_the_baseline(self):
        current = {"hit1": 0.94, "hit5": 1.0, "mrr": 0.95, "contact_accuracy": 1.0, "zero_hit_rate": 0.02}
        baseline = {"hit1": 0.92, "hit5": 1.0, "mrr": 0.9567, "contact_accuracy": 1.0, "zero_hit_rate": 0.0}
        rows = {line.split(" | ")[0].lstrip("| "): line for line in summary_table(current, baseline, 0.7)[2:]}
        self.assertIn("94.0% | 92.0% | ▲ 良くなった", rows["Hit@1"])
        self.assertIn("（目標 70.0% 以上） | 100.0% |  |", rows["Hit@5"])
        self.assertIn("0.950 | 0.957 | ▼ 悪くなった", rows["MRR"])
        # ゼロヒット率は低いほど良い
        self.assertIn("2.0% | 0.0% | ▼ 悪くなった", rows["ゼロヒット率"])

    def test_without_baseline(self):
        rows = summary_table({"hit1": 0.5, "hit5": 0.8, "mrr": 0.6, "contact_accuracy": 1.0, "zero_hit_rate": 0.0}, {}, 0.7)
        self.assertTrue(all("| — |" in row for row in rows[2:]))

if __name__ == "__main__":
    unittest.main()
