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

    def test_personal_profile_accepts_weighted_positive_and_negative_signals(self):
        request = app.PersonalProfileScoreRequest(
            positiveSignals=[{"restaurantId": 10, "weight": 1.7}],
            negativeSignals=[{"restaurantId": 20, "weight": 0.3}],
            candidateRestaurantIds=[30, 40],
        )

        self.assertEqual(1.7, request.positiveSignals[0].weight)
        self.assertEqual(0.3, request.negativeSignals[0].weight)

    def test_personal_profile_requires_a_positive_signal(self):
        with self.assertRaises(ValidationError):
            app.PersonalProfileScoreRequest(
                positiveSignals=[],
                candidateRestaurantIds=[30],
            )

    def test_personal_profile_rejects_non_positive_weights(self):
        with self.assertRaises(ValidationError):
            app.PersonalProfileScoreRequest(
                positiveSignals=[{"restaurantId": 10, "weight": 0.0}],
                candidateRestaurantIds=[30],
            )

    def test_personal_profile_scores_a_whole_radius_of_candidates(self):
        """개인화는 반경 안 매장을 전부 채점한다. 검색용 10,000건 상한을 쓰면
        밀집 지역에서 매 요청이 422로 거절되어 조용히 TF-IDF로 떨어진다."""
        self.assertGreater(
            app.MAX_PROFILE_CANDIDATE_RESTAURANTS, app.MAX_CANDIDATE_RESTAURANTS
        )

        request = app.PersonalProfileScoreRequest(
            positiveSignals=[{"restaurantId": 10, "weight": 1.0}],
            candidateRestaurantIds=list(range(app.MAX_PROFILE_CANDIDATE_RESTAURANTS)),
        )

        self.assertEqual(
            app.MAX_PROFILE_CANDIDATE_RESTAURANTS, len(request.candidateRestaurantIds)
        )

        with self.assertRaises(ValidationError):
            app.PersonalProfileScoreRequest(
                positiveSignals=[{"restaurantId": 10, "weight": 1.0}],
                candidateRestaurantIds=list(
                    range(app.MAX_PROFILE_CANDIDATE_RESTAURANTS + 1)
                ),
            )


if __name__ == "__main__":
    unittest.main()
