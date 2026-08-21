package com.example.backend.board.controller;

import com.example.backend.board.dto.request.PostCreateRequest;
import com.example.backend.board.dto.request.PostUpdateRequest;
import com.example.backend.board.dto.response.PostDetailResponse;
import com.example.backend.board.dto.response.PostLikeResponse;
import com.example.backend.board.dto.response.PostListItemResponse;
import com.example.backend.board.dto.response.PostPageResponse;
import com.example.backend.board.exception.BoardException;
import com.example.backend.board.service.PostService;
import com.example.backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

import java.io.IOException;
import java.io.OutputStream;
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

    @GetMapping("/restaurants/public/{publicRestaurantId}/news")
    public ApiResponse<PostService.RestaurantNewsPageResponse>
    getPublicRestaurantNews(
            @PathVariable Long publicRestaurantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(postService.getPublicRestaurantNews(
                publicRestaurantId,
                page,
                size,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @PostMapping("/restaurants/public/{publicRestaurantId}/news")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostService.RestaurantNewsItemResponse>
    createPublicRestaurantNews(
            @PathVariable Long publicRestaurantId,
            @Valid @RequestBody NewsCreateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "식당 소식이 등록되었습니다.",
                postService.createPublicRestaurantNews(
                        publicRestaurantId,
                        request.title(),
                        request.content(),
                        BoardAuthentication.accountId(authentication)
                )
        );
    }

    @PutMapping("/restaurants/public/{publicRestaurantId}/news/{postId}")
    public ApiResponse<PostDetailResponse> updatePublicRestaurantNews(
            @PathVariable Long publicRestaurantId,
            @PathVariable Long postId,
            @Valid @RequestBody NewsCreateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "식당 소식이 수정되었습니다.",
                postService.updatePublicRestaurantNews(
                        publicRestaurantId,
                        postId,
                        request.title(),
                        request.content(),
                        BoardAuthentication.accountId(authentication)
                )
        );
    }

    @DeleteMapping("/restaurants/public/{publicRestaurantId}/news/{postId}")
    public ApiResponse<Void> deletePublicRestaurantNews(
            @PathVariable Long publicRestaurantId,
            @PathVariable Long postId,
            Authentication authentication
    ) {
        postService.deletePublicRestaurantNews(
                publicRestaurantId,
                postId,
                BoardAuthentication.accountId(authentication)
        );
        return ApiResponse.success("식당 소식이 삭제되었습니다.", null);
    }

    @GetMapping("/restaurants/{restaurantId}/news")
    public ApiResponse<PostService.RestaurantNewsPageResponse>
    getOwnedRestaurantNews(
            @PathVariable Long restaurantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        return ApiResponse.success(postService.getOwnedRestaurantNews(
                restaurantId,
                page,
                size,
                BoardAuthentication.accountId(authentication)
        ));
    }

    @PostMapping("/restaurants/{restaurantId}/news")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostService.RestaurantNewsItemResponse>
    createOwnedRestaurantNews(
            @PathVariable Long restaurantId,
            @Valid @RequestBody NewsCreateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "식당 소식이 등록되었습니다.",
                postService.createOwnedRestaurantNews(
                        restaurantId,
                        request.title(),
                        request.content(),
                        BoardAuthentication.accountId(authentication)
                )
        );
    }

    @PutMapping("/restaurants/{restaurantId}/news/{postId}")
    public ApiResponse<PostDetailResponse> updateOwnedRestaurantNews(
            @PathVariable Long restaurantId,
            @PathVariable Long postId,
            @Valid @RequestBody NewsCreateRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "식당 소식이 수정되었습니다.",
                postService.updateOwnedRestaurantNews(
                        restaurantId,
                        postId,
                        request.title(),
                        request.content(),
                        BoardAuthentication.accountId(authentication)
                )
        );
    }

    @DeleteMapping("/restaurants/{restaurantId}/news/{postId}")
    public ApiResponse<Void> deleteOwnedRestaurantNews(
            @PathVariable Long restaurantId,
            @PathVariable Long postId,
            Authentication authentication
    ) {
        postService.deleteOwnedRestaurantNews(
                restaurantId,
                postId,
                BoardAuthentication.accountId(authentication)
        );
        return ApiResponse.success("식당 소식이 삭제되었습니다.", null);
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

    @GetMapping("/authors/reviews")
    public ApiResponse<List<PostService.ReviewAuthorLinkResponse>>
    getReviewAuthorLinks(@RequestParam List<Long> reviewIds) {
        return ApiResponse.success(postService.getReviewAuthorLinks(reviewIds));
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
            HttpServletRequest request,
            Authentication authentication
    ) {
        PostDetailResponse.MediaResponse media;
        try {
            media = postService.uploadMedia(
                    postId,
                    encodedFileName,
                    contentType,
                    request.getInputStream(),
                    request.getContentLengthLong(),
                    BoardAuthentication.accountId(authentication)
            );
        } catch (IOException exception) {
            throw new BoardException(
                    HttpStatus.BAD_REQUEST,
                    "BOARD_MEDIA_READ_FAILED",
                    "첨부파일 전송 데이터를 읽지 못했습니다."
            );
        }
        return ApiResponse.success(
                "PROCESSING".equals(media.processingStatus())
                        ? "동영상 전송이 완료되어 서버 처리를 시작했습니다."
                        : "첨부파일이 등록되었습니다.",
                media
        );
    }

    @GetMapping("/{postId}/media")
    public ResponseEntity<ApiResponse<List<PostDetailResponse.MediaResponse>>>
    getMediaStatus(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(postService.getMediaStatus(
                        postId,
                        BoardAuthentication.accountId(authentication)
                )));
    }

    @GetMapping("/media/{postMediaId}")
    public void getMedia(
            @PathVariable Long postMediaId,
            @RequestHeader(
                    value = HttpHeaders.RANGE,
                    required = false
            ) String range,
            @RequestHeader(
                    value = HttpHeaders.IF_NONE_MATCH,
                    required = false
            ) String ifNoneMatch,
            @RequestParam(defaultValue = "false") boolean download,
            Authentication authentication,
            HttpServletResponse response
    ) throws IOException {
        PostService.MediaDownload media = postService.getMedia(
                postMediaId,
                range,
                download,
                BoardAuthentication.accountId(authentication)
        );

        String etag = "\"board-media-" + media.postMediaId()
                + "-" + media.totalSize() + "\"";
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setHeader(
                HttpHeaders.CACHE_CONTROL,
                "private, max-age=3600, no-transform"
        );
        response.setHeader(HttpHeaders.ETAG, etag);
        response.setHeader(HttpHeaders.VARY, HttpHeaders.RANGE);

        if (matchesIfNoneMatch(ifNoneMatch, etag)) {
            response.setStatus(HttpStatus.NOT_MODIFIED.value());
            return;
        }

        response.setStatus(
                media.partial()
                        ? HttpStatus.PARTIAL_CONTENT.value()
                        : HttpStatus.OK.value()
        );
        response.setContentType(resolveMediaType(media.mimeType()).toString());
        response.setContentLengthLong(media.contentLength());
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                (media.download()
                        ? ContentDisposition.attachment()
                        : ContentDisposition.inline())
                        .filename(
                                media.originalName(),
                                StandardCharsets.UTF_8
                        )
                        .build()
                        .toString()
        );
        if (media.partial()) {
            response.setHeader(
                    HttpHeaders.CONTENT_RANGE,
                    "bytes " + media.start() + "-" + media.end()
                            + "/" + media.totalSize()
            );
        }

        OutputStream outputStream = response.getOutputStream();
        postService.streamMedia(
                media.postMediaId(),
                media.start(),
                media.contentLength(),
                outputStream
        );
        outputStream.flush();
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

    @PatchMapping("/{postId}/pin")
    public ApiResponse<PostDetailResponse> pinPost(
            @PathVariable Long postId,
            @RequestParam String category,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "게시글을 공지로 올렸습니다.",
                postService.updatePinnedState(
                        postId,
                        category,
                        true,
                        BoardAuthentication.accountId(authentication)
                )
        );
    }

    @PatchMapping("/{postId}/unpin")
    public ApiResponse<PostDetailResponse> unpinPost(
            @PathVariable Long postId,
            @RequestParam String category,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "게시글을 공지에서 내렸습니다.",
                postService.updatePinnedState(
                        postId,
                        category,
                        false,
                        BoardAuthentication.accountId(authentication)
                )
        );
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

    private boolean matchesIfNoneMatch(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String rawCandidate : ifNoneMatch.split(",")) {
            String candidate = rawCandidate.strip();
            if ("*".equals(candidate)) {
                return true;
            }
            if (candidate.startsWith("W/")) {
                candidate = candidate.substring(2).strip();
            }
            if (etag.equals(candidate)) {
                return true;
            }
        }
        return false;
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

    public record NewsCreateRequest(
            @NotBlank(message = "제목을 입력해 주세요.")
            @Size(max = 200, message = "제목은 200자 이하로 입력해 주세요.")
            String title,

            @NotBlank(message = "내용을 입력해 주세요.")
            @Size(max = 10000, message = "내용은 10,000자 이하로 입력해 주세요.")
            String content
    ) {
    }
}
