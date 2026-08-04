package com.example.backend.auth.service;

import com.example.backend.auth.domain.entity.AccountAuthority;
import com.example.backend.auth.domain.entity.Authority;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.repository.AccountAuthorityRepository;
import com.example.backend.auth.repository.AuthorityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
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
        authorityCode.includedAuthorities()
                .forEach(includedAuthority -> grantSingle(accountId, includedAuthority));
    }

    private void grantSingle(Long accountId, AuthorityCode authorityCode) {
        Authority authority = authorityRepository.findByAuthorityCode(authorityCode.name())
                .orElseGet(() -> authorityRepository.save(new Authority(
                        authorityCode.authorityId(),
                        authorityCode.name(),
                        authorityCode.displayName()
                )));
        var id = new com.example.backend.auth.domain.entity.AccountAuthorityId(
                accountId,
                authority.getAuthorityId()
        );
        if (!accountAuthorityRepository.existsById(id)) {
            accountAuthorityRepository.save(new AccountAuthority(accountId, authority.getAuthorityId()));
        }
    }

    @Transactional
    public void setRole(Long accountId, AuthorityCode role) {
        accountAuthorityRepository.deleteAllByIdAccountId(accountId);
        grant(accountId, role);
    }

    @Transactional(readOnly = true)
    public List<String> findCodes(Long accountId) {
        List<Short> authorityIds = accountAuthorityRepository.findAllByIdAccountId(accountId)
                .stream()
                .map(AccountAuthority::getAuthorityId)
                .toList();

        int highestAuthorityId = authorityRepository.findAllById(authorityIds)
                .stream()
                .map(Authority::getAuthorityCode)
                .map(AuthorityCode::fromCode)
                .flatMap(java.util.Optional::stream)
                .mapToInt(AuthorityCode::authorityId)
                .max()
                .orElse(AuthorityCode.ROLE_USER.authorityId());

        return Arrays.stream(AuthorityCode.values())
                .filter(code -> code.authorityId() <= highestAuthorityId)
                .map(AuthorityCode::name)
                .toList();
    }
}
