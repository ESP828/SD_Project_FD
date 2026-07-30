package com.example.backend.board.controller;

import com.example.backend.board.dto.request.CommentCreateRequest;
import com.example.backend.board.dto.request.CommentUpdateRequest;
import com.example.backend.board.dto.response.CommentPageResponse;
import com.example.backend.board.dto.response.CommentResponse;
import com.example.backend.board.service.CommentService;
import com.example.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/board")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<CommentPageResponse> getComments(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(commentService.getComments(
                postId,
                page,
                size,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentResponse> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "댓글이 등록되었습니다.",
                commentService.createComment(
                        postId,
                        request,
                        BoardAuthentication.accountId(authentication)
                )
        );
    }

    @PutMapping("/comments/{commentId}")
    public ApiResponse<CommentResponse> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            Authentication authentication
    ) {
        return update(commentId, request, authentication);
    }

    @PatchMapping("/comments/{commentId}")
    public ApiResponse<CommentResponse> updateCommentCompatibility(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            Authentication authentication
    ) {
        return update(commentId, request, authentication);
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(
            @PathVariable Long commentId,
            Authentication authentication
    ) {
        commentService.deleteComment(
                commentId,
                BoardAuthentication.accountId(authentication)
        );
        return ApiResponse.success("댓글이 삭제되었습니다.", null);
    }

    private ApiResponse<CommentResponse> update(
            Long commentId,
            CommentUpdateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "댓글이 수정되었습니다.",
                commentService.updateComment(
                        commentId,
                        request,
                        BoardAuthentication.accountId(authentication)
                )
        );
    }
}
