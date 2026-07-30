package com.example.backend.auth.integration.oauth;

import com.example.backend.auth.domain.type.SocialProvider;

public record OAuthUserProfile(
        SocialProvider provider,
        String providerUserId,
        String email,
        String nickname
) {
}
