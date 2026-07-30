package com.example.backend.board.dto.request;

import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostCreateRequest(
        @NotNull(message = "게시 공간을 선택해 주세요.")
        BoardType boardType,

        @NotNull(message = "카테고리를 선택해 주세요.")
        PostCategory category,

        Long restaurantId,

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 200, message = "제목은 200자 이하로 입력해 주세요.")
        String title,

        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 10000, message = "내용은 10,000자 이하로 입력해 주세요.")
        String content
) {
}
