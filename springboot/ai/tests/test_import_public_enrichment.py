import sys
import unittest
from pathlib import Path


AI_DIR = Path(__file__).resolve().parents[1]
if str(AI_DIR) not in sys.path:
    sys.path.insert(0, str(AI_DIR))

from import_public_enrichment import (  # noqa: E402
    RestaurantReference,
    deduplicate_matches,
    match_records,
    normalize_identity,
    parse_yes_no,
)


def _row(record_id: str, name: str, branch: str = "", menu: str = "") -> dict[str, str]:
    return {
        "식당(ID)": record_id,
        "식당명": name,
        "지점명": branch,
        "지역명": "영등포구",
        "주차가능여부": "Y",
        "와이파이제공여부": "N",
        "놀이방유무": "N",
        "다국어메뉴판제공여부": "N",
        "화장실정보내용": "",
        "휴무일정보내용": "",
        "영업시간내용": "",
        "배달서비스유무": "N",
        "온라인예약정보내용": "",
        "홈페이지(URL)": "",
        "인근랜드마크명": "",
        "인근랜드마크위도": "",
        "인근랜드마크경도": "",
        "인근랜드마크와거리": "",
        "스마트오더유무": "N",
        "대표메뉴명": menu,
        "식당상태": "NORMAL",
        "해시태그": "",
        "면적정보내용": "",
    }


class PublicEnrichmentImportTest(unittest.TestCase):

    def test_normalizes_spacing_punctuation_and_compatibility_characters(self):
        self.assertEqual("63뷔페파빌리온", normalize_identity(" 63뷔페-파빌리온 "))
        self.assertEqual("abc카페", normalize_identity("ＡＢＣ 카페"))

    def test_parses_only_known_yes_no_values(self):
        self.assertIs(parse_yes_no("Y"), True)
        self.assertIs(parse_yes_no("n"), False)
        self.assertIsNone(parse_yes_no(""))

    def test_matches_only_unique_name_or_exact_name_branch(self):
        restaurants = [
            RestaurantReference(1, "정성식당", None, "영등포구"),
            RestaurantReference(2, "커피집 여의도점", None, "영등포구"),
        ]
        rows = [_row("10", "정성식당"), _row("11", "커피집", "여의도점")]

        matched, reasons = match_records(rows, restaurants)

        self.assertEqual([1, 2], [value.public_restaurant_id for value in matched])
        self.assertEqual(1, reasons["EXACT_UNIQUE_NAME_REGION"])
        self.assertEqual(1, reasons["EXACT_NAME_BRANCH"])

    def test_rejects_ambiguous_unbranched_source_names(self):
        restaurants = [RestaurantReference(1, "정성식당", None, "영등포구")]
        rows = [_row("10", "정성식당"), _row("11", "정성식당")]

        matched, reasons = match_records(rows, restaurants)

        self.assertEqual([], matched)
        self.assertEqual(2, reasons["SOURCE_NAME_AMBIGUOUS"])

    def test_deduplication_keeps_the_most_complete_source_record(self):
        restaurants = [RestaurantReference(1, "커피집 여의도점", None, "영등포구")]
        rows = [
            _row("10", "커피집", "여의도점"),
            _row("11", "커피집", "여의도점", "라테"),
        ]
        matched, _ = match_records(rows, restaurants)

        selected, removed = deduplicate_matches(matched)

        self.assertEqual(1, removed)
        self.assertEqual("11", selected[0].record["식당(ID)"])


if __name__ == "__main__":
    unittest.main()
