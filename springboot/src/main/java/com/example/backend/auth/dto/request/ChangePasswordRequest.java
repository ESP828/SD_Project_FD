package com.example.backend.auth.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 모두 포함해야 합니다."
        )
        String newPassword,

        @NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
        String newPasswordConfirm
) {
    @AssertTrue(message = "새 비밀번호가 일치하지 않습니다.")
    public boolean isNewPasswordConfirmed() {
        return newPassword != null && newPassword.equals(newPasswordConfirm);
    }
}
