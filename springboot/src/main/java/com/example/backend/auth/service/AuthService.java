package com.example.backend.auth.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.entity.AccountCredential;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.dto.request.LoginRequest;
import com.example.backend.auth.dto.request.SignupRequest;
import com.example.backend.auth.dto.response.AuthTokenResponse;
import com.example.backend.auth.repository.AccountCredentialRepository;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final AccountCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorityService authorityService;
    private final TokenService tokenService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
            AccountRepository accountRepository,
            AccountCredentialRepository credentialRepository,
            PasswordEncoder passwordEncoder,
            AuthorityService authorityService,
            TokenService tokenService,
            EmailVerificationService emailVerificationService
    ) {
        this.accountRepository = accountRepository;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorityService = authorityService;
        this.tokenService = tokenService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public void signup(SignupRequest request) {
        String loginId = request.loginId().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String nickname = request.nickname().trim().replaceAll("\\s+", " ");
        validateDuplicates(loginId, email, nickname);
        emailVerificationService.assertVerified(email);
        Account account = accountRepository.save(
                Account.local(loginId, email, nickname)
        );
        credentialRepository.save(new AccountCredential(
                account.getAccountId(),
                passwordEncoder.encode(request.password())
        ));
        authorityService.grant(account.getAccountId(), AuthorityCode.ROLE_USER);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        Account account = accountRepository.findByLoginId(request.loginId().trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        AccountCredential credential = credentialRepository.findById(account.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        account.markLoginSucceeded();
        return tokenService.issueAccessToken(account);
    }

    public boolean isLoginIdAvailable(String loginId) {
        return !accountRepository.existsByLoginId(loginId.trim());
    }

    private void validateDuplicates(String loginId, String email, String nickname) {
        if (accountRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (accountRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (accountRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }
}
