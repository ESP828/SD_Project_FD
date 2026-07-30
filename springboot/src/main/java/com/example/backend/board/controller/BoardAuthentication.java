package com.example.backend.board.controller;

import com.example.backend.global.security.principal.AuthenticatedAccount;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

final class BoardAuthentication {

    private BoardAuthentication() {
    }

    static Long accountId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (authentication.getPrincipal() instanceof AuthenticatedAccount account) {
            return account.accountId();
        }
        return null;
    }
}
