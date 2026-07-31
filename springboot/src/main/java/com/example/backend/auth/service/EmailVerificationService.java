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

/**
 * signup_email_verification 테이블은 회원가입 이메일 인증뿐 아니라
 * 아이디/비밀번호 찾기(계정 복구) 인증에도 함께 사용된다. email 단일 unique 제약이라
 * 같은 이메일로 회원가입 인증과 계정 복구 인증이 동시에 진행되면 서로 코드를 덮어쓸 수 있으나,
 * 실제 영향은 "인증번호 재발급 필요" 수준이라 별도 purpose 컬럼/마이그레이션 없이 허용한다.
 */
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
        issueAndSend(email);
    }

    /**
     * 아이디/비밀번호 찾기용 인증코드 발송. sendCode()와 반대로 계정이 이미 존재해야 한다.
     */
    @Transactional
    public void sendRecoveryCode(String rawEmail) {
        String email = normalize(rawEmail);
        if (!accountRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
        }
        issueAndSend(email);
    }

    private void issueAndSend(String email) {
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
