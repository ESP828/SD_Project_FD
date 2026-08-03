package com.example.backend.auth.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.entity.AccountCredential;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.dto.request.LoginRequest;
import com.example.backend.auth.dto.request.SignupRequest;
import com.example.backend.auth.dto.response.AuthTokenResponse;
import com.example.backend.auth.dto.response.FindLoginIdResponse;
import com.example.backend.auth.mail.TemporaryPasswordMailSender;
import com.example.backend.auth.repository.AccountCredentialRepository;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;

@Service
public class AuthService {

    private static final String TEMP_PASSWORD_UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String TEMP_PASSWORD_LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String TEMP_PASSWORD_DIGIT = "23456789";
    private static final String TEMP_PASSWORD_SPECIAL = "!@#$%^&*";
    private static final String TEMP_PASSWORD_ALL =
            TEMP_PASSWORD_UPPER + TEMP_PASSWORD_LOWER + TEMP_PASSWORD_DIGIT + TEMP_PASSWORD_SPECIAL;
    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final AccountRepository accountRepository;
    private final AccountCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthorityService authorityService;
    private final TokenService tokenService;
    private final EmailVerificationService emailVerificationService;
    private final TemporaryPasswordMailSender temporaryPasswordMailSender;
    private final RefreshTokenService refreshTokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            AccountRepository accountRepository,
            AccountCredentialRepository credentialRepository,
            PasswordEncoder passwordEncoder,
            AuthorityService authorityService,
            TokenService tokenService,
            EmailVerificationService emailVerificationService,
            TemporaryPasswordMailSender temporaryPasswordMailSender,
            RefreshTokenService refreshTokenService
    ) {
        this.accountRepository = accountRepository;
        this.credentialRepository = credentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorityService = authorityService;
        this.tokenService = tokenService;
        this.emailVerificationService = emailVerificationService;
        this.temporaryPasswordMailSender = temporaryPasswordMailSender;
        this.refreshTokenService = refreshTokenService;
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
    public LoginResult login(LoginRequest request) {
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
        AuthTokenResponse accessToken = tokenService.issueAccessToken(account);
        String refreshToken = request.rememberLogin() ? refreshTokenService.issue(account) : null;
        return new LoginResult(accessToken, refreshToken);
    }

    public record LoginResult(AuthTokenResponse accessToken, String refreshToken) {
    }

    public boolean isLoginIdAvailable(String loginId) {
        return !accountRepository.existsByLoginId(loginId.trim());
    }

    @Transactional
    public FindLoginIdResponse findLoginId(String rawEmail, String code) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        emailVerificationService.confirmCode(email, code);
        emailVerificationService.assertVerified(email);
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        if (account.getLoginId() == null) {
            throw new BusinessException(ErrorCode.LOGIN_ID_NOT_AVAILABLE);
        }
        return new FindLoginIdResponse(account.getLoginId());
    }

    @Transactional
    public void requestPasswordResetCode(String rawLoginId, String rawEmail) {
        Account account = matchAccount(rawLoginId, rawEmail);
        emailVerificationService.sendRecoveryCode(account.getEmail());
    }

    @Transactional
    public void resetPassword(String rawLoginId, String rawEmail, String code) {
        Account account = matchAccount(rawLoginId, rawEmail);
        emailVerificationService.confirmCode(account.getEmail(), code);
        emailVerificationService.assertVerified(account.getEmail());
        AccountCredential credential = credentialRepository.findById(account.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        String tempPassword = generateTemporaryPassword();
        credential.changePassword(passwordEncoder.encode(tempPassword));
        refreshTokenService.revokeAllForAccount(account.getAccountId());
        temporaryPasswordMailSender.send(account.getEmail(), tempPassword);
    }

    private Account matchAccount(String rawLoginId, String rawEmail) {
        String loginId = rawLoginId.trim();
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        Account account = accountRepository.findByLoginIdAndEmail(loginId, email)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        return account;
    }

    private String generateTemporaryPassword() {
        char[] password = new char[TEMP_PASSWORD_LENGTH];
        password[0] = TEMP_PASSWORD_UPPER.charAt(secureRandom.nextInt(TEMP_PASSWORD_UPPER.length()));
        password[1] = TEMP_PASSWORD_LOWER.charAt(secureRandom.nextInt(TEMP_PASSWORD_LOWER.length()));
        password[2] = TEMP_PASSWORD_DIGIT.charAt(secureRandom.nextInt(TEMP_PASSWORD_DIGIT.length()));
        password[3] = TEMP_PASSWORD_SPECIAL.charAt(secureRandom.nextInt(TEMP_PASSWORD_SPECIAL.length()));
        for (int i = 4; i < TEMP_PASSWORD_LENGTH; i++) {
            password[i] = TEMP_PASSWORD_ALL.charAt(secureRandom.nextInt(TEMP_PASSWORD_ALL.length()));
        }
        for (int i = password.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char temp = password[i];
            password[i] = password[j];
            password[j] = temp;
        }
        return new String(password);
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
