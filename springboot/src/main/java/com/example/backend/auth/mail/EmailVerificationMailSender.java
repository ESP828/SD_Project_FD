package com.example.backend.auth.mail;

import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationMailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationMailSender.class);

    private final JavaMailSender mailSender;

    public EmailVerificationMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[푸드덕] 이메일 인증번호");
        message.setText("요청하신 이메일 인증번호는 [" + code + "] 입니다. 인증번호는 5분간 유효합니다.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Failed to send verification email to {}", email, exception);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
