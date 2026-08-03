package com.example.backend.restaurant.integration.publicdata;

import com.example.backend.restaurant.integration.publicdata.dto.PublicDataStoreListResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 공공데이터포털 storeListInUpjong 실제 응답 샘플(sample-public-data-store-list.json,
 * 2026-07-31 실제 API 호출로 캡처)이 DTO로 정확히 파싱되는지 검증한다.
 */
class PublicDataStoreListResponseParsingTest {

    @Test
    void parsesRealSampleResponse() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream in = getClass().getResourceAsStream("/sample-public-data-store-list.json")) {
            assertNotNull(in, "테스트 픽스처 파일을 찾을 수 없습니다.");
            PublicDataStoreListResponse response = mapper.readValue(in, PublicDataStoreListResponse.class);

            assertTrue(response.header().isSuccess());
            assertFalse(response.header().isNoData());
            assertEquals(1000, response.body().numOfRows());
            assertEquals(1, response.body().pageNo());
            assertEquals(827828, response.body().totalCount());
            assertEquals(1000, response.body().itemsOrEmpty().size());

            PublicDataStoreListResponse.Item first = response.body().itemsOrEmpty().get(0);
            assertEquals("MA010120220800015932", first.bizesId());
            assertEquals("프랜즈", first.bizesNm());
            assertEquals("I2", first.indsLclsCd());
            assertEquals("음식", first.indsLclsNm());
            assertNotNull(first.rdnmAdr());
            assertNotNull(first.lat());
            assertNotNull(first.lon());
        }
    }
}
