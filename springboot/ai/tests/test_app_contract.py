import unittest

from pydantic import ValidationError

import app


class AppRequestContractTest(unittest.TestCase):
    def test_embedding_search_accepts_evidence_candidate_limit(self):
        request = app.EmbeddingSearchRequest(
            query="채식 식당",
            restaurantIds=list(range(5_000)),
            topK=5_000,
        )

        self.assertEqual(5_000, len(request.restaurantIds))
        self.assertEqual(5_000, request.topK)

    def test_embedding_search_rejects_candidates_above_safety_limit(self):
        with self.assertRaises(ValidationError):
            app.EmbeddingSearchRequest(
                query="채식 식당",
                restaurantIds=list(range(app.MAX_CANDIDATE_RESTAURANTS + 1)),
                topK=app.MAX_CANDIDATE_RESTAURANTS,
            )

    def test_favorite_scoring_uses_same_candidate_safety_limit(self):
        request = app.FavoriteScoreRequest(
            favoriteRestaurantIds=[1],
            candidateRestaurantIds=list(range(app.MAX_CANDIDATE_RESTAURANTS)),
        )

        self.assertEqual(app.MAX_CANDIDATE_RESTAURANTS, len(request.candidateRestaurantIds))


if __name__ == "__main__":
    unittest.main()
