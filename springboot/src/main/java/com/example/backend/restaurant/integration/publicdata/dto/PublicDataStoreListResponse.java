package com.example.backend.restaurant.integration.publicdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 소상공인시장진흥공단 상가업소정보 API의 "업종별 상가업소 조회"(storeListInUpjong) 응답.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicDataStoreListResponse(Header header, Body body) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Header(String resultCode, String resultMsg, String stdrYm) {
        public boolean isSuccess() {
            return "00".equals(resultCode);
        }

        public boolean isNoData() {
            return "03".equals(resultCode);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Body(List<Item> items, int numOfRows, int pageNo, long totalCount) {
        public List<Item> itemsOrEmpty() {
            return items == null ? List.of() : items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String bizesId,
            String bizesNm,
            String brchNm,
            String indsLclsCd,
            String indsLclsNm,
            String indsMclsCd,
            String indsMclsNm,
            String indsSclsCd,
            String indsSclsNm,
            String ctprvnNm,
            String signguNm,
            String rdnmAdr,
            String lnoAdr,
            Double lon,
            Double lat
    ) {
    }
}
