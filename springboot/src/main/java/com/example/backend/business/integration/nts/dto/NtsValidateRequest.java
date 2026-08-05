package com.example.backend.business.integration.nts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record NtsValidateRequest(
        @JsonProperty("businesses") List<Business> businesses
) {
    public record Business(
            @JsonProperty("b_no") String businessNumber,
            @JsonProperty("start_dt") String openedAt,
            @JsonProperty("p_nm") String representativeName,
            @JsonProperty("p_nm2") String representativeName2,
            @JsonProperty("b_nm") String businessName,
            @JsonProperty("corp_no") String corporateNumber,
            @JsonProperty("b_sector") String businessSector,
            @JsonProperty("b_type") String businessType,
            @JsonProperty("b_adr") String businessAddress
    ) {
        public static Business of(String businessNumber, String openedAt, String representativeName) {
            return new Business(businessNumber, openedAt, representativeName, "", "", "", "", "", "");
        }
    }
}
