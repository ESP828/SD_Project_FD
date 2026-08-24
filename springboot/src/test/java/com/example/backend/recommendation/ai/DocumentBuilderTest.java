package com.example.backend.recommendation.ai;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentBuilderTest {

    @Test
    void buildsCanonicalDocumentVersionOne() {
        PublicRestaurant restaurant = new PublicRestaurant("external-1", "강남 파스타");
        restaurant.update(
                "강남 파스타",
                null,
                "I2",
                "음식",
                null,
                "I20401",
                "서양식 면 요리",
                "서울특별시",
                "강남구",
                "서울특별시 강남구 테헤란로 1",
                null,
                new BigDecimal("37.5000000"),
                new BigDecimal("127.0000000"),
                "202608"
        );

        String document = new DocumentBuilder().build(restaurant);

        assertThat(document).isEqualTo(
                "강남 파스타 음식 양식 서양식 면 요리 서울특별시 강남구 테헤란로 1"
        );
        assertThat(DocumentBuilder.DOCUMENT_VERSION).isEqualTo(1);
    }
}
