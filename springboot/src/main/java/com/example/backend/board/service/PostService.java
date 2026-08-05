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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private static final int MAX_DISCOVERY_SIZE = 10;
    private static final int MAX_AUTHOR_RECENT_POSTS = 5;
    private static final int BEST_COMMUNITY_MINIMUM_LIKE_COUNT = 3;
    private static final Duration BEST_COMMUNITY_WINDOW = Duration.ofDays(7);
    private static final Duration RAPID_DUPLICATE_WINDOW =
            Duration.ofMillis(3_500);
    private static final long RAPID_DUPLICATE_WINDOW_NANOS =
            RAPID_DUPLICATE_WINDOW.toNanos();
    private static final long SUBMISSION_IN_PROGRESS = Long.MAX_VALUE;
    private static final int SUBMISSION_CLEANUP_INTERVAL = 256;

    private final ConcurrentMap<PostSubmissionKey, Long>
            recentPostSubmissions = new ConcurrentHashMap<>();
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
                    PostCategory.NOTICE,
                    PageRequest.of(page, size)
            );
        } else {
            Sort springSort = "LIKES".equals(sort)
                    ? Sort.by(
                            Sort.Order.desc("likeCount"),
                            Sort.Order.desc("createdAt"),
                            Sort.Order.desc("postId")
                    )
                    : Sort.by(
                            Sort.Order.desc("createdAt"),
                            Sort.Order.desc("postId")
                    );
            result = postRepository.search(
                    boardType,
                    category,
                    normalizedKeyword,
                    PostStatus.ACTIVE,
                    PostCategory.NOTICE,
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

    @Transactional(readOnly = true)
    public PostPageResponse getBestPostPage(
            int page,
            int size,
            Long currentAccountId
    ) {
        validatePage(page, size);
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        BoardType readableBoardType = accessPolicy.isApprovedBusiness(currentAccount)
                ? null
                : BoardType.GENERAL;
        Page<Post> result = postRepository.findBestPostPage(
                readableBoardType,
                PostCategory.NOTICE,
                LocalDateTime.now().minus(BEST_COMMUNITY_WINDOW),
                BEST_COMMUNITY_MINIMUM_LIKE_COUNT,
                PostStatus.ACTIVE,
                PageRequest.of(page, size)
        );

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
    public List<PostListItemResponse> getRelatedPosts(
            Long postId,
            int size,
            Long currentAccountId
    ) {
        validateDiscoverySize(size);
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        Post currentPost = getExistingPost(postId);
        accessPolicy.assertCanRead(currentPost.getBoardType(), currentAccount);

        BoardType readableBoardType = accessPolicy.isApprovedBusiness(currentAccount)
                ? null
                : BoardType.GENERAL;
        List<Post> posts = postRepository.findRelatedPosts(
                currentPost.getPostId(),
                currentPost.getRestaurantId(),
                currentPost.getCategory(),
                PostCategory.NOTICE,
                readableBoardType,
                PostStatus.ACTIVE,
                PageRequest.of(0, size)
        );
        return responseMapper.toListItems(posts, currentAccount);
    }

    @Transactional(readOnly = true)
    public List<PostListItemResponse> getUnansweredPosts(
            String boardTypeValue,
            int size,
            Long currentAccountId
    ) {
        validateDiscoverySize(size);
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        BoardType boardType = accessPolicy.resolveReadableBoardType(
                parseBoardType(boardTypeValue),
                currentAccount
        );
        List<Post> posts = postRepository.findUnansweredPosts(
                boardType,
                PostCategory.QUESTION,
                PostStatus.ACTIVE,
                CommentStatus.ACTIVE,
                PageRequest.of(0, size)
        );
        return responseMapper.toListItems(posts, currentAccount);
    }

    @Transactional(readOnly = true)
    public AuthorSummaryResponse getAuthorSummary(
            Long authorAccountId,
            Long excludePostId,
            Long currentAccountId
    ) {
        validateId(authorAccountId, "작성자 계정");
        if (excludePostId != null) {
            validateId(excludePostId, "제외 게시글");
        }
        Account author = boardUserService.findOptional(authorAccountId);
        if (author == null) {
            throw new BoardException(
                    HttpStatus.NOT_FOUND,
                    "BOARD_AUTHOR_NOT_FOUND",
                    "작성자 정보를 찾을 수 없습니다."
            );
        }
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        BoardType readableBoardType = accessPolicy.isApprovedBusiness(currentAccount)
                ? null
                : BoardType.GENERAL;
        List<AuthorRecentPostResponse> recentPosts = postRepository
                .findRecentActivePostsByAuthor(
                        authorAccountId,
                        PostStatus.ACTIVE,
                        readableBoardType,
                        excludePostId,
                        PageRequest.of(0, MAX_AUTHOR_RECENT_POSTS)
                )
                .stream()
                .map(post -> new AuthorRecentPostResponse(
                        post.getPostId(),
                        post.getTitle(),
                        post.getBoardType(),
                        post.getCategory(),
                        post.getCreatedAt()
                ))
                .toList();

        return new AuthorSummaryResponse(
                author.getAccountId(),
                author.getNickname(),
                author.getLoginId() == null
                        ? "소셜 계정"
                        : "@" + author.getLoginId(),
                postRepository.countActivePostsByAuthor(
                        authorAccountId,
                        PostStatus.ACTIVE,
                        readableBoardType
                ),
                commentRepository.countActiveCommentsByAuthor(
                        authorAccountId,
                        CommentStatus.ACTIVE,
                        PostStatus.ACTIVE,
                        readableBoardType
                ),
                recentPosts
        );
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
        Account currentAccount = boardUserService.require(currentAccountId);
        String title = request.title().strip();
        String content = request.content().strip();
        accessPolicy.assertCanWrite(
                request.boardType(),
                request.category(),
                request.restaurantId(),
                currentAccount
        );
        PostSubmissionKey submissionKey = new PostSubmissionKey(
                currentAccount.getAccountId(),
                content
        );
        long checkedAt = System.nanoTime();
        if (!reservePostSubmission(submissionKey, checkedAt)) {
            throw rapidDuplicatePost();
        }

        try {
            assertNotRapidDuplicate(currentAccount.getAccountId(), content);
            String authorRole = accessPolicy.displayRole(currentAccount);

            Post post = Post.create(
                    currentAccount,
                    request.restaurantId(),
                    request.boardType(),
                    request.category(),
                    title,
                    content
            );
            postRepository.save(post);
            PostDetailResponse response = responseMapper.toDetail(
                    post,
                    currentAccount,
                    authorRole
            );
            completePostSubmissionAfterTransaction(submissionKey);
            return response;
        } catch (RuntimeException | Error exception) {
            releasePostSubmission(submissionKey);
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
        postRepository.delete(post);
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

    private void validateDiscoverySize(int size) {
        if (size < 1 || size > MAX_DISCOVERY_SIZE) {
            throw badRequest(
                    "탐색 게시글 개수는 1~" + MAX_DISCOVERY_SIZE
                            + " 사이여야 합니다."
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

    private void assertNotRapidDuplicate(Long accountId, String content) {
        LocalDateTime createdAfter = LocalDateTime.now()
                .minus(RAPID_DUPLICATE_WINDOW);
        if (postRepository.existsByAuthorAccountIdAndContentAndCreatedAtAfter(
                accountId,
                content,
                createdAfter
        )) {
            throw rapidDuplicatePost();
        }
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
                    || checkedAt - previous < RAPID_DUPLICATE_WINDOW_NANOS) {
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

    private void completePostSubmissionAfterTransaction(
            PostSubmissionKey submissionKey
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager
                .isActualTransactionActive()) {
            completePostSubmission(submissionKey);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            completePostSubmission(submissionKey);
                        } else {
                            releasePostSubmission(submissionKey);
                        }
                    }
                }
        );
    }

    private void completePostSubmission(PostSubmissionKey submissionKey) {
        recentPostSubmissions.replace(
                submissionKey,
                SUBMISSION_IN_PROGRESS,
                System.nanoTime()
        );
    }

    private void releasePostSubmission(PostSubmissionKey submissionKey) {
        recentPostSubmissions.remove(
                submissionKey,
                SUBMISSION_IN_PROGRESS
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
                    >= RAPID_DUPLICATE_WINDOW_NANOS) {
                recentPostSubmissions.remove(submissionKey, completedAt);
            }
        });
    }

    private BoardException rapidDuplicatePost() {
        return new BoardException(
                HttpStatus.TOO_MANY_REQUESTS,
                "BOARD_RAPID_DUPLICATE",
                "같은 내용을 연달아 등록할 수 없습니다. 잠시 후 다시 시도해 주세요."
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
            String content
    ) {
    }

    public record AuthorSummaryResponse(
            Long accountId,
            String nickname,
            String accountLabel,
            long postCount,
            long commentCount,
            List<AuthorRecentPostResponse> recentPosts
    ) {
        public AuthorSummaryResponse {
            recentPosts = List.copyOf(recentPosts);
        }
    }

    public record AuthorRecentPostResponse(
            Long postId,
            String title,
            BoardType boardType,
            PostCategory category,
            LocalDateTime createdAt
    ) {
    }
}
