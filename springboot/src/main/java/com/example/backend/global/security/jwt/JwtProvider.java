package com.example.backend.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Component
public class JwtProvider {

    private final String secret;
    private final long accessTokenValidity;
    private SecretKey key;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity}") long accessTokenValidity
    ) {
        this.secret = secret;
        this.accessTokenValidity = accessTokenValidity;
    }

    @PostConstruct
    void initializeKey() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET은 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
        if (accessTokenValidity <= 0) {
            throw new IllegalStateException("JWT access token 만료시간은 0보다 커야 합니다.");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(
            Long accountId,
            String loginId,
            List<String> authorities
    ) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenValidity);
        var builder = Jwts.builder()
                .subject(String.valueOf(accountId))
                .claim("authorities", List.copyOf(authorities))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key);
        if (loginId != null) {
            builder.claim("loginId", loginId);
        }
        return builder.compact();
    }

    public Long getAccountId(String token) {
        return Long.valueOf(parse(token).getSubject());
    }

    public String getLoginId(String token) {
        return parse(token).get("loginId", String.class);
    }

    public List<String> getAuthorities(String token) {
        Object raw = parse(token).get("authorities");
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<String> authorities = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof String authority) {
                authorities.add(authority);
            }
        }
        return List.copyOf(authorities);
    }

    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public long getAccessTokenValidity() {
        return accessTokenValidity;
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
