package com.example.backend.auth.service;

import com.example.backend.auth.domain.entity.EmailVerification;
import com.example.backend.auth.mail.EmailVerificationMailSender;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.repository.EmailVerificationRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class EmailVerificationService {

    private static final short MAX_ATTEMPTS = 5;

    private final EmailVerificationRepository verificationRepository;
    private final AccountRepository accountRepository;
    private final EmailVerificationMailSender mailSender;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration codeValidity;
    private final Duration verifiedValidity;

    public EmailVerificationService(
            EmailVerificationRepository verificationRepository,
            AccountRepository accountRepository,
            EmailVerificationMailSender mailSender,
            @Value("${email.verification-code-validity:300000}") long codeValidityMillis,
            @Value("${email.verification-validity:1800000}") long verifiedValidityMillis
    ) {
        this.verificationRepository = verificationRepository;
        this.accountRepository = accountRepository;
        this.mailSender = mailSender;
        this.codeValidity = Duration.ofMillis(codeValidityMillis);
        this.verifiedValidity = Duration.ofMillis(verifiedValidityMillis);
    }

    @Transactional
    public void sendCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (accountRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plus(codeValidity);
        EmailVerification verification = verificationRepository.findByEmail(email)
                .orElseGet(() -> verificationRepository.save(new EmailVerification(email, code, expiresAt)));
        verification.reissue(code, expiresAt);
        mailSender.send(email, code);
    }

    @Transactional
    public void confirmCode(String rawEmail, String code) {
        String email = normalize(rawEmail);
        EmailVerification verification = verificationRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE));
        if (verification.isExpired() || verification.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        if (!verification.getCode().equals(code)) {
            verification.registerFailedAttempt();
            throw new BusinessException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        verification.markVerified(LocalDateTime.now().plus(verifiedValidity));
    }

    @Transactional
    public void assertVerified(String rawEmail) {
        String email = normalize(rawEmail);
        EmailVerification verification = verificationRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));
        if (!verification.isVerified() || verification.isExpired()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        verificationRepository.delete(verification);
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
