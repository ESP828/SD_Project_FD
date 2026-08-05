package com.example.backend.business.integration.nts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NtsValidateResponse(
        @JsonProperty("status_code") String statusCode,
        @JsonProperty("data") List<Result> data
) {
    /**
     * valid: "01" = 진위 일치, "02" = 불일치
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(
            @JsonProperty("b_no") String businessNumber,
            @JsonProperty("valid") String valid,
            @JsonProperty("valid_msg") String validMessage
    ) {
        public boolean isMatched() {
            return "01".equals(valid);
        }
    }
}
