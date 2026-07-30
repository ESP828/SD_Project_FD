package com.example.backend.auth.integration.oauth;

import com.example.backend.auth.integration.oauth.dto.GoogleTokenResponse;
import com.example.backend.auth.integration.oauth.dto.KakaoTokenResponse;
import com.example.backend.auth.integration.oauth.dto.KakaoUserInfoResponse;
import com.example.backend.auth.integration.oauth.dto.NaverTokenResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OAuthDtoDeserializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void springBoot4JacksonReadsProviderSnakeCaseFields() {
        KakaoTokenResponse kakao = objectMapper.readValue(
                """
                        {"access_token":"kakao-token","token_type":"bearer","unknown":"ignored"}
                        """,
                KakaoTokenResponse.class
        );
        NaverTokenResponse naver = objectMapper.readValue(
                """
                        {"access_token":"naver-token","token_type":"bearer","expires_in":"3600"}
                        """,
                NaverTokenResponse.class
        );
        GoogleTokenResponse google = objectMapper.readValue(
                """
                        {"access_token":"google-token","token_type":"Bearer","expires_in":3600}
                        """,
                GoogleTokenResponse.class
        );
        KakaoUserInfoResponse kakaoUser = objectMapper.readValue(
                """
                        {
                          "id": 12345,
                          "kakao_account": {
                            "email": "user@example.com",
                            "profile": {"nickname": "사용자"}
                          }
                        }
                        """,
                KakaoUserInfoResponse.class
        );

        assertEquals("kakao-token", kakao.accessToken());
        assertEquals("naver-token", naver.accessToken());
        assertEquals("google-token", google.accessToken());
        assertEquals("user@example.com", kakaoUser.email());
        assertEquals("사용자", kakaoUser.nickname());
    }
}
