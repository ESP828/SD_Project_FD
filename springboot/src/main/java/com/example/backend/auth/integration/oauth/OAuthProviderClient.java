package com.example.backend.auth.integration.oauth;

import com.example.backend.auth.domain.type.SocialProvider;

import java.net.URI;

public interface OAuthProviderClient {

    SocialProvider provider();

    URI buildAuthorizationUri(String state);

    OAuthUserProfile fetchUserProfile(String authorizationCode, String state);
}
