package com.example.backend.board.controller;

import com.example.backend.board.dto.request.CommentCreateRequest;
import com.example.backend.board.dto.request.CommentUpdateRequest;
import com.example.backend.board.dto.response.CommentPageResponse;
import com.example.backend.board.dto.response.CommentResponse;
import com.example.backend.board.service.CommentService;
import com.example.backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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

    @PostMapping(value = "/comments/{commentId}/image", consumes = MediaType.ALL_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> uploadCommentImage(
            @PathVariable Long commentId,
            @RequestHeader("X-File-Name") String encodedFileName,
            @RequestHeader(
                    value = HttpHeaders.CONTENT_TYPE,
                    required = false
            ) String contentType,
            Authentication authentication,
            HttpServletRequest request
    ) throws IOException {
        commentService.uploadCommentImage(
                commentId,
                encodedFileName,
                contentType,
                request.getInputStream(),
                request.getContentLengthLong(),
                BoardAuthentication.accountId(authentication)
        );
        return ApiResponse.success("댓글 사진이 등록되었습니다.", null);
    }

    @GetMapping("/comments/{commentId}/image")
    public void getCommentImage(
            @PathVariable Long commentId,
            Authentication authentication,
            HttpServletResponse response
    ) throws IOException {
        CommentService.CommentImageDownload image =
                commentService.getCommentImage(
                        commentId,
                        BoardAuthentication.accountId(authentication)
                );

        response.setStatus(HttpStatus.OK.value());
        response.setContentType(resolveMediaType(image.mimeType()).toString());
        response.setContentLengthLong(image.fileSize());
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                "private, max-age=3600, no-transform"
        );
        response.setHeader(
                HttpHeaders.ETAG,
                "\"board-comment-image-" + image.commentId()
                        + "-" + image.fileSize() + "\""
        );
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline()
                        .filename(
                                image.originalName(),
                                StandardCharsets.UTF_8
                        )
                        .build()
                        .toString()
        );

        OutputStream outputStream = response.getOutputStream();
        commentService.streamCommentImage(
                image.commentId(),
                image.fileSize(),
                outputStream
        );
        outputStream.flush();
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

    private MediaType resolveMediaType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
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
