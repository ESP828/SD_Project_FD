package com.example.backend.recommendation.ai;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

/**
 * Builds the canonical recommendation document shared by the KURE and TF-IDF indexes.
 */
@Component
public class DocumentBuilder {

    public static final int DOCUMENT_VERSION = 1;

    public String build(PublicRestaurant restaurant) {
        return Stream.of(
                        restaurant.getName(),
                        restaurant.getCategoryLargeName(),
                        restaurant.getCategoryMediumName(),
                        restaurant.getCategorySmallName(),
                        restaurant.getRoadAddress()
                )
                .map(DocumentBuilder::normalize)
                .filter(value -> !value.isEmpty())
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}
