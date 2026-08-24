import json
import sys
import unittest
from pathlib import Path


AI_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(AI_DIR))

import evaluate_recommendations


class EvaluationDatasetTest(unittest.TestCase):
    def test_dataset_has_one_hundred_unique_cases(self):
        dataset = json.loads(
            evaluate_recommendations.DEFAULT_DATASET.read_text(encoding="utf-8")
        )
        cases = dataset["cases"]
        ids = [case["id"] for case in cases]

        self.assertEqual(100, len(cases))
        self.assertEqual(100, len(set(ids)))
        self.assertEqual("Q001", ids[0])
        self.assertEqual("Q100", ids[-1])

    def test_percentile_interpolates_deterministically(self):
        values = [100.0, 200.0, 300.0, 400.0]

        self.assertEqual(250.0, evaluate_recommendations._percentile(values, 0.5))
        self.assertEqual(385.0, evaluate_recommendations._percentile(values, 0.95))


if __name__ == "__main__":
    unittest.main()
