package com.example.backend.recommendation.preference.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「나를 위한 맛집」 한 번의 요청에 쓰이는 사용자 취향 스냅샷.
 * 찜 중심 긍정 프로필과 별점 기반 긍정·부정 보정 신호를 담은 결과이며,
 * 여기서 나온 신호가 KURE 취향 점수·경험 매장 제외·찜 카테고리 다양화 기준이 된다.
 * 개인화 추천 노출에는 찜이 필수이고 나이/성별은 선택 신호다.
 */
public record PersonalPreferenceProfile(
        List<WeightedRestaurantSignal> positiveSignals,
        List<WeightedRestaurantSignal> negativeSignals,
        Set<Long> experiencedRestaurantIds,
        Set<String> preferredCategories,
        String gender,
        Integer ageGroup,
        int favoriteCount,
        int ratingCount
) {

    public PersonalPreferenceProfile {
        positiveSignals = positiveSignals == null ? List.of() : List.copyOf(positiveSignals);
        negativeSignals = negativeSignals == null ? List.of() : List.copyOf(negativeSignals);
        experiencedRestaurantIds = experiencedRestaurantIds == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(experiencedRestaurantIds));
        preferredCategories = preferredCategories == null
                ? Set.of()
                : Collections.unmodifiableSet(new LinkedHashSet<>(preferredCategories));
    }

    public static PersonalPreferenceProfile empty() {
        return new PersonalPreferenceProfile(
                List.of(), List.of(), Set.of(), Set.of(), null, null, 0, 0
        );
    }

    /** KURE/TF-IDF 취향 점수를 계산할 재료가 있는지. */
    public boolean hasTasteSignal() {
        return !positiveSignals.isEmpty();
    }

    /** 「나를 위한 맛집」을 노출할 최소 조건인 찜이 있는지. */
    public boolean hasFavorites() {
        return favoriteCount > 0;
    }

    /** 찜이든 평점이든 사용자가 직접 한 행동이 있는지. */
    public boolean hasBehaviorSignal() {
        return favoriteCount > 0 || ratingCount > 0;
    }

    /** 나이/성별 중 하나라도 입력되어 있는지. */
    public boolean hasProfileSignal() {
        return gender != null || ageGroup != null;
    }

    /** 응답에 담는 개인화 수준. 찜은 필수이고 나이/성별은 있을 때만 수준을 높인다. */
    public String personalizationLevel() {
        if (!hasFavorites()) {
            return "NO_FAVORITES";
        }
        if (hasProfileSignal()) {
            return "FULL";
        }
        return "BEHAVIOR_ONLY";
    }
}
