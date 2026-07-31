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
public class TemporaryPasswordMailSender {

    private static final Logger log = LoggerFactory.getLogger(TemporaryPasswordMailSender.class);

    private final JavaMailSender mailSender;

    public TemporaryPasswordMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String email, String tempPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[푸드덕] 임시 비밀번호 안내");
        message.setText("요청하신 임시 비밀번호는 [" + tempPassword + "] 입니다. 로그인 후 반드시 비밀번호를 변경해 주세요.");
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.warn("Failed to send temporary password email to {}", email, exception);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
