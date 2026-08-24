package com.example.backend.recommendation.service;

import com.example.backend.recommendation.integration.kakao.KakaoImageSearchClient;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공공데이터 매장의 대표 이미지를 카카오 이미지 검색으로 찾아 DB에 캐싱한다.
 * 추천/맛집랭킹 화면에 노출되는(그리고 실제 리뷰가 있는) 매장에 한해서만 호출한다 -
 * 138,561개 매장 전체를 미리 긁는 건 API 호출량 낭비라, 처음 노출될 때 1회만 검색하고
 * 그 뒤로는 DB에 저장된 값을 그대로 쓴다.
 */
@Service
public class PublicRestaurantImageService {

    private static final Logger log = LoggerFactory.getLogger(PublicRestaurantImageService.class);

    private final PublicRestaurantRepository publicRestaurantRepository;
    private final KakaoImageSearchClient kakaoImageSearchClient;

    public PublicRestaurantImageService(
            PublicRestaurantRepository publicRestaurantRepository,
            KakaoImageSearchClient kakaoImageSearchClient
    ) {
        this.publicRestaurantRepository = publicRestaurantRepository;
        this.kakaoImageSearchClient = kakaoImageSearchClient;
    }

    /**
     * 캐싱된 이미지 URL을 반환한다. 아직 한 번도 검색해본 적 없는 매장이면 카카오 이미지
     * 검색을 호출해서 결과를 DB에 저장한 뒤 반환한다. 빈 문자열이면 "검색했는데 결과 없음"
     * 이라 프론트에는 null과 동일하게 취급하도록 넘긴다.
     */
    @Transactional
    public String getOrFetchImageUrl(Long publicRestaurantId, String restaurantName) {
        if (!kakaoImageSearchClient.isConfigured()) {
            return null;
        }
        PublicRestaurant restaurant = publicRestaurantRepository.findById(publicRestaurantId).orElse(null);
        if (restaurant == null) {
            return null;
        }
        if (restaurant.getImageUrl() != null) {
            // 이미 검색해본 매장 - 빈 문자열(검색했지만 결과 없음)이면 프론트에는 null로 보낸다.
            return restaurant.getImageUrl().isBlank() ? null : restaurant.getImageUrl();
        }
        String query = restaurantName;
        String found = kakaoImageSearchClient.searchFirstImageUrl(query).orElse(null);
        try {
            restaurant.cacheImageUrl(found);
            publicRestaurantRepository.save(restaurant);
        } catch (RuntimeException e) {
            log.warn("매장 이미지 캐싱 실패 (publicRestaurantId={}): {}", publicRestaurantId, e.getMessage());
        }
        return found;
    }
}
