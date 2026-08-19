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
import java.util.Set;

/**
 * 검색어 속 지명(예: "신논현", "성수")을 카카오 로컬 API로 좌표화한다.
 * 1) 주소검색으로 먼저 시도하고, 2) 실패하면 키워드검색을 지하철역/행정동 카테고리로만
 * 한정해 시도한다. "라멘집" 같은 일반 명사가 엉뚱한 상호로 지오코딩되는 것을 막기 위함이다.
 * 실패 시 빈 Optional을 반환하며, 호출부(RecommendationService)는 GPS 좌표로 폴백한다.
 */
@Component
public class KakaoLocalGeocodingClient {

    private static final String ADDRESS_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/address.json";
    private static final String KEYWORD_SEARCH_URL = "https://dapi.kakao.com/v2/local/search/keyword.json";

    // SW8: 지하철역, AD5: 행정동 -> 실제 지명으로 신뢰할 수 있는 카테고리만 채택
    private static final Set<String> TRUSTED_KEYWORD_CATEGORY_CODES = Set.of("SW8", "AD5");

    private final RestClient restClient;
    private final String restApiKey;

    public KakaoLocalGeocodingClient(
            RestClient.Builder restClientBuilder,
            @Value("${kakao.rest-api-key:}") String restApiKey
    ) {
        this.restClient = restClientBuilder.build();
        this.restApiKey = restApiKey;
    }

    public record GeocodedPoint(double latitude, double longitude, String matchedName) {}

    public boolean isConfigured() {
        return StringUtils.hasText(restApiKey);
    }

    public Optional<GeocodedPoint> geocode(String locationText) {
        if (!isConfigured() || !StringUtils.hasText(locationText)) {
            return Optional.empty();
        }
        return searchAddress(locationText).or(() -> searchKeywordTrusted(locationText));
    }

    private Optional<GeocodedPoint> searchAddress(String query) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(ADDRESS_SEARCH_URL)
                    .queryParam("query", query)
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
            Map<String, Object> first = documents.get(0);
            return toPoint(first, (String) first.get("address_name"));
        } catch (RestClientException e) {
            return Optional.empty();
        }
    }

    private Optional<GeocodedPoint> searchKeywordTrusted(String query) {
        try {
            URI uri = UriComponentsBuilder.fromUriString(KEYWORD_SEARCH_URL)
                    .queryParam("query", query)
                    .build()
                    .encode()
                    .toUri();
            Map<String, Object> body = restClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> documents = extractDocuments(body);
            for (Map<String, Object> doc : documents) {
                String categoryGroupCode = (String) doc.get("category_group_code");
                if (categoryGroupCode != null && TRUSTED_KEYWORD_CATEGORY_CODES.contains(categoryGroupCode)) {
                    return toPoint(doc, (String) doc.get("place_name"));
                }
            }
            return Optional.empty();
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

    private Optional<GeocodedPoint> toPoint(Map<String, Object> document, String matchedName) {
        try {
            double longitude = Double.parseDouble(String.valueOf(document.get("x")));
            double latitude = Double.parseDouble(String.valueOf(document.get("y")));
            return Optional.of(new GeocodedPoint(latitude, longitude, matchedName));
        } catch (NumberFormatException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
