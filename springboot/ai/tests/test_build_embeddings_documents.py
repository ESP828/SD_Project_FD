import sys
import unittest
from pathlib import Path

import pandas as pd


AI_DIR = Path(__file__).resolve().parents[1]
if str(AI_DIR) not in sys.path:
    sys.path.insert(0, str(AI_DIR))

from build_embeddings import canonical_document, canonical_document_v2  # noqa: E402


def _row() -> pd.Series:
    return pd.Series({
        "name": "여의도 식당",
        "category_large_name": "음식",
        "category_medium_name": "한식",
        "category_small_name": "한식 일반 음식점업",
        "road_address": "서울특별시 영등포구 여의대로 1",
        "parking_available": 1,
        "wifi_available": 1,
        "playroom_available": 0,
        "multilingual_menu_available": 1,
        "delivery_available": 0,
        "smart_order_available": None,
        "representative_menu": "비빔밥",
        "hashtags": "안심식당,애견동반",
        "opening_hours": "11:00~21:00",
        "closed_days": "매주 일요일",
        "reservation_info": "전화 예약",
        "area_info": "",
        "average_rating": 4.5,
        "review_count": 3,
    })


class DocumentVersionTest(unittest.TestCase):

    def test_document_v1_remains_byte_compatible(self):
        self.assertEqual(
            "여의도 식당 음식 한식 한식 일반 음식점업 서울특별시 영등포구 여의대로 1",
            canonical_document(_row(), 1),
        )

    def test_document_v2_appends_only_verified_fields_in_stable_order(self):
        self.assertEqual(
            "여의도 식당 음식 한식 한식 일반 음식점업 서울특별시 영등포구 여의대로 1 "
            "주차 가능 와이파이 제공 다국어 메뉴판 제공 대표메뉴 비빔밥 "
            "해시태그 안심식당,애견동반 영업시간 11:00~21:00 휴무일 매주 일요일 "
            "예약정보 전화 예약 FOODUCK 리뷰 평점 4.50 리뷰 3개",
            canonical_document_v2(_row()),
        )

    def test_document_v2_appends_official_menu_price_and_quality_fields(self):
        row = _row()
        row["verified_menu_names"] = "비빔밥, 된장찌개"
        row["priced_menu_count"] = 7
        row["minimum_menu_price"] = 8000
        row["typical_menu_price"] = 12000
        row["vegan_labeled_menu_available"] = 1
        row["vegetarian_labeled_menu_available"] = 1
        row["gluten_free_labeled_menu_available"] = 0
        row["award_description"] = "모범음식점(2023)"
        row["rti_score"] = 4.21
        row["acceptance_score"] = 0.37
        row["popularity_score"] = 0.33
        row["naver_rating"] = 4.4

        document = canonical_document_v2(row)

        self.assertIn(
            "검증메뉴 비빔밥, 된장찌개 공식 메뉴 대표가격 12000원 "
            "최저가격 8000원 가격표본 7개 비건 표기 메뉴 있음 채식 표기 메뉴 있음",
            document,
        )
        self.assertIn(
            "공식 어워드 모범음식점(2023) 공식 RTI 지수 4.21 "
            "공식 수용태세 지수 0.37 공식 인기도 0.33 공식 외부평점 네이버 4.40",
            document,
        )


if __name__ == "__main__":
    unittest.main()
