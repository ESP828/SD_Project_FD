package com.example.backend.auth.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.dto.response.AuthTokenResponse;
import com.example.backend.global.security.jwt.JwtProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TokenService {

    private final AuthorityService authorityService;
    private final JwtProvider jwtProvider;

    public TokenService(AuthorityService authorityService, JwtProvider jwtProvider) {
        this.authorityService = authorityService;
        this.jwtProvider = jwtProvider;
    }

    public AuthTokenResponse issueAccessToken(Account account) {
        List<String> authorities = authorityService.findCodes(account.getAccountId());
        String token = jwtProvider.createAccessToken(
                account.getAccountId(),
                account.getLoginId(),
                authorities
        );
        return AuthTokenResponse.bearer(token, jwtProvider.getAccessTokenValidity());
    }
}
