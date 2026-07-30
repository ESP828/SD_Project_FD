package com.example.backend.auth.integration.oauth;

import com.example.backend.auth.domain.type.SocialProvider;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Component
public class OAuthStateService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final long validityMillis;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder decoder = Base64.getUrlDecoder();

    public OAuthStateService(
            @Value("${oauth.state-secret}") String secret,
            @Value("${oauth.state-validity:300000}") long validityMillis
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("OAUTH_STATE_SECRET은 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
        if (validityMillis <= 0) {
            throw new IllegalStateException("OAuth state 만료시간은 0보다 커야 합니다.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.validityMillis = validityMillis;
    }

    public String issue(SocialProvider provider) {
        byte[] nonce = new byte[24];
        secureRandom.nextBytes(nonce);
        long expiresAt = Instant.now().toEpochMilli() + validityMillis;
        String payload = provider.name() + "|" + expiresAt + "|" + encoder.encodeToString(nonce);
        String encodedPayload = encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + encoder.encodeToString(sign(encodedPayload));
    }

    public void validate(SocialProvider expectedProvider, String state) {
        try {
            String[] tokenParts = state.split("\\.", -1);
            if (tokenParts.length != 2) {
                throw invalid();
            }
            byte[] actualSignature = decoder.decode(tokenParts[1]);
            byte[] expectedSignature = sign(tokenParts[0]);
            if (!MessageDigest.isEqual(actualSignature, expectedSignature)) {
                throw invalid();
            }
            String payload = new String(decoder.decode(tokenParts[0]), StandardCharsets.UTF_8);
            String[] payloadParts = payload.split("\\|", -1);
            if (payloadParts.length != 3
                    || !expectedProvider.name().equals(payloadParts[0])
                    || Long.parseLong(payloadParts[1]) < Instant.now().toEpochMilli()) {
                throw invalid();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    public void validateForBrowser(
            SocialProvider expectedProvider,
            String state,
            String browserState
    ) {
        if (state == null || browserState == null
                || !MessageDigest.isEqual(
                        state.getBytes(StandardCharsets.UTF_8),
                        browserState.getBytes(StandardCharsets.UTF_8)
                )) {
            throw invalid();
        }
        validate(expectedProvider, state);
    }

    public Duration validity() {
        return Duration.ofMillis(validityMillis);
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("OAuth state 서명을 생성하지 못했습니다.", exception);
        }
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_OAUTH_STATE);
    }
}
