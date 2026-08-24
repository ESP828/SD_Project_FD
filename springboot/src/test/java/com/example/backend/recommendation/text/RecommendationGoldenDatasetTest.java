package com.example.backend.recommendation.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationGoldenDatasetTest {

    private final RecommendationQueryParser parser =
            new RecommendationQueryParser(new RecommendationTextRules());

    @Test
    void parsesAllGoldenCasesDeterministically() throws Exception {
        Path datasetPath = Path.of("ai", "evaluation", "recommendation_queries_v1.json");
        if (!Files.exists(datasetPath)) {
            datasetPath = Path.of("springboot", "ai", "evaluation", "recommendation_queries_v1.json");
        }
        JsonNode dataset = new ObjectMapper().readTree(
                datasetPath.toFile()
        );
        JsonNode cases = dataset.path("cases");
        List<String> failures = new ArrayList<>();

        assertThat(cases.isArray()).isTrue();
        assertThat(cases.size()).isEqualTo(100);

        for (JsonNode evaluationCase : cases) {
            String id = evaluationCase.path("id").asText();
            ParsedRecommendationQuery parsed = parser.parse(evaluationCase.path("query").asText());
            JsonNode expected = evaluationCase.path("expected");

            if (expected.hasNonNull("location")) {
                String actualLocation = parsed.locationText().isBlank()
                        ? parsed.locationCandidate()
                        : parsed.locationText();
                collectFailure(failures, id, "location", expected.path("location").asText(), actualLocation);
            }
            if (expected.hasNonNull("category")) {
                collectFailure(
                        failures,
                        id,
                        "category",
                        expected.path("category").asText(),
                        parsed.categoryMedium()
                );
            }
            if (expected.has("excludedCategories")) {
                for (JsonNode excluded : expected.path("excludedCategories")) {
                    if (!parsed.excludedCategoryMediumNames().contains(excluded.asText())) {
                        failures.add(id + " excluded category expected=" + excluded.asText()
                                + " actual=" + parsed.excludedCategoryMediumNames());
                    }
                }
            }
            if (expected.hasNonNull("radiusMeters")) {
                collectFailure(
                        failures,
                        id,
                        "radiusMeters",
                        expected.path("radiusMeters").asInt(),
                        parsed.radiusMeters()
                );
            }
            if ("UNSUPPORTED".equals(evaluationCase.path("supportLevel").asText())
                    && parsed.unsupportedConstraints().isEmpty()) {
                failures.add(id + " should expose at least one unsupported constraint");
            }
        }

        assertThat(failures).isEmpty();
    }

    private static void collectFailure(
            List<String> failures,
            String id,
            String field,
            Object expected,
            Object actual
    ) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            failures.add(id + " " + field + " expected=" + expected + " actual=" + actual);
        }
    }
}
