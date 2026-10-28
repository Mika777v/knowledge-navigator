"""rag_metrics.py の単体テスト（python3 -m unittest discover scripts/eval）"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from rag_metrics import evaluate_rag_question, parse_sse, summarize_rag  # noqa: E402

TITLES = {"REG-GA-001": "慶弔見舞金規程"}
QUESTION = {
    "id": "R001",
    "q": "結婚祝金はいくら？",
    "expect": {"sections": [{"document": "REG-GA-001", "article": "第5条"}], "facts": ["30,000円"]},
    "tags": ["amount"],
}
STREAM = (
    'event:results\ndata:{"programs":[],"fallback":false}\n\n'
    'event:answer\ndata:{"answer":"結婚祝金は３０，０００円です。[1]",'
    '"evidence":[{"ref":1,"documentTitle":"慶弔見舞金規程","articleNo":"第5条"}],"model":"m"}\n\n'
    "event:done\ndata:{}\n\n"
)


class ParseSseTest(unittest.TestCase):
    def test_events_in_order(self):
        events = parse_sse(STREAM)
        self.assertEqual([name for name, _ in events], ["results", "answer", "done"])
        self.assertEqual(events[1][1]["model"], "m")

    def test_multi_line_data_and_crlf(self):
        events = parse_sse('event:x\r\ndata:{"a":\r\ndata: 1}\r\n\r\n')
        self.assertEqual(events, [("x", {"a": 1})])


class EvaluateRagQuestionTest(unittest.TestCase):
    def test_correct_citation_and_facts_with_notation_differences(self):
        result = evaluate_rag_question(QUESTION, parse_sse(STREAM), 12.0, TITLES)
        self.assertTrue(result.answered)
        self.assertTrue(result.citation_ok)
        self.assertTrue(result.facts_ok)
        self.assertEqual(result.cited, ["慶弔見舞金規程 第5条"])

    def test_wrong_article_and_missing_fact(self):
        stream = STREAM.replace("第5条", "第6条").replace("３０，０００円", "20,000円")
        result = evaluate_rag_question(QUESTION, parse_sse(stream), 1.0, TITLES)
        self.assertFalse(result.citation_ok)
        self.assertFalse(result.facts_ok)

    def test_unanswerable(self):
        stream = (
            'event:results\ndata:{}\n\nevent:unanswerable\ndata:{"reason":"NO_EVIDENCE"}\n\nevent:done\ndata:{}\n\n'
        )
        result = evaluate_rag_question(QUESTION, parse_sse(stream), 1.0, TITLES)
        self.assertFalse(result.answered)
        self.assertEqual(result.reason, "NO_EVIDENCE")
        self.assertFalse(result.citation_ok)


class SummarizeRagTest(unittest.TestCase):
    def test_rates_count_only_the_relevant_questions(self):
        ok = evaluate_rag_question(QUESTION, parse_sse(STREAM), 10.0, TITLES)
        wrong = evaluate_rag_question(QUESTION, parse_sse(STREAM.replace("第5条", "第6条")), 20.0, TITLES)
        unanswered = evaluate_rag_question(
            QUESTION, parse_sse('event:unanswerable\ndata:{"reason":"GENERATION_FAILED"}\n\n'), 30.0, TITLES
        )
        no_answer_q = {"id": "R039", "q": "社員食堂", "answerable": False, "tags": ["no-answer"]}
        abstained = evaluate_rag_question(
            no_answer_q, parse_sse('event:unanswerable\ndata:{"reason":"NO_EVIDENCE"}\n\n'), 2.0, TITLES
        )

        summary = summarize_rag([ok, wrong, unanswered, abstained])

        self.assertEqual(summary["citation_accuracy"], 0.5)  # 回答できた2問のうち1問
        self.assertAlmostEqual(summary["answer_rate"], 2 / 3)
        self.assertEqual(summary["abstain_rate"], 1.0)
        self.assertEqual(summary["seconds_median"], 15.0)
        self.assertEqual(summary["seconds_max"], 30.0)
        self.assertEqual(summary["reasons"], {"GENERATION_FAILED": 1, "NO_EVIDENCE": 1})

    def test_no_answered_questions_gives_no_rate(self):
        self.assertIsNone(summarize_rag([])["citation_accuracy"])


if __name__ == "__main__":
    unittest.main()
