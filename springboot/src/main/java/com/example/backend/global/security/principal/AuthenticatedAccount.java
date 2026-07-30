package com.example.backend.global.security.principal;

import java.security.Principal;
import java.util.List;

public record AuthenticatedAccount(
        Long accountId,
        String loginId,
        List<String> authorities
) implements Principal {

    @Override
    public String getName() {
        return loginId != null ? loginId : String.valueOf(accountId);
    }
}
