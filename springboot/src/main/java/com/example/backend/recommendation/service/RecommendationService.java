package com.example.backend.recommendation.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.recommendation.ai.DocumentBuilder;
import com.example.backend.recommendation.dto.request.NaturalLanguageRecommendationRequest;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse;
import com.example.backend.recommendation.dto.response.NaturalLanguageRecommendationResponse.RecommendedItemDto;
import com.example.backend.recommendation.dto.response.PersonalRecommendationResponse;
import com.example.backend.recommendation.model.RecommendationModelStore;
import com.example.backend.recommendation.query.PublicRecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository.RestaurantCandidate;
import com.example.backend.recommendation.score.RecommendationScoreCalculator;
import com.example.backend.recommendation.text.ParsedRecommendationQuery;
import com.example.backend.recommendation.text.RecommendationQueryParser;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationQueryParser queryParser;
    private final RecommendationModelStore modelStore;
    private final RecommendationScoreCalculator scoreCalculator;
    private final PublicRecommendationQueryRepository publicQueryRepository;
    private final RecommendationQueryRepository recommendationQueryRepository;
    private final DocumentBuilder documentBuilder;
    private final AccountRepository accountRepository;

    public RecommendationService(RecommendationQueryParser queryParser,
                                 RecommendationModelStore modelStore,
                                 RecommendationScoreCalculator scoreCalculator,
                                 PublicRecommendationQueryRepository publicQueryRepository,
                                 RecommendationQueryRepository recommendationQueryRepository,
                                 DocumentBuilder documentBuilder,
                                 AccountRepository accountRepository) {
        this.queryParser = queryParser;
        this.modelStore = modelStore;
        this.scoreCalculator = scoreCalculator;
        this.publicQueryRepository = publicQueryRepository;
        this.recommendationQueryRepository = recommendationQueryRepository;
        this.documentBuilder = documentBuilder;
        this.accountRepository = accountRepository;
    }

    /**
     * 1. 자연어 키워드 검색 기반 추천 (기존 로직 유지)
     */
    public NaturalLanguageRecommendationResponse recommendByQuery(NaturalLanguageRecommendationRequest request) {
        ParsedRecommendationQuery parsedQuery = queryParser.parse(request.query());

        Double centerLat = request.latitude();
        Double centerLng = request.longitude();

        Double minLat = null, maxLat = null, minLng = null, maxLng = null;
        if (centerLat != null && centerLng != null) {
            double delta = request.radiusMeters() / 111000.0;
            minLat = centerLat - delta;
            maxLat = centerLat + delta;
            minLng = centerLng - delta;
            maxLng = centerLng + delta;
        }

        List<PublicRestaurant> candidates = publicQueryRepository.findCandidatesInBounds(
                minLat, maxLat, minLng, maxLng, PageRequest.of(0, 300)
        );

        List<RecommendedItemDto> scoredItems = new ArrayList<>();

        for (PublicRestaurant restaurant : candidates) {
            String name = restaurant.getName() != null ? restaurant.getName() : "";
            String categoryLarge = restaurant.getCategoryLargeName() != null ? restaurant.getCategoryLargeName() : "";
            String categorySmall = restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : "";

            boolean isDirectCategoryMatch = false;
            String matchedCategoryToken = "";
            if (parsedQuery.categoryTokens() != null && !parsedQuery.categoryTokens().isEmpty()) {
                for (String catToken : parsedQuery.categoryTokens()) {
                    if (name.contains(catToken) || categoryLarge.contains(catToken) || categorySmall.contains(catToken)) {
                        isDirectCategoryMatch = true;
                        matchedCategoryToken = catToken;
                        break;
                    }
                }
            }

            String doc = documentBuilder.build(restaurant);
            double textScore = scoreCalculator.calculateTextSimilarity(parsedQuery.normalizedTokens(), doc);

            if (textScore == 0.0 && parsedQuery.normalizedTokens() != null) {
                for (String token : parsedQuery.normalizedTokens()) {
                    if (token.length() >= 2 && doc.contains(token)) {
                        textScore += 0.2;
                    }
                }
            }

            boolean isFastfood = name.contains("맥도날드") || name.contains("버거킹") ||
                                 name.contains("롯데리아") || name.contains("KFC") ||
                                 categorySmall.contains("버거") || categorySmall.contains("치킨") || categorySmall.contains("피자");
            if (isFastfood && !isDirectCategoryMatch) {
                continue;
            }

            double categoryBonus = isDirectCategoryMatch ? 0.3 : 0.0;

            double distanceMeters = 0.0;
            double distanceScore = 1.0;
            if (centerLat != null && centerLng != null && restaurant.getLatitude() != null && restaurant.getLongitude() != null) {
                distanceMeters = calculateHaversineDistance(
                        centerLat, centerLng,
                        restaurant.getLatitude().doubleValue(),
                        restaurant.getLongitude().doubleValue()
                );
                distanceScore = Math.max(0.0, 1.0 - (distanceMeters / request.radiusMeters()));
            }

            double textWeighted = textScore * 0.5;
            double distanceWeighted = distanceScore * 0.2;
            double finalScore = textWeighted + distanceWeighted + categoryBonus;

            List<String> reasons = new ArrayList<>();
            if (isDirectCategoryMatch) {
                reasons.add("찾으시는 음식 종류(" + matchedCategoryToken + ")와 일치하는 매장입니다.");
            } else if (textScore > 0.05) {
                reasons.add("검색하신 키워드와 연관성이 높은 매장입니다.");
            }
            if (distanceScore > 0.5) {
                reasons.add("선택하신 위치와 가까운 매장입니다.");
            }

            scoredItems.add(new RecommendedItemDto(
                    "PUBLIC",
                    restaurant.getPublicRestaurantId(),
                    name,
                    restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : restaurant.getCategoryLargeName(),
                    restaurant.getRoadAddress(),
                    restaurant.getLatitude() != null ? restaurant.getLatitude().doubleValue() : null,
                    restaurant.getLongitude() != null ? restaurant.getLongitude().doubleValue() : null,
                    Math.round(distanceMeters * 10) / 10.0,
                    Math.round(finalScore * 10000) / 10000.0,
                    reasons
            ));
        }

        scoredItems.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<RecommendedItemDto> finalItems = scoredItems.stream().limit(request.limit()).toList();

        return new NaturalLanguageRecommendationResponse(
                request.query(), null, finalItems, "none", "SUCCESS", false
        );
    }

    /**
     * 2. 💡 [나를 위한 맛집] 사용자 정보(나이 + 성별 + 찜) 기반 Spring Boot 자체 추천
     */
    public PersonalRecommendationResponse recommendForUser(AuthenticatedAccount authenticatedAccount, Double latitude, Double longitude, Double radiusMeters, int limit) {
        // A. 비로그인 유저 처리
        if (authenticatedAccount == null) {
            log.info("ℹ️ [개인화 추천] 비로그인 유저 요청입니다.");
            return new PersonalRecommendationResponse(false, "로그인이 필요합니다.", Collections.emptyList());
        }

        Long accountId = authenticatedAccount.accountId();

        // B. 유저 정보 (만 나이 및 성별) 조회
        Integer age = null;
        String gender = null;
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            if (account.getGender() != null) {
                gender = account.getGender().name().toUpperCase(); // MALE / FEMALE
            }
            if (account.getBirthDate() != null) {
                age = Period.between(account.getBirthDate(), LocalDate.now()).getYears();
            }
        }

        // C. 유저 찜 목록 조회 (DB)
        List<RestaurantCandidate> userFavorites = recommendationQueryRepository.findFavoritesByAccountId(accountId);
        if (userFavorites == null || userFavorites.isEmpty()) {
            log.info("ℹ️ [개인화 추천] accountId: {} 님의 찜 데이터가 없습니다.", accountId);
            return new PersonalRecommendationResponse(false, "선호 데이터가 없습니다. 맛집을 찜해보세요!", Collections.emptyList());
        }

        // D. 찜한 매장 ID 및 찜한 카테고리 수집
        Set<Long> favoriteRestaurantIds = new HashSet<>();
        Set<String> favoriteCategories = new LinkedHashSet<>();
        StringBuilder userProfileBuilder = new StringBuilder();

        for (RestaurantCandidate fav : userFavorites) {
            if (fav.restaurantId() != null) {
                favoriteRestaurantIds.add(fav.restaurantId());
            }
            if (fav.restaurantName() != null) {
                userProfileBuilder.append(fav.restaurantName()).append(" ");
            }
            if (fav.categoryName() != null && !fav.categoryName().isBlank()) {
                userProfileBuilder.append(fav.categoryName()).append(" ");
                favoriteCategories.add(fav.categoryName());
            }
        }

        List<String> userProfileTokens = Arrays.asList(userProfileBuilder.toString().trim().split("\\s+"));

        log.info("==================================================================================");
        log.info("👤 [개인 맞춤 자바 연산] accountId: {} | 나이: {} | 성별: {} | 찜 개수: {}개", accountId, age, gender, userFavorites.size());
        log.info("==================================================================================");

        // E. 위치 범위 기반 후보 매장 DB 조회
        Double minLat = null, maxLat = null, minLng = null, maxLng = null;
        double radius = (radiusMeters != null) ? radiusMeters : 2000.0;

        if (latitude != null && longitude != null) {
            double delta = radius / 111000.0;
            minLat = latitude - delta;
            maxLat = latitude + delta;
            minLng = longitude - delta;
            maxLng = longitude + delta;
        }

        List<PublicRestaurant> candidates = publicQueryRepository.findCandidatesInBounds(
                minLat, maxLat, minLng, maxLng, PageRequest.of(0, 300)
        );

        List<RecommendedItemDto> scoredItems = new ArrayList<>();

        for (PublicRestaurant candidate : candidates) {
            // 이미 찜한 매장은 중복 추천 방지를 위해 제외
            if (favoriteRestaurantIds.contains(candidate.getPublicRestaurantId())) {
                continue;
            }

            String category = candidate.getCategorySmallName() != null ? candidate.getCategorySmallName() : candidate.getCategoryLargeName();
            if (category == null) category = "";

            double baseScore = 0.5;
            List<String> reasons = new ArrayList<>();

            // 1) 찜 카테고리 연관 점수 (+0.3)
            String candidateDoc = documentBuilder.build(candidate);
            double favoriteScore = scoreCalculator.calculateTextSimilarity(userProfileTokens, candidateDoc);
            if (favoriteScore > 0.1 || favoriteCategories.contains(category)) {
                baseScore += 0.3;
                reasons.add("자주 찜한 취향 맛집");
            }

            // 2) 연령대별 가중치 (+0.1)
            if (age != null) {
                int ageGroup = (age / 10) * 10;
                if (ageGroup == 20 && (category.contains("카페") || category.contains("디저트") || category.contains("양식") || category.contains("패스트푸드"))) {
                    baseScore += 0.1;
                    reasons.add(ageGroup + "대 인기 스팟");
                } else if ((ageGroup == 30 || ageGroup == 40) && (category.contains("한식") || category.contains("일식") || category.contains("중식"))) {
                    baseScore += 0.1;
                    reasons.add(ageGroup + "대 선호 스팟");
                }
            }

            // 3) 성별 가중치 (+0.05)
            if (gender != null) {
                if (gender.contains("FEMALE") || gender.contains("F")) {
                    if (category.contains("카페") || category.contains("디저트") || category.contains("양식")) {
                        baseScore += 0.05;
                        reasons.add("여성 선호 스팟");
                    }
                } else if (gender.contains("MALE") || gender.contains("M")) {
                    if (category.contains("한식") || category.contains("국밥") || category.contains("고기") || category.contains("주점")) {
                        baseScore += 0.05;
                        reasons.add("남성 선호 스팟");
                    }
                }
            }

            // 4) 거리 점수 (거리가 가까울수록 높음)
            double distanceMeters = 0.0;
            double distanceScore = 1.0;
            if (latitude != null && longitude != null && candidate.getLatitude() != null && candidate.getLongitude() != null) {
                distanceMeters = calculateHaversineDistance(
                        latitude, longitude,
                        candidate.getLatitude().doubleValue(),
                        candidate.getLongitude().doubleValue()
                );
                distanceScore = Math.max(0.0, 1.0 - (distanceMeters / radius));
            }

            // 최종 매칭 점수 (개인화 점수 70% + 거리 점수 30%)
            double finalScore = Math.min((baseScore * 0.7) + (distanceScore * 0.3), 1.0);

            if (reasons.isEmpty()) {
                reasons.add("주변 추천 맛집");
            }

            scoredItems.add(new RecommendedItemDto(
                    "PUBLIC",
                    candidate.getPublicRestaurantId(),
                    candidate.getName(),
                    category,
                    candidate.getRoadAddress(),
                    candidate.getLatitude() != null ? candidate.getLatitude().doubleValue() : null,
                    candidate.getLongitude() != null ? candidate.getLongitude().doubleValue() : null,
                    Math.round(distanceMeters * 10) / 10.0,
                    Math.round(finalScore * 10000) / 10000.0,
                    reasons
            ));
        }

        // 점수 기준 내림차순 정렬 및 개수 제한
        scoredItems.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<RecommendedItemDto> finalItems = scoredItems.stream().limit(limit).toList();

        String summary = !favoriteCategories.isEmpty()
                ? "회원님의 " + String.join(", ", favoriteCategories) + " 취향 기반 맞춤 추천"
                : "회원님을 위한 맞춤 추천 맛집";

        return new PersonalRecommendationResponse(true, summary, finalItems);
    }

    /**
     * 하버사인(Haversine) 거리 계산
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
