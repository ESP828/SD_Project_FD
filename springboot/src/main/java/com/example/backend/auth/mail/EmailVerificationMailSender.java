package com.example.backend.auth.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationMailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationMailSender.class);

    private final JavaMailSender mailSender;

    public EmailVerificationMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 실제 SMTP 발송(TLS 핸드셰이크 포함)은 수백ms~수초가 걸려서 요청 스레드를 그대로 막으면
     * "인증코드 발송" 버튼이 느리게 느껴진다. 인증코드는 이미 DB에 동기로 저장돼 있으므로
     * 메일 발송 자체는 비동기로 돌리고 API 응답은 즉시 내려준다.
     */
    @Async
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[푸드덕] 이메일 인증번호");
        message.setText("요청하신 이메일 인증번호는 [" + code + "] 입니다. 인증번호는 5분간 유효합니다.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Failed to send verification email to {}", email, exception);
        }
    }
}
