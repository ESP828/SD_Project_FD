package com.example.backend.mypage.dto.request;

import com.example.backend.auth.domain.type.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record MyPageProfileUpdateRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 2, max = 30, message = "닉네임은 2~30자로 입력해 주세요.")
        String nickname,

        @NotNull(message = "성별을 선택해 주세요.")
        Gender gender,

        @Past(message = "생년월일은 오늘보다 이전 날짜여야 합니다.")
        LocalDate birthDate
) {
}
