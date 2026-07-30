package com.example.backend.auth.integration.oauth;

import com.example.backend.auth.domain.type.SocialProvider;
import com.example.backend.auth.exception.OAuthProviderException;
import com.example.backend.auth.integration.oauth.dto.GoogleTokenResponse;
import com.example.backend.auth.integration.oauth.dto.GoogleUserInfoResponse;
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
public class GoogleOAuthClient implements OAuthProviderClient {

    private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleOAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${google.client-id:}") String clientId,
            @Value("${google.client-secret:}") String clientSecret,
            @Value("${google.redirect-uri:}") String redirectUri
    ) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public URI buildAuthorizationUri(String state) {
        requireConfigured();
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", "email profile")
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
            GoogleTokenResponse token = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
            if (token == null || !StringUtils.hasText(token.accessToken())) {
                throw new OAuthProviderException();
            }
            GoogleUserInfoResponse user = restClient.get()
                    .uri(USER_INFO_URL)
                    .headers(headers -> headers.setBearerAuth(token.accessToken()))
                    .retrieve()
                    .body(GoogleUserInfoResponse.class);
            if (user == null || !StringUtils.hasText(user.sub())) {
                throw new OAuthProviderException();
            }
            return new OAuthUserProfile(provider(), user.sub(), user.email(), user.name());
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
