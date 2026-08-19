package com.example.backend.review.integration.sentiment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FastAPI 감성분석 서비스(POST /predict/sentiment)의 응답.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SentimentPredictionResponse(
        String review,
        String prediction,
        @JsonProperty("positive_probability") double positiveProbability,
        @JsonProperty("negative_probability") double negativeProbability
) {
    public boolean isPositive() {
        return "positive".equals(prediction);
    }
}
