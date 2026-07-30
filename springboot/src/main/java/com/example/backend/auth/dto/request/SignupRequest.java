package com.example.backend.auth.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @JsonAlias("username")
        @NotBlank(message = "아이디를 입력해 주세요.")
        @Size(min = 4, max = 20, message = "아이디는 4~20자로 입력해 주세요.")
        @Pattern(
                regexp = "^[A-Za-z0-9_]+$",
                message = "아이디는 영문, 숫자, 밑줄만 사용할 수 있습니다."
        )
        String loginId,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 모두 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "비밀번호 확인을 입력해 주세요.")
        String passwordConfirm,

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
        String email,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 2, max = 30, message = "닉네임은 2~30자로 입력해 주세요.")
        String nickname
) {
    @AssertTrue(message = "비밀번호가 일치하지 않습니다.")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirm);
    }
}
