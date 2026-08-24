import sys
import unittest
from pathlib import Path


AI_DIR = Path(__file__).resolve().parents[1]
if str(AI_DIR) not in sys.path:
    sys.path.insert(0, str(AI_DIR))

from import_seoul_tourism_evidence import MenuAggregate, identity_matches  # noqa: E402


class SeoulTourismEvidenceImportTest(unittest.TestCase):

    def test_menu_summary_excludes_implausible_prices_and_uses_median_low(self):
        aggregate = MenuAggregate()
        for menu_id, name, price in (
            ("1", "데이터 오류", "1"),
            ("2", "비빔밥", "8000"),
            ("3", "된장찌개", "12000"),
            ("4", "한정식", "25000"),
            ("5", "오류 고가", "150000000"),
        ):
            aggregate.add({"메뉴(ID)": menu_id, "메뉴명": name, "메뉴가격": price})

        self.assertEqual((8000, 12000, 25000), aggregate.price_summary())
        self.assertEqual(3, len(aggregate.priced_entries))
        self.assertEqual(2, aggregate.implausible_price_count)

    def test_menu_labels_are_literal_and_source_identity_must_match(self):
        aggregate = MenuAggregate()
        aggregate.add({"메뉴(ID)": "1", "메뉴명": "비건 채식 볼", "메뉴가격": "14000"})

        self.assertTrue(aggregate.vegan_labeled)
        self.assertTrue(aggregate.vegetarian_labeled)
        self.assertFalse(aggregate.gluten_free_labeled)
        self.assertTrue(identity_matches(
            {"식당명": "명동정", "지점명": "본점"},
            {"식당명": "명동정", "지점명": "본점"},
        ))
        self.assertFalse(identity_matches(
            {"식당명": "명동정", "지점명": "강남점"},
            {"식당명": "명동정", "지점명": "본점"},
        ))


if __name__ == "__main__":
    unittest.main()
