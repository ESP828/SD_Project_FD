package com.example.backend.board.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.board.domain.entity.Post;
import com.example.backend.board.domain.entity.PostLike;
import com.example.backend.board.domain.entity.PostLikeId;
import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.CommentStatus;
import com.example.backend.board.domain.type.PostCategory;
import com.example.backend.board.domain.type.PostStatus;
import com.example.backend.board.dto.request.PostCreateRequest;
import com.example.backend.board.dto.request.PostUpdateRequest;
import com.example.backend.board.dto.response.PostDetailResponse;
import com.example.backend.board.dto.response.PostLikeResponse;
import com.example.backend.board.dto.response.PostListItemResponse;
import com.example.backend.board.dto.response.PostPageResponse;
import com.example.backend.board.exception.BoardException;
import com.example.backend.board.mapper.BoardResponseMapper;
import com.example.backend.board.policy.BoardAccessPolicy;
import com.example.backend.board.repository.CommentRepository;
import com.example.backend.board.repository.PostLikeRepository;
import com.example.backend.board.repository.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PostService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_BEST_SIZE = 10;
    private static final long DUPLICATE_SUBMISSION_WINDOW_NANOS =
            Duration.ofMillis(3_500).toNanos();
    private static final long SUBMISSION_IN_PROGRESS = Long.MAX_VALUE;
    private static final int SUBMISSION_CLEANUP_INTERVAL = 256;

    private final ConcurrentMap<PostSubmissionKey, Long> recentPostSubmissions =
            new ConcurrentHashMap<>();
    private final AtomicInteger postSubmissionChecks = new AtomicInteger();

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final BoardUserService boardUserService;
    private final BoardAccessPolicy accessPolicy;
    private final BoardResponseMapper responseMapper;

    @Value("${board.best-window-days:30}")
    private int bestWindowDays = 30;

    public PostService(
            PostRepository postRepository,
            CommentRepository commentRepository,
            PostLikeRepository postLikeRepository,
            BoardUserService boardUserService,
            BoardAccessPolicy accessPolicy,
            BoardResponseMapper responseMapper
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.boardUserService = boardUserService;
        this.accessPolicy = accessPolicy;
        this.responseMapper = responseMapper;
    }

    @Transactional(readOnly = true)
    public PostPageResponse getPosts(
            String boardTypeValue,
            String categoryValue,
            String keyword,
            String sortValue,
            int page,
            int size,
            Long currentAccountId
    ) {
        validatePage(page, size);
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        BoardType boardType = accessPolicy.resolveReadableBoardType(
                parseBoardType(boardTypeValue),
                currentAccount
        );
        PostCategory category = parseCategory(categoryValue);
        String normalizedKeyword = normalizeKeyword(keyword);
        String sort = normalizeSort(sortValue);

        Page<Post> result;
        if ("COMMENTS".equals(sort)) {
            result = postRepository.searchOrderByComments(
                    boardType,
                    category,
                    normalizedKeyword,
                    PostStatus.ACTIVE,
                    CommentStatus.ACTIVE,
                    PageRequest.of(page, size)
            );
        } else {
            Sort springSort = "LIKES".equals(sort)
                    ? Sort.by(
                            Sort.Order.desc("likeCount"),
                            Sort.Order.desc("createdAt")
                    )
                    : Sort.by(Sort.Order.desc("createdAt"));
            result = postRepository.search(
                    boardType,
                    category,
                    normalizedKeyword,
                    PostStatus.ACTIVE,
                    PageRequest.of(page, size, springSort)
            );
        }

        return new PostPageResponse(
                responseMapper.toListItems(result.getContent(), currentAccount),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @Transactional(readOnly = true)
    public List<PostListItemResponse> getBestPosts(
            String boardTypeValue,
            int size,
            Long currentAccountId
    ) {
        if (size < 1 || size > MAX_BEST_SIZE) {
            throw badRequest(
                    "베스트 게시글 개수는 1~" + MAX_BEST_SIZE + " 사이여야 합니다."
            );
        }
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        BoardType boardType = accessPolicy.resolveReadableBoardType(
                parseBoardType(boardTypeValue),
                currentAccount
        );
        int safeWindowDays = Math.max(1, Math.min(bestWindowDays, 365));
        List<Post> posts = postRepository.findBestPosts(
                boardType,
                PostCategory.NOTICE,
                LocalDateTime.now().minusDays(safeWindowDays),
                PostStatus.ACTIVE,
                PageRequest.of(0, size)
        );
        return responseMapper.toListItems(posts, currentAccount);
    }

    @Transactional
    public PostDetailResponse getPost(Long postId, Long currentAccountId) {
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        Post post = getExistingPost(postId);
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);

        if (postRepository.increaseViewCount(postId, PostStatus.ACTIVE) != 1) {
            throw notFound("게시글을 찾을 수 없습니다.");
        }
        return responseMapper.toDetail(getExistingPost(postId), currentAccount);
    }

    @Transactional
    public PostDetailResponse createPost(
            PostCreateRequest request,
            Long currentAccountId
    ) {
        String title = request.title().strip();
        String content = request.content().strip();
        PostSubmissionKey submissionKey = new PostSubmissionKey(
                currentAccountId,
                request.boardType(),
                request.category(),
                request.restaurantId(),
                title,
                content
        );
        long checkedAt = System.nanoTime();
        if (!reservePostSubmission(submissionKey, checkedAt)) {
            throw duplicatePostSubmission();
        }

        try {
            Account currentAccount = boardUserService.require(currentAccountId);
            accessPolicy.assertCanWrite(
                    request.boardType(),
                    request.category(),
                    request.restaurantId(),
                    currentAccount
            );

            Post post = Post.create(
                    currentAccount,
                    request.restaurantId(),
                    request.boardType(),
                    request.category(),
                    title,
                    content
            );
            postRepository.save(post);
            PostDetailResponse response =
                    responseMapper.toDetail(post, currentAccount);
            completePostSubmission(submissionKey);
            return response;
        } catch (RuntimeException exception) {
            recentPostSubmissions.remove(
                    submissionKey,
                    SUBMISSION_IN_PROGRESS
            );
            throw exception;
        }
    }

    @Transactional
    public PostDetailResponse updatePost(
            Long postId,
            PostUpdateRequest request,
            Long currentAccountId
    ) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Post post = getExistingPostForUpdate(postId);
        assertOwnerOrAdmin(post, currentAccount);
        accessPolicy.assertBoardTypeChangeAllowed(
                post.getBoardType(),
                request.boardType(),
                currentAccount
        );
        accessPolicy.assertCanWrite(
                request.boardType(),
                request.category(),
                request.restaurantId(),
                currentAccount
        );

        post.update(
                request.restaurantId(),
                request.boardType(),
                request.category(),
                request.title().strip(),
                request.content().strip()
        );
        return responseMapper.toDetail(post, currentAccount);
    }

    @Transactional
    public void deletePost(Long postId, Long currentAccountId) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Post post = getExistingPostForUpdate(postId);
        assertOwnerOrAdmin(post, currentAccount);

        LocalDateTime deletedAt = LocalDateTime.now();
        commentRepository.softDeleteAllByPostId(
                postId,
                CommentStatus.ACTIVE,
                CommentStatus.DELETED,
                deletedAt
        );
        postLikeRepository.deleteAllByPostId(postId);
        post.clearLikeCount();
        post.softDelete(deletedAt);
    }

    @Transactional
    public PostLikeResponse likePost(Long postId, Long currentAccountId) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Post post = getExistingPostForUpdate(postId);
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);

        PostLikeId id = new PostLikeId(postId, currentAccount.getAccountId());
        if (!postLikeRepository.existsById(id)) {
            postLikeRepository.save(PostLike.create(post, currentAccount));
            post.increaseLikeCount();
        }
        return new PostLikeResponse(postId, post.getLikeCount(), true);
    }

    @Transactional
    public PostLikeResponse unlikePost(Long postId, Long currentAccountId) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Post post = getExistingPostForUpdate(postId);
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);

        PostLikeId id = new PostLikeId(postId, currentAccount.getAccountId());
        if (postLikeRepository.existsById(id)) {
            postLikeRepository.deleteById(id);
            post.decreaseLikeCount();
        }
        return new PostLikeResponse(postId, post.getLikeCount(), false);
    }

    private Post getExistingPost(Long postId) {
        validateId(postId, "게시글");
        return postRepository.findByPostIdAndStatus(postId, PostStatus.ACTIVE)
                .orElseThrow(() -> notFound("게시글을 찾을 수 없습니다."));
    }

    private Post getExistingPostForUpdate(Long postId) {
        validateId(postId, "게시글");
        return postRepository.findByPostIdAndStatusForUpdate(postId, PostStatus.ACTIVE)
                .orElseThrow(() -> notFound("게시글을 찾을 수 없습니다."));
    }

    private boolean reservePostSubmission(
            PostSubmissionKey submissionKey,
            long checkedAt
    ) {
        cleanExpiredPostSubmissions(checkedAt);
        while (true) {
            Long previous = recentPostSubmissions.putIfAbsent(
                    submissionKey,
                    SUBMISSION_IN_PROGRESS
            );
            if (previous == null) {
                return true;
            }
            if (previous == SUBMISSION_IN_PROGRESS
                    || checkedAt - previous < DUPLICATE_SUBMISSION_WINDOW_NANOS) {
                return false;
            }
            if (recentPostSubmissions.replace(
                    submissionKey,
                    previous,
                    SUBMISSION_IN_PROGRESS
            )) {
                return true;
            }
        }
    }

    private void completePostSubmission(PostSubmissionKey submissionKey) {
        recentPostSubmissions.replace(
                submissionKey,
                SUBMISSION_IN_PROGRESS,
                System.nanoTime()
        );
    }

    private void cleanExpiredPostSubmissions(long checkedAt) {
        if ((postSubmissionChecks.incrementAndGet()
                & (SUBMISSION_CLEANUP_INTERVAL - 1)) != 0) {
            return;
        }
        recentPostSubmissions.forEach((submissionKey, completedAt) -> {
            if (completedAt != SUBMISSION_IN_PROGRESS
                    && checkedAt - completedAt
                    >= DUPLICATE_SUBMISSION_WINDOW_NANOS) {
                recentPostSubmissions.remove(submissionKey, completedAt);
            }
        });
    }

    private BoardException duplicatePostSubmission() {
        return new BoardException(
                HttpStatus.CONFLICT,
                "BOARD_DUPLICATE_POST_SUBMISSION",
                "같은 내용의 게시글이 이미 등록 중이거나 방금 등록되었습니다."
        );
    }

    private void assertOwnerOrAdmin(Post post, Account account) {
        boolean owned = post.getAuthor().getAccountId().equals(account.getAccountId());
        if (!owned && !accessPolicy.isAdmin(account)) {
            throw new BoardException(
                    HttpStatus.FORBIDDEN,
                    "BOARD_POST_OWNER_REQUIRED",
                    "게시글 작성자 또는 관리자만 수정·삭제할 수 있습니다."
            );
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw badRequest("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest(
                    "페이지 크기는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다."
            );
        }
    }

    private void validateId(Long id, String field) {
        if (id == null || id < 1) {
            throw badRequest(field + " 번호는 1 이상의 숫자여야 합니다.");
        }
    }

    private BoardType parseBoardType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return BoardType.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("boardType은 GENERAL 또는 BUSINESS여야 합니다.");
        }
    }

    private PostCategory parseCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return PostCategory.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("지원하지 않는 게시글 카테고리입니다.");
        }
    }

    private String normalizeSort(String value) {
        String normalized = value == null || value.isBlank()
                ? "LATEST"
                : value.strip().toUpperCase(Locale.ROOT);
        if (!List.of("LATEST", "LIKES", "COMMENTS").contains(normalized)) {
            throw badRequest("sort는 LATEST, LIKES, COMMENTS 중 하나여야 합니다.");
        }
        return normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String normalized = keyword.strip();
        if (normalized.length() > 100) {
            throw badRequest("검색어는 100자 이하로 입력해 주세요.");
        }
        return normalized;
    }

    private BoardException badRequest(String message) {
        return new BoardException(
                HttpStatus.BAD_REQUEST,
                "BOARD_INVALID_INPUT",
                message
        );
    }

    private BoardException notFound(String message) {
        return new BoardException(
                HttpStatus.NOT_FOUND,
                "BOARD_POST_NOT_FOUND",
                message
        );
    }

    private record PostSubmissionKey(
            Long accountId,
            BoardType boardType,
            PostCategory category,
            Long restaurantId,
            String title,
            String content
    ) {
    }
}
