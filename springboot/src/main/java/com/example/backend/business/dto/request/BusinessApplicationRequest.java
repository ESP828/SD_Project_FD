package com.example.backend.business.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BusinessApplicationRequest(
        @NotBlank(message = "사업자명은 필수입니다.")
        @Size(max = 100, message = "사업자명은 100자 이하여야 합니다.")
        String businessName,

        @NotBlank(message = "사업자등록번호는 필수입니다.")
        @Size(max = 20, message = "사업자등록번호는 20자 이하여야 합니다.")
        String businessNumber,

        @NotBlank(message = "대표자명은 필수입니다.")
        @Size(max = 50, message = "대표자명은 50자 이하여야 합니다.")
        String representativeName,

        @NotNull(message = "개업일자는 필수입니다.")
        @PastOrPresent(message = "개업일자가 올바르지 않습니다.")
        LocalDate openedAt,

        @NotBlank(message = "연락처는 필수입니다.")
        @Size(max = 30, message = "연락처는 30자 이하여야 합니다.")
        String contact,

        @Size(max = 500, message = "신청 사유는 500자 이하여야 합니다.")
        String reason
) {
}
