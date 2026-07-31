package com.example.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FindPasswordResetRequest(
        @NotBlank(message = "아이디를 입력해 주세요.")
        @Size(max = 50, message = "아이디는 50자 이하여야 합니다.")
        String loginId,

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        String email,

        @NotBlank(message = "인증번호를 입력해 주세요.")
        @Pattern(regexp = "^[0-9]{6}$", message = "인증번호는 숫자 6자리입니다.")
        String code
) {
}
