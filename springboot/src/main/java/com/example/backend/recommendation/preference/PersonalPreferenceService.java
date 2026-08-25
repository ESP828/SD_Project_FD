package com.example.backend.recommendation.preference;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.recommendation.preference.domain.PersonalPreferenceProfile;
import com.example.backend.recommendation.preference.domain.WeightedRestaurantSignal;
import com.example.backend.recommendation.query.RecommendationQueryRepository;
import com.example.backend.recommendation.query.RecommendationQueryRepository.RestaurantCandidate;
import com.example.backend.review.repository.AccountPublicRestaurantRating;
import com.example.backend.review.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 찜을 중심으로 내가 남긴 평점을 보정 신호로 합친다.
 *
 * <p>리뷰가 많은 테스트 계정에서도 찜 한 건이 묻히지 않도록 긍정 프로필의 75%를 찜,
 * 25%를 4~5점 리뷰에 배정한다. 1~2점은 별도 부정 프로필로 보내 KURE에서 감점한다.
 * 나이/성별은 값만 실어 보내고 실제 통계가 있을 때만 별도 서비스가 사용한다.
 *
 * <p>찜과 평점이 같은 매장에 함께 있으면 부호 있는 값으로 먼저 합친 뒤 그룹을 나눈다.
 * 찜해 두고 실제로 가서 1점을 준 매장을 계속 강한 긍정 취향으로 두면 안 되기 때문이다.
 *
 * <pre>
 * 찜 + 5점 = +1.7   찜 + 4점 = +1.3   찜 + 3점 = +0.7
 * 찜 + 2점 = +0.1   찜 + 1점 = -0.3 (부정 프로필로 이동)
 * </pre>
 */
@Service
@Transactional(readOnly = true)
public class PersonalPreferenceService {

    /** 긍정 취향 프로필에서 찜이 차지하는 총 비율. */
    public static final double FAVORITE_PROFILE_SHARE = 0.75;

    /** 긍정 취향 프로필에서 4~5점 리뷰가 차지할 수 있는 최대 비율. */
    public static final double POSITIVE_RATING_PROFILE_SHARE = 0.25;

    /** 찜 하나의 부호 있는 기본 가중치. 내 평점과 합쳐 매장별 순 선호도를 만든다. */
    public static final double FAVORITE_SIGNAL_WEIGHT = 0.7;

    /** 부호 있는 가중치의 절댓값이 이 값보다 작으면 중립으로 보고 취향 벡터에서 제외한다. */
    private static final double SIGNAL_EPSILON = 1e-6;

    /** KURE 요청 크기를 지키기 위한 신호 상한. */
    private static final int MAX_SIGNALS = 500;

    private final AccountRepository accountRepository;
    private final RecommendationQueryRepository recommendationQueryRepository;
    private final ReviewRepository reviewRepository;

    public PersonalPreferenceService(
            AccountRepository accountRepository,
            RecommendationQueryRepository recommendationQueryRepository,
            ReviewRepository reviewRepository
    ) {
        this.accountRepository = accountRepository;
        this.recommendationQueryRepository = recommendationQueryRepository;
        this.reviewRepository = reviewRepository;
    }

    public PersonalPreferenceProfile build(Long accountId) {
        if (accountId == null) {
            return PersonalPreferenceProfile.empty();
        }

        List<RestaurantCandidate> allFavorites =
                recommendationQueryRepository.findFavoritesByAccountId(accountId);
        if (allFavorites == null) {
            allFavorites = List.of();
        }
        List<Long> publicFavoriteIds = recommendationQueryRepository
                .findPublicFavoriteIdsByAccountId(accountId)
                .stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<AccountPublicRestaurantRating> ratings =
                reviewRepository.findActivePublicRatingsByAccountId(accountId);

        // 1. 매장별로 찜과 내 평점을 부호 있는 하나의 값으로 먼저 합친다.
        //    찜해 둔 매장이라도 직접 가보고 1~2점을 줬다면 더 이상 긍정 취향이 아니다.
        Set<Long> favoriteIds = new LinkedHashSet<>(publicFavoriteIds);
        Set<Long> experiencedIds = new LinkedHashSet<>(publicFavoriteIds);
        Map<Long, Double> ratingWeights = new LinkedHashMap<>();
        for (AccountPublicRestaurantRating rating : ratings) {
            Long restaurantId = rating.publicRestaurantId();
            if (restaurantId == null) {
                continue;
            }
            experiencedIds.add(restaurantId);
            ratingWeights.merge(restaurantId, ratingWeight(rating.ratingValue()), Double::sum);
        }

        // 2. 부호에 따라 세 그룹으로 나눈다. 찜한 매장은 평점을 합친 결과가 양수일 때만
        //    찜 그룹에 남고, 음수가 되면 부정 프로필로 넘어간다(찜 + 1점 = -0.3).
        //    같은 매장이 긍정과 부정에 동시에 들어가면 두 프로필이 서로를 상쇄한다.
        Map<Long, Double> favoriteWeights = new LinkedHashMap<>();
        Map<Long, Double> positiveRatingWeights = new LinkedHashMap<>();
        Map<Long, Double> negativeWeights = new LinkedHashMap<>();
        for (Long favoriteId : favoriteIds) {
            double net = FAVORITE_SIGNAL_WEIGHT
                    + ratingWeights.getOrDefault(favoriteId, 0.0);
            if (net > SIGNAL_EPSILON) {
                favoriteWeights.put(favoriteId, net);
            } else if (net < -SIGNAL_EPSILON) {
                negativeWeights.put(favoriteId, -net);
            }
        }
        ratingWeights.forEach((restaurantId, weight) -> {
            if (favoriteIds.contains(restaurantId)) {
                // 찜한 매장의 평점은 위에서 이미 합쳤다. 여기서 또 더하면 이중계산이 된다.
                return;
            }
            if (weight > SIGNAL_EPSILON) {
                positiveRatingWeights.put(restaurantId, weight);
            } else if (weight < -SIGNAL_EPSILON) {
                negativeWeights.put(restaurantId, -weight);
            }
        });

        Map<Long, Double> positiveWeights = new LinkedHashMap<>();
        mergeNormalizedGroup(positiveWeights, favoriteWeights, FAVORITE_PROFILE_SHARE);
        mergeNormalizedGroup(
                positiveWeights, positiveRatingWeights, POSITIVE_RATING_PROFILE_SHARE
        );
        List<WeightedRestaurantSignal> positiveSignals = toSignals(positiveWeights);
        List<WeightedRestaurantSignal> negativeSignals = toSignals(negativeWeights);

        // 3. 요약과 다양화는 찜 카테고리만 사용한다. 리뷰 카테고리는 취향 벡터에만 반영한다.
        Set<String> preferredCategories = new LinkedHashSet<>();
        allFavorites.stream()
                .map(RestaurantCandidate::categoryName)
                .filter(value -> value != null && !value.isBlank())
                .forEach(preferredCategories::add);

        // 4. 나이/성별은 값만 담는다. 점수화는 DemographicPreferenceService가 맡는다.
        String gender = null;
        Integer ageGroup = null;
        Optional<Account> account = accountRepository.findById(accountId);
        if (account.isPresent()) {
            Account profile = account.get();
            if (profile.getGender() != null) {
                gender = normalizeGender(profile.getGender().name());
            }
            if (profile.getBirthDate() != null) {
                ageGroup = normalizeAgeGroup(
                        Period.between(profile.getBirthDate(), LocalDate.now()).getYears()
                );
            }
        }

        return new PersonalPreferenceProfile(
                limit(positiveSignals),
                limit(negativeSignals),
                experiencedIds,
                preferredCategories,
                gender,
                ageGroup,
                allFavorites.size(),
                ratings.size()
        );
    }

    /**
     * 인수인계서의 행동 가중치 표.
     * 3점은 중립이라 취향 벡터에 들어가지 않지만, 경험한 매장으로는 계속 남는다.
     */
    public static double ratingWeight(int rating) {
        return switch (rating) {
            case 5 -> 1.0;
            case 4 -> 0.6;
            case 2 -> -0.6;
            case 1 -> -1.0;
            default -> 0.0;
        };
    }

    public static String preferenceSummary(PersonalPreferenceProfile profile) {
        if (!profile.hasFavorites()) {
            return "아직 찜한 맛집이 없어요. 맛집을 찜하면 나만의 추천이 시작됩니다.";
        }
        List<String> categories = profile.preferredCategories().stream()
                .filter(value -> value != null && !value.isBlank())
                .limit(3)
                .toList();
        if (!categories.isEmpty()) {
            return "회원님의 " + String.join(", ", categories) + " 취향 기반 맞춤 추천";
        }
        return "회원님의 행동 취향을 반영한 맞춤 추천";
    }

    public static String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        String normalized = gender.trim().toUpperCase();
        if (normalized.startsWith("F")
                || normalized.contains("FEMALE")
                || normalized.contains("WOMAN")) {
            return "FEMALE";
        }
        if (normalized.startsWith("M")
                || normalized.contains("MALE")
                || normalized.contains("MAN")) {
            return "MALE";
        }
        return null;
    }

    public static Integer normalizeAgeGroup(Integer age) {
        if (age == null || age < 10 || age > 120) {
            return null;
        }
        return Math.min((age / 10) * 10, 60);
    }

    private static void mergeNormalizedGroup(
            Map<Long, Double> target,
            Map<Long, Double> source,
            double groupShare
    ) {
        double total = source.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= SIGNAL_EPSILON || groupShare <= 0.0) {
            return;
        }
        source.forEach((restaurantId, weight) ->
                target.merge(restaurantId, groupShare * weight / total, Double::sum));
    }

    private static List<WeightedRestaurantSignal> toSignals(Map<Long, Double> weights) {
        List<WeightedRestaurantSignal> signals = new ArrayList<>();
        weights.forEach((restaurantId, weight) -> {
            if (restaurantId != null && weight > SIGNAL_EPSILON) {
                signals.add(new WeightedRestaurantSignal(restaurantId, weight));
            }
        });
        return limit(signals);
    }

    /** 가중치가 큰 신호부터 남긴다. 잘라내야 한다면 약한 신호를 버리는 편이 손해가 적다. */
    private static List<WeightedRestaurantSignal> limit(List<WeightedRestaurantSignal> signals) {
        if (signals.size() <= MAX_SIGNALS) {
            return List.copyOf(signals);
        }
        return signals.stream()
                .sorted((left, right) -> Double.compare(right.weight(), left.weight()))
                .limit(MAX_SIGNALS)
                .toList();
    }
}
