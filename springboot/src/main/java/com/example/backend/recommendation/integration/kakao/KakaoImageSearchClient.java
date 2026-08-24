package com.example.backend.recommendation.integration.kakao;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 카카오(다음) 이미지 검색 API로 매장 대표 이미지를 찾는다. 공식 API이지 매장 사진을
 * 보장해주는 API가 아니라 웹 전체를 키워드로 검색하는 일반 이미지 검색이라, 결과가 그
 * 매장과 무관할 수 있다 - 그래서 호출부(PublicRestaurantImageService)에서 결과를 한 번
 * DB에 캐싱해두고 재검색하지 않는다.
 */
@Component
public class KakaoImageSearchClient {

    private static final String IMAGE_SEARCH_URL = "https://dapi.kakao.com/v2/search/image";

    private final RestClient restClient;
    private final String restApiKey;

    public KakaoImageSearchClient(
            RestClient.Builder restClientBuilder,
            @Value("${kakao.rest-api-key:}") String restApiKey
    ) {
        this.restClient = restClientBuilder.build();
        this.restApiKey = restApiKey;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(restApiKey);
    }

    /** 검색어로 이미지를 하나 찾아 URL을 반환한다. 결과가 없거나 호출에 실패하면 빈 Optional. */
    public Optional<String> searchFirstImageUrl(String query) {
        if (!isConfigured() || !StringUtils.hasText(query)) {
            return Optional.empty();
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(IMAGE_SEARCH_URL)
                    .queryParam("query", query)
                    .queryParam("size", 1)
                    .queryParam("sort", "accuracy")
                    .build()
                    .encode()
                    .toUri();
            Map<String, Object> body = restClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> documents = extractDocuments(body);
            if (documents.isEmpty()) {
                return Optional.empty();
            }
            Object imageUrl = documents.get(0).get("image_url");
            return imageUrl == null ? Optional.empty() : Optional.of(imageUrl.toString());
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDocuments(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }
        Object documents = body.get("documents");
        return documents instanceof List ? (List<Map<String, Object>>) documents : List.of();
    }
}
