package com.example.backend.business.integration.nts;

import com.example.backend.business.integration.nts.dto.NtsValidateRequest;
import com.example.backend.business.integration.nts.dto.NtsValidateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * 국세청 사업자등록정보 진위확인 API(공공데이터포털) 호출을 담당한다.
 * 사업자등록번호·대표자명·개업일자가 국세청 등록 정보와 일치하는지 확인한다.
 */
@Component
public class NtsBusinessVerificationClient {

    private static final String BASE_URL = "https://api.odcloud.kr/api/nts-businessman/v1";

    private final RestClient restClient;
    private final String serviceKey;

    public NtsBusinessVerificationClient(
            RestClient.Builder restClientBuilder,
            @Value("${public-data.business-registration.service-key:}") String serviceKey
    ) {
        this.restClient = restClientBuilder.build();
        this.serviceKey = serviceKey;
    }

    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    /**
     * @param businessNumber    숫자 10자리 (하이픈 제거)
     * @param openedAt          yyyyMMdd 형식
     * @param representativeName 대표자명
     * @return 국세청 등록 정보와 일치하면 true
     */
    public boolean verify(String businessNumber, String openedAt, String representativeName) {
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL + "/validate")
                .queryParam("serviceKey", serviceKey)
                .encode()
                .build()
                .toUri();

        NtsValidateRequest request = new NtsValidateRequest(
                List.of(NtsValidateRequest.Business.of(businessNumber, openedAt, representativeName))
        );

        NtsValidateResponse response = restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(NtsValidateResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            return false;
        }
        return response.data().get(0).isMatched();
    }
}
