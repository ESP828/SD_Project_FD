package com.example.backend.auth.domain.type;

import java.util.Locale;

public enum SocialProvider {
    KAKAO,
    NAVER,
    GOOGLE;

    public static SocialProvider fromPath(String value) {
        if (value == null) {
            throw new IllegalArgumentException("소셜 로그인 제공자가 필요합니다.");
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 소셜 로그인 제공자입니다.");
        }
    }
}
