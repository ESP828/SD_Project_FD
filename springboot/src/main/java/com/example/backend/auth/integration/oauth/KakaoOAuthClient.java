package com.example.backend.auth.integration.oauth;

import com.example.backend.auth.domain.type.SocialProvider;
import com.example.backend.auth.exception.OAuthProviderException;
import com.example.backend.auth.integration.oauth.dto.KakaoTokenResponse;
import com.example.backend.auth.integration.oauth.dto.KakaoUserInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class KakaoOAuthClient implements OAuthProviderClient {

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final String clientId;
    private final String redirectUri;
    private final String clientSecret;

    public KakaoOAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${kakao.rest-api-key:}") String clientId,
            @Value("${kakao.redirect-uri:}") String redirectUri,
            @Value("${kakao.client-secret:}") String clientSecret
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.clientSecret = clientSecret;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public URI buildAuthorizationUri(String state) {
        requireConfigured(clientId, redirectUri);
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();
    }

    @Override
    public OAuthUserProfile fetchUserProfile(String authorizationCode, String state) {
        requireConfigured(clientId, redirectUri);
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", clientId);
            form.add("redirect_uri", redirectUri);
            form.add("code", authorizationCode);
            if (StringUtils.hasText(clientSecret)) {
                form.add("client_secret", clientSecret);
            }
            KakaoTokenResponse token = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (token == null || !StringUtils.hasText(token.accessToken())) {
                throw new OAuthProviderException();
            }
            KakaoUserInfoResponse user = restClient.get()
                    .uri(USER_INFO_URL)
                    .headers(headers -> headers.setBearerAuth(token.accessToken()))
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);
            if (user == null || user.id() == null) {
                throw new OAuthProviderException();
            }
            return new OAuthUserProfile(
                    provider(),
                    String.valueOf(user.id()),
                    user.email(),
                    user.nickname()
            );
        } catch (RestClientException exception) {
            throw new OAuthProviderException();
        }
    }

    private void requireConfigured(String... values) {
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                throw new OAuthProviderException();
            }
        }
    }
}
