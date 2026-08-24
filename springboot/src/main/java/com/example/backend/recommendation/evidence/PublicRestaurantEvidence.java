package com.example.backend.recommendation.evidence;

import java.util.Arrays;
import java.util.List;

public record PublicRestaurantEvidence(
        Long publicRestaurantId,
        List<String> sourceCodes,
        List<String> evidenceSources,
        Boolean parkingAvailable,
        Boolean wifiAvailable,
        Boolean playroomAvailable,
        Boolean multilingualMenuAvailable,
        Boolean deliveryAvailable,
        Boolean smartOrderAvailable,
        String closedDays,
        String openingHours,
        String reservationInfo,
        String representativeMenu,
        String hashtags,
        String areaInfo,
        String verifiedMenuNames,
        int menuCount,
        int pricedMenuCount,
        Integer minimumMenuPrice,
        Integer typicalMenuPrice,
        Integer maximumMenuPrice,
        Boolean veganLabeledMenuAvailable,
        Boolean vegetarianLabeledMenuAvailable,
        Boolean glutenFreeLabeledMenuAvailable,
        String awardDescription,
        Double rtiScore,
        Double acceptanceScore,
        Double popularityScore,
        Double naverRating,
        Double tripadvisorRating,
        Double ctripRating,
        Double averageRating,
        long reviewCount
) {
    public PublicRestaurantEvidence(
            Long publicRestaurantId,
            List<String> sourceCodes,
            List<String> evidenceSources,
            Boolean parkingAvailable,
            Boolean wifiAvailable,
            Boolean playroomAvailable,
            Boolean multilingualMenuAvailable,
            Boolean deliveryAvailable,
            Boolean smartOrderAvailable,
            String closedDays,
            String openingHours,
            String reservationInfo,
            String representativeMenu,
            String hashtags,
            String areaInfo,
            Double averageRating,
            long reviewCount
    ) {
        this(
                publicRestaurantId, sourceCodes, evidenceSources,
                parkingAvailable, wifiAvailable, playroomAvailable,
                multilingualMenuAvailable, deliveryAvailable, smartOrderAvailable,
                closedDays, openingHours, reservationInfo, representativeMenu, hashtags, areaInfo,
                null, 0, 0, null, null, null,
                null, null, null,
                null, null, null, null, null, null, null,
                averageRating, reviewCount
        );
    }

    public boolean hasHashtag(String expected) {
        if (hashtags == null || hashtags.isBlank()) {
            return false;
        }
        return Arrays.stream(hashtags.split("[,|]"))
                .map(String::trim)
                .anyMatch(value -> value.equalsIgnoreCase(expected));
    }

    public Double officialRating() {
        if (naverRating != null) {
            return naverRating;
        }
        if (tripadvisorRating != null) {
            return tripadvisorRating;
        }
        return ctripRating;
    }

    public String officialRatingProvider() {
        if (naverRating != null) {
            return "네이버";
        }
        if (tripadvisorRating != null) {
            return "트립어드바이저";
        }
        return ctripRating == null ? null : "씨트립";
    }
}
