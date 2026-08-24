package com.example.backend.recommendation.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationQueryParserTest {

    private final RecommendationQueryParser parser =
            new RecommendationQueryParser(new RecommendationTextRules());

    @Test
    void separatesLocationCategoryAndSemanticText() {
        ParsedRecommendationQuery parsed = parser.parse("신논현역 분위기 좋은 카페 추천해줘");

        assertThat(parsed.locationText()).isEqualTo("신논현역");
        assertThat(parsed.category()).isEqualTo("카페·디저트");
        assertThat(parsed.categoryMedium()).isEqualTo("카페·디저트");
        assertThat(parsed.semanticText()).isEqualTo("분위기 좋은");
        assertThat(parsed.atmosphereTokens()).containsExactly("분위기");
    }

    @Test
    void parsesNumericConstraintsWithoutSendingThemToSemanticEngine() {
        ParsedRecommendationQuery parsed =
                parser.parse("강남역에서 2만원 이하 평점 4.0 이상 한식 추천");

        assertThat(parsed.locationText()).isEqualTo("강남역");
        assertThat(parsed.categoryMedium()).isEqualTo("한식");
        assertThat(parsed.maxPrice()).isEqualTo(20_000);
        assertThat(parsed.minRating()).isEqualTo(4.0);
        assertThat(parsed.semanticText()).isEmpty();
    }

    @Test
    void keepsLowConfidenceLocationForGeocoding() {
        ParsedRecommendationQuery parsed = parser.parse("신논현 주변 라멘");

        assertThat(parsed.locationText()).isEqualTo("신논현역");
        assertThat(parsed.nearby()).isTrue();
        assertThat(parsed.categoryMedium()).isEqualTo("일식");
        assertThat(parsed.categorySmallKeyword()).isEqualTo("면 요리");
    }

    @Test
    void parsesCategoryExclusionAndTextRadius() {
        ParsedRecommendationQuery parsed = parser.parse(
                "강남역 근처 카페 말고 500m 이내 조용한 한식집 추천해줘"
        );

        assertThat(parsed.locationText()).isEqualTo("강남역");
        assertThat(parsed.categoryMedium()).isEqualTo("한식");
        assertThat(parsed.excludedCategoryMediumNames()).containsExactly("카페·디저트");
        assertThat(parsed.radiusMeters()).isEqualTo(500);
        assertThat(parsed.unsupportedConstraints()).contains("ATMOSPHERE_DATA_UNAVAILABLE");
    }

    @Test
    void normalizesColloquialLocationAndCategoryAliases() {
        ParsedRecommendationQuery parsed = parser.parse("건대에서 친구랑 고기먹을데 알려줘");

        assertThat(parsed.locationText()).isEqualTo("건대입구역");
        assertThat(parsed.categoryMedium()).isEqualTo("한식");
        assertThat(parsed.purposeTokens()).contains("모임");
    }

    @Test
    void parsesMixedKoreanPriceAndWalkingDistance() {
        ParsedRecommendationQuery parsed = parser.parse(
                "종로3가역 주변 2만5천원 이하 걸어서 10분 술집 알려줘"
        );

        assertThat(parsed.maxPrice()).isEqualTo(25_000);
        assertThat(parsed.radiusMeters()).isEqualTo(800);
        assertThat(parsed.categoryMedium()).isEqualTo("주점");
    }

    @Test
    void parsesTenThousandWonPriceBandAsUpperBound() {
        ParsedRecommendationQuery parsed = parser.parse(
                "건대입구역 근처 만원대로 먹을 수 있는 고깃집 찾아줘"
        );

        assertThat(parsed.maxPrice()).isEqualTo(19_999);
        assertThat(parsed.semanticText()).doesNotContain("만원대");
    }

    @Test
    void convertsPartyTotalBudgetToPerPersonPrice() {
        ParsedRecommendationQuery parsed = parser.parse(
                "여의도역 주변 4명 합쳐서 10만원 이하 회식 장소 추천해줘"
        );

        assertThat(parsed.maxPrice()).isEqualTo(25_000);
    }
}
