package com.example.backend.mypage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawAccountRequest(
        @Size(max = 128, message = "비밀번호가 너무 깁니다.")
        String currentPassword,

        @NotBlank(message = "회원 탈퇴 확인 문구를 입력해 주세요.")
        @Size(max = 20, message = "회원 탈퇴 확인 문구가 너무 깁니다.")
        String confirmation
) {
}
