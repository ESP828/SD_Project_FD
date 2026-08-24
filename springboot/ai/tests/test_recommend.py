import unittest

import numpy as np

import recommend


class FakeModel:
    def encode(self, _texts, normalize_embeddings=True):
        self.normalize_embeddings = normalize_embeddings
        return np.asarray([[1.0, 0.0]], dtype=np.float32)


class RecommendTest(unittest.TestCase):
    def setUp(self):
        self.original_state = dict(recommend._state)
        self.original_model = recommend._model
        self.original_embeddings = recommend._embeddings
        self.original_restaurant_ids = recommend._restaurant_ids
        self.original_id_to_index = recommend._id_to_index
        self.original_manifest = recommend._manifest

        recommend._state.update(
            status="READY",
            reason=None,
            message=None,
            modelLoaded=True,
            indexLoaded=True,
        )
        recommend._model = FakeModel()
        recommend._embeddings = np.asarray(
            [
                [1.0, 0.0],
                [0.0, 1.0],
                [2**-0.5, 2**-0.5],
            ],
            dtype=np.float32,
        )
        recommend._restaurant_ids = np.asarray([10, 20, 30], dtype=np.int64)
        recommend._id_to_index = {10: 0, 20: 1, 30: 2}
        recommend._manifest = {
            "modelName": "nlpai-lab/KURE-v1",
            "indexVersion": "test-index",
            "documentVersion": 1,
        }

    def tearDown(self):
        recommend._state.clear()
        recommend._state.update(self.original_state)
        recommend._model = self.original_model
        recommend._embeddings = self.original_embeddings
        recommend._restaurant_ids = self.original_restaurant_ids
        recommend._id_to_index = self.original_id_to_index
        recommend._manifest = self.original_manifest

    def test_search_scores_only_requested_candidates(self):
        result = recommend.search("query", [20, 30], 2)

        self.assertEqual([item["id"] for item in result["items"]], [30, 20])
        self.assertEqual(result["indexVersion"], "test-index")

    def test_search_rejects_candidate_missing_from_index(self):
        with self.assertRaises(recommend.KureServiceError) as context:
            recommend.search("query", [999], 1)

        self.assertEqual(context.exception.code, "KURE_CANDIDATE_SET_MISMATCH")

    def test_favorite_profile_uses_existing_embeddings(self):
        result = recommend.score_favorites([10, 20], [30])

        self.assertEqual(result["items"][0]["id"], 30)
        self.assertAlmostEqual(result["items"][0]["score"], 1.0, places=5)


if __name__ == "__main__":
    unittest.main()
