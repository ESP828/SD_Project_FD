package com.example.backend.auth.domain.type;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum AuthorityCode {
    ROLE_USER((short) 0, "일반 사용자"),
    ROLE_BUSINESS((short) 1, "사업자"),
    ROLE_ADMIN((short) 2, "관리자");

    private final short authorityId;
    private final String displayName;

    AuthorityCode(short authorityId, String displayName) {
        this.authorityId = authorityId;
        this.displayName = displayName;
    }

    public short authorityId() {
        return authorityId;
    }

    public String displayName() {
        return displayName;
    }

    public List<AuthorityCode> includedAuthorities() {
        return Arrays.stream(values())
                .filter(code -> code.authorityId <= authorityId)
                .toList();
    }

    public static Optional<AuthorityCode> fromCode(String authorityCode) {
        return Arrays.stream(values())
                .filter(code -> code.name().equals(authorityCode))
                .findFirst();
    }
}
