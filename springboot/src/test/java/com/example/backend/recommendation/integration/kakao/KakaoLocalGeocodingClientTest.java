package com.example.backend.recommendation.integration.kakao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoLocalGeocodingClientTest {

    @Test
    void qualifiesAmbiguousBukchonAsTheSeoulLandmark() {
        assertThat(KakaoLocalGeocodingClient.qualifySearchQuery("북촌"))
                .isEqualTo("서울 북촌한옥마을");
    }

    @Test
    void leavesUnambiguousLocationsUntouched() {
        assertThat(KakaoLocalGeocodingClient.qualifySearchQuery("강남역"))
                .isEqualTo("강남역");
    }
}
