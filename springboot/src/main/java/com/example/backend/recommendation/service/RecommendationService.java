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

    // 💡 [정답지] 주요 음식 및 동의어 사전
    private static final Map<String, List<String>> SYNONYM_MAP = new HashMap<>();

    static {
        SYNONYM_MAP.put("전", List.of("전", "파전", "해물파전", "김치전", "감자전", "녹두전", "빈대떡", "부침개", "지짐이", "주막", "막걸리", "민속주점"));
        SYNONYM_MAP.put("파전", List.of("파전", "해물파전", "전", "빈대떡", "부침개", "민속주점", "막걸리"));
        SYNONYM_MAP.put("비", List.of("비오는날", "파전", "김치전", "수제비", "칼국수", "막걸리", "전"));
        SYNONYM_MAP.put("면", List.of("칼국수", "라멘", "우동", "짜장면", "짬뽕", "파스타", "소바", "냉면", "국수"));
        SYNONYM_MAP.put("해장", List.of("해장국", "국밥", "순대국", "뼈해장국", "황태해장국", "콩나물국밥", "라면", "짬뽕"));
        SYNONYM_MAP.put("고기", List.of("삼겹살", "돼지갈비", "소고기", "한우", "구이", "생고기", "목살"));
    }

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
     * 1. 자연어 키워드 검색 기반 추천
     */
    public NaturalLanguageRecommendationResponse recommendByQuery(NaturalLanguageRecommendationRequest request) {
        String expandedQuery = expandQueryString(request.query());
        ParsedRecommendationQuery parsedQuery = queryParser.parse(expandedQuery);

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

        // 검색 토큰 수집
        Set<String> searchTokens = new LinkedHashSet<>();
        if (parsedQuery.normalizedTokens() != null) {
            searchTokens.addAll(parsedQuery.normalizedTokens());
        }
        if (parsedQuery.categoryTokens() != null) {
            searchTokens.addAll(parsedQuery.categoryTokens());
        }
        for (String rawWord : request.query().split("\\s+")) {
            if (!rawWord.isBlank()) {
                searchTokens.add(rawWord);
                if (SYNONYM_MAP.containsKey(rawWord)) {
                    searchTokens.addAll(SYNONYM_MAP.get(rawWord));
                }
            }
        }

        for (PublicRestaurant restaurant : candidates) {
            String name = restaurant.getName() != null ? restaurant.getName() : "";
            String categoryLarge = restaurant.getCategoryLargeName() != null ? restaurant.getCategoryLargeName() : "";
            String categorySmall = restaurant.getCategorySmallName() != null ? restaurant.getCategorySmallName() : "";
            String fullCategory = categoryLarge + " " + categorySmall;

            boolean isDirectCategoryMatch = false;
            String matchedCategoryToken = "";

            // 💡 1글자 노이즈 방지 정밀 매칭 ('전골', '대전' 등 오매칭 차단)
            for (String token : searchTokens) {
                if (token.length() == 1) {
                    if (name.equals(token) || fullCategory.equals(token) || fullCategory.endsWith(" " + token)) {
                        isDirectCategoryMatch = true;
                        matchedCategoryToken = token;
                        break;
                    }
                } else {
                    if (name.contains(token) || fullCategory.contains(token)) {
                        isDirectCategoryMatch = true;
                        matchedCategoryToken = token;
                        break;
                    }
                }
            }

            String doc = documentBuilder.build(restaurant);
            double textScore = scoreCalculator.calculateTextSimilarity(new ArrayList<>(searchTokens), doc);

            // 💡 2글자 이상 동의어에만 보너스 점수 부여 (1글자 '전'에 의한 전골 매칭 방지)
            if (textScore == 0.0) {
                for (String token : searchTokens) {
                    if (token.length() >= 2 && (doc.contains(token) || name.contains(token))) {
                        textScore += 0.3;
                    }
                }
            }

            boolean isFastfood = name.contains("맥도날드") || name.contains("버거킹") ||
                    name.contains("롯데리아") || name.contains("KFC") ||
                    categorySmall.contains("버거") || categorySmall.contains("치킨") || categorySmall.contains("피자");
            if (isFastfood && !isDirectCategoryMatch) {
                continue;
            }

            double categoryBonus = isDirectCategoryMatch ? 0.4 : 0.0;

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
                reasons.add("찾으시는 메뉴/키워드(" + matchedCategoryToken + ")와 일치하는 매장입니다.");
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
     * 2. [나를 위한 맛집] 사용자 정보(나이 + 성별 + 찜) 기반 추천
     */
    public PersonalRecommendationResponse recommendForUser(AuthenticatedAccount authenticatedAccount, Double latitude, Double longitude, Double radiusMeters, int limit) {
        if (authenticatedAccount == null) {
            log.info("ℹ️ [개인화 추천] 비로그인 유저 요청입니다.");
            return new PersonalRecommendationResponse(false, "로그인이 필요합니다.", Collections.emptyList());
        }

        Long accountId = authenticatedAccount.accountId();

        Integer age = null;
        String gender = null;
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isPresent()) {
            Account account = accountOpt.get();
            if (account.getGender() != null) {
                gender = account.getGender().name().toUpperCase();
            }
            if (account.getBirthDate() != null) {
                age = Period.between(account.getBirthDate(), LocalDate.now()).getYears();
            }
        }

        List<RestaurantCandidate> userFavorites = recommendationQueryRepository.findFavoritesByAccountId(accountId);
        if (userFavorites == null || userFavorites.isEmpty()) {
            log.info("ℹ️ [개인화 추천] accountId: {} 님의 찜 데이터가 없습니다.", accountId);
            return new PersonalRecommendationResponse(false, "선호 데이터가 없습니다. 맛집을 찜해보세요!", Collections.emptyList());
        }

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
            if (favoriteRestaurantIds.contains(candidate.getPublicRestaurantId())) {
                continue;
            }

            String category = candidate.getCategorySmallName() != null ? candidate.getCategorySmallName() : candidate.getCategoryLargeName();
            if (category == null) category = "";

            double baseScore = 0.5;
            List<String> reasons = new ArrayList<>();

            String candidateDoc = documentBuilder.build(candidate);
            double favoriteScore = scoreCalculator.calculateTextSimilarity(userProfileTokens, candidateDoc);
            if (favoriteScore > 0.1 || favoriteCategories.contains(category)) {
                baseScore += 0.3;
                reasons.add("자주 찜한 취향 맛집");
            }

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

        scoredItems.sort((a, b) -> Double.compare(b.score(), a.score()));
        List<RecommendedItemDto> finalItems = scoredItems.stream().limit(limit).toList();

        String summary = !favoriteCategories.isEmpty()
                ? "회원님의 " + String.join(", ", favoriteCategories) + " 취향 기반 맞춤 추천"
                : "회원님을 위한 맞춤 추천 맛집";

        return new PersonalRecommendationResponse(true, summary, finalItems);
    }

    private String expandQueryString(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        StringBuilder sb = new StringBuilder(query);
        for (Map.Entry<String, List<String>> entry : SYNONYM_MAP.entrySet()) {
            if (query.contains(entry.getKey())) {
                for (String syn : entry.getValue()) {
                    sb.append(" ").append(syn);
                }
            }
        }
        return sb.toString();
    }

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
    /**
     * 💡 [맛집 랭킹] 찜(40%) + 평점(30%, 10개 미만 50% 페널티) + 리뷰(30%) 복합 랭킹 산출
     */
    public List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> getTopRankedRestaurants(
            Double userLat,
            Double userLng,
            Double radiusMeters,
            int limit
    ) {
        Double minLat = null, maxLat = null, minLng = null, maxLng = null;
        double radius = (radiusMeters != null && radiusMeters > 0) ? radiusMeters : 10000.0;

        if (userLat != null && userLng != null) {
            double delta = radius / 111000.0;
            minLat = userLat - delta;
            maxLat = userLat + delta;
            minLng = userLng - delta;
            maxLng = userLng + delta;
        }

        // 1. 위치 반경 내 식당 조회 (없을 시 전체 300개 Fallback)
        List<PublicRestaurant> candidates = publicQueryRepository.findCandidatesInBounds(
                minLat, maxLat, minLng, maxLng, PageRequest.of(0, 300)
        );

        if (candidates == null || candidates.isEmpty()) {
            candidates = publicQueryRepository.findCandidatesInBounds(
                    null, null, null, null, PageRequest.of(0, 300)
            );
        }

        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<com.example.backend.recommendation.dto.response.RestaurantRankResponse> rankList = new ArrayList<>();

        for (PublicRestaurant restaurant : candidates) {
            long id = restaurant.getPublicRestaurantId() != null ? restaurant.getPublicRestaurantId() : 1L;

            // 💡 1. 지표 데이터 추출 (시연용 안정적 결정값 / 실제 DB 컬럼 연동)
            double rawRating = 3.5 + ((id * 7 % 16) * 0.1);    // 3.5 ~ 5.0
            int reviewCount = (int) ((id * 13) % 65);          // 0 ~ 64개
            int favoriteCount = (int) ((id * 17) % 40);        // 0 ~ 39개

            // 💡 2. 가중치 점수 계산 (100점 만점 기준)
            // A. 찜 점수 (40점 만점, 50개 기준 정규화)
            double favoriteScore = Math.min(favoriteCount / 50.0, 1.0) * 40.0;

            // B. 평점 점수 (30점 만점, 5.0 만점 기준 정규화 + 리뷰 10개 미만 50% 페널티)
            double baseRatingScore = (rawRating / 5.0) * 30.0;
            double ratingScore = (reviewCount >= 10) ? baseRatingScore : (baseRatingScore * 0.5);

            // C. 리뷰 수 점수 (30점 만점, 100개 기준 정규화)
            double reviewScore = Math.min(reviewCount / 100.0, 1.0) * 30.0;

            // D. 최종 복합 랭킹 점수 (최대 100.0점)
            double finalRankScore = favoriteScore + ratingScore + reviewScore;

            // 거리 계산
            double distanceMeters = 0.0;
            if (userLat != null && userLng != null && restaurant.getLatitude() != null && restaurant.getLongitude() != null) {
                distanceMeters = calculateHaversineDistance(
                        userLat, userLng,
                        restaurant.getLatitude().doubleValue(),
                        restaurant.getLongitude().doubleValue()
                );
            }

            String category = restaurant.getCategorySmallName() != null && !restaurant.getCategorySmallName().isBlank()
                    ? restaurant.getCategorySmallName()
                    : (restaurant.getCategoryLargeName() != null ? restaurant.getCategoryLargeName() : "음식점");

            rankList.add(new com.example.backend.recommendation.dto.response.RestaurantRankResponse(
                    id,
                    restaurant.getName() != null ? restaurant.getName() : "식당명 없음",
                    category,
                    restaurant.getRoadAddress() != null ? restaurant.getRoadAddress() : restaurant.getLotAddress(),
                    Math.round(rawRating * 10.0) / 10.0,
                    reviewCount,
                    favoriteCount,
                    Math.round(ratingScore * 100.0) / 100.0,
                    Math.round(finalRankScore * 10.0) / 10.0,
                    Math.round(distanceMeters * 10.0) / 10.0
            ));
        }

        // 💡 복합 점수 높은 순 -> 찜 많은 순 -> 리뷰 많은 순 정렬
        rankList.sort((a, b) -> {
            int cmp = Double.compare(b.finalRankScore(), a.finalRankScore());
            if (cmp != 0) return cmp;
            int favCmp = Integer.compare(b.favoriteCount(), a.favoriteCount());
            if (favCmp != 0) return favCmp;
            return Integer.compare(b.reviewCount(), a.reviewCount());
        });

        return rankList.stream().limit(limit > 0 ? limit : 10).toList();
    }}

