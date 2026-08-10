package com.example.backend.board.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank(message = "댓글 내용을 입력해 주세요.")
        @Size(max = 1000, message = "댓글은 1,000자 이하로 입력해 주세요.")
        String content,
        @Positive(message = "부모 댓글 번호는 1 이상의 숫자여야 합니다.")
        Long parentCommentId
) {
    public CommentCreateRequest(String content) {
        this(content, null);
    }
}
