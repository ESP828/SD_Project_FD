package com.example.backend.auth.integration.oauth;

import com.example.backend.auth.domain.type.SocialProvider;
import com.example.backend.auth.exception.OAuthProviderException;
import com.example.backend.auth.integration.oauth.dto.NaverTokenResponse;
import com.example.backend.auth.integration.oauth.dto.NaverUserInfoResponse;
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
public class NaverOAuthClient implements OAuthProviderClient {

    private static final String AUTHORIZE_URL = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public NaverOAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${naver.client-id:}") String clientId,
            @Value("${naver.client-secret:}") String clientSecret,
            @Value("${naver.redirect-uri:}") String redirectUri
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.NAVER;
    }

    @Override
    public URI buildAuthorizationUri(String state) {
        requireConfigured();
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
        requireConfigured();
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("redirect_uri", redirectUri);
            form.add("code", authorizationCode);
            form.add("state", state);
            NaverTokenResponse token = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(NaverTokenResponse.class);
            if (token == null || !StringUtils.hasText(token.accessToken())) {
                throw new OAuthProviderException();
            }
            NaverUserInfoResponse envelope = restClient.get()
                    .uri(USER_INFO_URL)
                    .headers(headers -> headers.setBearerAuth(token.accessToken()))
                    .retrieve()
                    .body(NaverUserInfoResponse.class);
            if (envelope == null
                    || envelope.response() == null
                    || !StringUtils.hasText(envelope.response().id())) {
                throw new OAuthProviderException();
            }
            NaverUserInfoResponse.Profile user = envelope.response();
            String nickname = StringUtils.hasText(user.nickname()) ? user.nickname() : user.name();
            return new OAuthUserProfile(provider(), user.id(), user.email(), nickname);
        } catch (RestClientException exception) {
            throw new OAuthProviderException();
        }
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(clientId)
                || !StringUtils.hasText(clientSecret)
                || !StringUtils.hasText(redirectUri)) {
            throw new OAuthProviderException();
        }
    }
}
