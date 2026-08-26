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

    /**
     * 예전에는 @Async로 돌려서 SMTP 발송(TLS 핸드셰이크 포함, 수백ms~수초)이 API 응답을
     * 막지 않게 했는데, 그 결과 발송이 실패해도(SMTP 인증 실패, 타임아웃 등) 예외가
     * 요청 스레드로 전달되지 않고 로그에만 찍혀서 "인증번호 발송했습니다" 성공 메시지가
     * 그대로 사용자에게 내려갔다 - 정작 메일은 안 왔는데 화면은 성공이라고 알려주는 상태.
     * TemporaryPasswordMailSender와 동일하게 동기로 보내고 실패를 그대로 알린다.
     */
    public void send(String email, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[푸드덕] 이메일 인증번호");
        message.setText("요청하신 이메일 인증번호는 [" + code + "] 입니다. 인증번호는 5분간 유효합니다.");
        try {
            mailSender.send(message);
            log.info("Verification email sent to {}", email);
        } catch (MailException exception) {
            log.warn("Failed to send verification email to {}", email, exception);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }
}
