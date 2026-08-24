package com.example.backend.recommendation.ai;

import com.example.backend.recommendation.evidence.PublicRestaurantEvidence;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentV2BuilderTest {

    @Test
    void appendsOnlyVerifiedEvidenceToTheVersionOneDocument() {
        PublicRestaurant restaurant = new PublicRestaurant("external-1", "여의도 식당");
        restaurant.update(
                "여의도 식당",
                null,
                "I2",
                "음식",
                null,
                "I20101",
                "한식 일반 음식점업",
                "서울특별시",
                "영등포구",
                "서울특별시 영등포구 여의대로 1",
                null,
                new BigDecimal("37.5000000"),
                new BigDecimal("126.9000000"),
                "202608"
        );
        PublicRestaurantEvidence evidence = new PublicRestaurantEvidence(
                1L,
                List.of("SOURCE"),
                List.of("공공기관 / 공식 데이터"),
                true,
                true,
                false,
                true,
                false,
                null,
                "매주 일요일",
                "11:00~21:00",
                "전화 예약",
                "비빔밥",
                "안심식당,애견동반",
                null,
                4.5,
                3
        );

        String document = new DocumentV2Builder(new DocumentBuilder()).build(restaurant, evidence);

        assertThat(document).isEqualTo(
                "여의도 식당 음식 한식 한식 일반 음식점업 서울특별시 영등포구 여의대로 1 "
                        + "주차 가능 와이파이 제공 다국어 메뉴판 제공 대표메뉴 비빔밥 "
                        + "해시태그 안심식당,애견동반 영업시간 11:00~21:00 휴무일 매주 일요일 "
                        + "예약정보 전화 예약 FOODUCK 리뷰 평점 4.50 리뷰 3개"
        );
        assertThat(DocumentV2Builder.DOCUMENT_VERSION).isEqualTo(2);
    }

    @Test
    void appendsOfficialMenuPriceAndQualityEvidenceInStableOrder() {
        PublicRestaurant restaurant = new PublicRestaurant("external-2", "검증 식당");
        PublicRestaurantEvidence evidence = new PublicRestaurantEvidence(
                2L,
                List.of("OPERATION", "QUALITY", "MENU"),
                List.of("서울관광재단 / 공식 데이터"),
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                "비빔밥, 된장찌개",
                8,
                7,
                8_000,
                12_000,
                25_000,
                true,
                true,
                false,
                "모범음식점(2023)",
                4.21,
                0.37,
                0.33,
                4.4,
                null,
                null,
                null,
                0
        );

        String document = new DocumentV2Builder(new DocumentBuilder()).build(restaurant, evidence);

        assertThat(document).isEqualTo(
                "검증 식당 검증메뉴 비빔밥, 된장찌개 "
                        + "공식 메뉴 대표가격 12000원 최저가격 8000원 가격표본 7개 "
                        + "비건 표기 메뉴 있음 채식 표기 메뉴 있음 "
                        + "공식 어워드 모범음식점(2023) 공식 RTI 지수 4.21 "
                        + "공식 수용태세 지수 0.37 공식 인기도 0.33 공식 외부평점 네이버 4.40"
        );
    }
}
