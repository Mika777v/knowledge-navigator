"""tune_weights.py の単体テスト（python3 -m unittest discover scripts/eval）"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from tune_weights import combinations, parse_grid, rank_key  # noqa: E402


class GridTest(unittest.TestCase):
    def test_parse_grid(self):
        self.assertEqual(
            parse_grid(["program-name=2.0,2.5", "synonym=0.8"]), {"program-name": [2.0, 2.5], "synonym": [0.8]}
        )
        with self.assertRaises(SystemExit):
            parse_grid(["program-name"])

    def test_combinations_cover_every_pair(self):
        combos = combinations({"a": [1.0, 2.0], "b": [3.0, 4.0]})
        self.assertEqual(len(combos), 4)
        self.assertIn({"a": 2.0, "b": 3.0}, combos)

    def test_rank_prefers_hit5_then_mrr_then_hit1(self):
        base = {"hit5": 0.9, "mrr": 0.8, "hit1": 0.7, "zero_hit_rate": 0.0}
        better_mrr = {**base, "mrr": 0.85, "hit1": 0.6}
        better_hit5 = {**base, "hit5": 0.95, "mrr": 0.5}
        ranked = sorted([base, better_mrr, better_hit5], key=rank_key, reverse=True)
        self.assertEqual(ranked, [better_hit5, better_mrr, base])


if __name__ == "__main__":
    unittest.main()
