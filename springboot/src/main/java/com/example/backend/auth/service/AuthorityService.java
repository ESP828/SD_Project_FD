package com.example.backend.auth.service;

import com.example.backend.auth.domain.entity.AccountAuthority;
import com.example.backend.auth.domain.entity.Authority;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.repository.AccountAuthorityRepository;
import com.example.backend.auth.repository.AuthorityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthorityService {

    private final AuthorityRepository authorityRepository;
    private final AccountAuthorityRepository accountAuthorityRepository;

    public AuthorityService(
            AuthorityRepository authorityRepository,
            AccountAuthorityRepository accountAuthorityRepository
    ) {
        this.authorityRepository = authorityRepository;
        this.accountAuthorityRepository = accountAuthorityRepository;
    }

    @Transactional
    public void grant(Long accountId, AuthorityCode authorityCode) {
        Authority authority = authorityRepository.findByAuthorityCode(authorityCode.name())
                .orElseGet(() -> authorityRepository.save(
                        new Authority(authorityCode.name(), displayName(authorityCode))
                ));
        var id = new com.example.backend.auth.domain.entity.AccountAuthorityId(
                accountId,
                authority.getAuthorityId()
        );
        if (!accountAuthorityRepository.existsById(id)) {
            accountAuthorityRepository.save(new AccountAuthority(accountId, authority.getAuthorityId()));
        }
    }

    @Transactional(readOnly = true)
    public List<String> findCodes(Long accountId) {
        List<Short> authorityIds = accountAuthorityRepository.findAllByIdAccountId(accountId)
                .stream()
                .map(AccountAuthority::getAuthorityId)
                .toList();
        return authorityRepository.findAllById(authorityIds)
                .stream()
                .map(Authority::getAuthorityCode)
                .sorted()
                .toList();
    }

    private String displayName(AuthorityCode authorityCode) {
        return switch (authorityCode) {
            case ROLE_USER -> "일반 사용자";
            case ROLE_BUSINESS -> "사업자";
            case ROLE_ADMIN -> "관리자";
        };
    }
}
