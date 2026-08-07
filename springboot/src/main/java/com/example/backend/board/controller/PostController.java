package com.example.backend.board.controller;

import com.example.backend.board.dto.request.PostCreateRequest;
import com.example.backend.board.dto.request.PostUpdateRequest;
import com.example.backend.board.dto.response.PostDetailResponse;
import com.example.backend.board.dto.response.PostLikeResponse;
import com.example.backend.board.dto.response.PostListItemResponse;
import com.example.backend.board.dto.response.PostPageResponse;
import com.example.backend.board.service.PostService;
import com.example.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/board/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ApiResponse<PostPageResponse> getPosts(
            @RequestParam(required = false) String boardType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "LATEST") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(postService.getPosts(
                boardType,
                category,
                keyword,
                sort,
                page,
                size,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @GetMapping("/business-access")
    public ApiResponse<Boolean> canUseBusinessBoard(Authentication authentication) {
        return ApiResponse.success(postService.canUseBusinessBoard(
                BoardAuthentication.accountId(authentication)
        ));
    }

    @GetMapping("/best")
    public ApiResponse<List<PostListItemResponse>> getBestPosts(
            @RequestParam(required = false) String boardType,
            @RequestParam(defaultValue = "3") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(postService.getBestPosts(
                boardType,
                size,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @GetMapping("/popular")
    public ApiResponse<List<PostListItemResponse>> getPopularPosts(
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(postService.getPopularPosts(
                size,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @GetMapping("/best/community")
    public ApiResponse<PostPageResponse> getBestPostPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(postService.getBestPostPage(
                page,
                size,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @GetMapping("/unanswered")
    public ApiResponse<List<PostListItemResponse>> getUnansweredPosts(
            @RequestParam(required = false) String boardType,
            @RequestParam(defaultValue = "3") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(postService.getUnansweredPosts(
                boardType,
                size,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @GetMapping("/authors/{authorAccountId}/summary")
    public ApiResponse<PostService.AuthorSummaryResponse> getAuthorSummary(
            @PathVariable Long authorAccountId,
            @RequestParam(required = false) Long excludePostId,
            Authentication authentication
    ) {
        return ApiResponse.success(postService.getAuthorSummary(
                authorAccountId,
                excludePostId,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @GetMapping("/{postId}/related")
    public ApiResponse<List<PostListItemResponse>> getRelatedPosts(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "5") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(postService.getRelatedPosts(
                postId,
                size,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @PostMapping(value = "/{postId}/media", consumes = MediaType.ALL_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostDetailResponse.MediaResponse> uploadMedia(
            @PathVariable Long postId,
            @RequestHeader("X-File-Name") String encodedFileName,
            @RequestHeader(
                    value = HttpHeaders.CONTENT_TYPE,
                    required = false
            ) String contentType,
            @RequestBody byte[] mediaData,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "첨부파일이 등록되었습니다.",
                postService.uploadMedia(
                        postId,
                        encodedFileName,
                        contentType,
                        mediaData,
                        BoardAuthentication.accountId(authentication)
                )
        );
    }

    @GetMapping("/media/{postMediaId}")
    public ResponseEntity<byte[]> getMedia(
            @PathVariable Long postMediaId,
            @RequestHeader(
                    value = HttpHeaders.RANGE,
                    required = false
            ) String range,
            @RequestParam(defaultValue = "false") boolean download,
            Authentication authentication
    ) {
        PostService.MediaDownload media = postService.getMedia(
                postMediaId,
                range,
                download,
                BoardAuthentication.accountId(authentication)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(resolveMediaType(media.mimeType()));
        headers.setContentLength(media.data().length);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setCacheControl("private, max-age=3600, no-transform");
        headers.setETag(
                "\"board-media-" + media.postMediaId()
                        + "-" + media.totalSize() + "\""
        );
        headers.set(HttpHeaders.VARY, HttpHeaders.RANGE);
        headers.setContentDisposition(
                (media.download()
                        ? ContentDisposition.attachment()
                        : ContentDisposition.inline())
                        .filename(
                                media.originalName(),
                                StandardCharsets.UTF_8
                        )
                        .build()
        );
        if (media.partial()) {
            headers.set(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes " + media.start() + "-" + media.end()
                            + "/" + media.totalSize()
            );
        }

        return new ResponseEntity<>(
                media.data(),
                headers,
                media.partial()
                        ? HttpStatus.PARTIAL_CONTENT
                        : HttpStatus.OK
        );
    }

    @DeleteMapping("/{postId}/media/{postMediaId}")
    public ApiResponse<Void> deleteMedia(
            @PathVariable Long postId,
            @PathVariable Long postMediaId,
            Authentication authentication
    ) {
        postService.deleteMedia(
                postId,
                postMediaId,
                BoardAuthentication.accountId(authentication)
        );
        return ApiResponse.success("첨부파일이 삭제되었습니다.", null);
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> getPost(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        return ApiResponse.success(postService.getPost(
                postId,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostDetailResponse> createPost(
            @Valid @RequestBody PostCreateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "게시글이 등록되었습니다.",
                postService.createPost(
                        request,
                        BoardAuthentication.accountId(authentication)
                )
        );
    }

    @PutMapping("/{postId}")
    public ApiResponse<PostDetailResponse> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request,
            Authentication authentication
    ) {
        return update(postId, request, authentication);
    }

    /**
     * JS 원본이 지원하던 기존 클라이언트 호환 경로다. 동작은 PUT 전체 수정과 같다.
     */
    @PatchMapping("/{postId}")
    public ApiResponse<PostDetailResponse> updatePostCompatibility(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request,
            Authentication authentication
    ) {
        return update(postId, request, authentication);
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> deletePost(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        postService.deletePost(
                postId,
                BoardAuthentication.accountId(authentication)
        );
        return ApiResponse.success(
                "게시글과 연결된 댓글·추천이 정리되었습니다.",
                null
        );
    }

    @PostMapping("/{postId}/like")
    public ApiResponse<PostLikeResponse> likePost(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "게시글을 추천했습니다.",
                postService.likePost(
                        postId,
                        BoardAuthentication.accountId(authentication)
                )
        );
    }

    @DeleteMapping("/{postId}/like")
    public ApiResponse<PostLikeResponse> unlikePost(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "게시글 추천을 취소했습니다.",
                postService.unlikePost(
                        postId,
                        BoardAuthentication.accountId(authentication)
                )
        );
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

    private ApiResponse<PostDetailResponse> update(
            Long postId,
            PostUpdateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "게시글이 수정되었습니다.",
                postService.updatePost(
                        postId,
                        request,
                        BoardAuthentication.accountId(authentication)
                )
        );
    }
}
