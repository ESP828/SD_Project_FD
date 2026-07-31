package com.example.backend.restaurant.integration.publicdata;

import com.example.backend.restaurant.integration.publicdata.dto.PublicDataStoreListResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 소상공인시장진흥공단 상가업소정보 공공데이터 API 호출을 담당한다.
 */
@Component
public class PublicDataRestaurantClient {

    private static final String BASE_URL = "https://apis.data.go.kr/B553077/api/open/sdsc2";
    private static final String FOOD_LARGE_CATEGORY_CODE = "I2";

    private final RestClient restClient;
    private final String serviceKey;

    public PublicDataRestaurantClient(
            RestClient.Builder restClientBuilder,
            @Value("${public-data.restaurant.service-key:}") String serviceKey
    ) {
        this.restClient = restClientBuilder.build();
        this.serviceKey = serviceKey;
    }

    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /**
     * 업종 대분류 "음식"(I2)에 해당하는 전국 상가업소를 페이지 단위로 조회한다.
     */
    public PublicDataStoreListResponse fetchFoodStores(int pageNo, int numOfRows) {
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/storeListInUpjong")
                .queryParam("serviceKey", serviceKey)
                .queryParam("type", "json")
                .queryParam("divId", "indsLclsCd")
                .queryParam("key", FOOD_LARGE_CATEGORY_CODE)
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", numOfRows)
                .encode()
                .build()
                .toUri();
        return restClient.get()
                .uri(uri)
                .retrieve()
                .body(PublicDataStoreListResponse.class);
    }
}
