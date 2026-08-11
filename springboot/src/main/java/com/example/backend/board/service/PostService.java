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
import com.example.backend.board.query.BoardReferenceQueryRepository;
import com.example.backend.board.query.BoardReferenceQueryRepository.PostMediaFileReference;
import com.example.backend.board.repository.CommentRepository;
import com.example.backend.board.repository.PostLikeRepository;
import com.example.backend.board.repository.PostRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PostService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PostService.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_BEST_SIZE = 10;
    private static final int MIN_POPULAR_SIZE = 10;
    private static final int MAX_POPULAR_SIZE = 20;
    private static final int MAX_DISCOVERY_SIZE = 10;
    private static final int MAX_AUTHOR_RECENT_POSTS = 5;
    private static final int MAX_AUTHOR_RECENT_COMMENTS = 5;
    private static final int MAX_NEWS_TITLE_LENGTH = 200;
    private static final int MAX_NEWS_CONTENT_LENGTH = 10_000;
    private static final int BEST_COMMUNITY_MINIMUM_LIKE_COUNT = 3;
    private static final Duration RAPID_DUPLICATE_WINDOW =
            Duration.ofMillis(3_500);
    private static final long RAPID_DUPLICATE_WINDOW_NANOS =
            RAPID_DUPLICATE_WINDOW.toNanos();
    private static final long SUBMISSION_IN_PROGRESS = Long.MAX_VALUE;
    private static final int SUBMISSION_CLEANUP_INTERVAL = 256;
    private static final int MAX_MEDIA_COUNT = 10;
    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024;
    private static final int MAX_VIDEO_BYTES = 100 * 1024 * 1024;
    private static final int VIDEO_PROCESSING_QUEUE_CAPACITY = 16;
    private static final int VIDEO_PROCESSING_SLOT_CAPACITY =
            VIDEO_PROCESSING_QUEUE_CAPACITY + 1;
    private static final int INITIAL_MEDIA_RANGE_BYTES = 8 * 1024 * 1024;
    private static final int STREAM_MEDIA_RANGE_BYTES = 8 * 1024 * 1024;
    private static final int SUFFIX_MEDIA_RANGE_BYTES = 4 * 1024 * 1024;
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp",
            "tif", "tiff", "avif", "heic", "heif"
    );
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "webm", "ogv", "m4v", "mov", "mkv",
            "avi", "wmv", "flv", "mpg", "mpeg", "3gp", "3g2"
    );
    private static final Map<String, String> MEDIA_MIME_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("tif", "image/tiff"),
            Map.entry("tiff", "image/tiff"),
            Map.entry("avif", "image/avif"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("ogv", "video/ogg"),
            Map.entry("m4v", "video/x-m4v"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("mkv", "video/x-matroska"),
            Map.entry("avi", "video/x-msvideo"),
            Map.entry("wmv", "video/x-ms-wmv"),
            Map.entry("flv", "video/x-flv"),
            Map.entry("mpg", "video/mpeg"),
            Map.entry("mpeg", "video/mpeg"),
            Map.entry("3gp", "video/3gpp"),
            Map.entry("3g2", "video/3gpp2")
    );

    private final ConcurrentMap<PostSubmissionKey, Long>
            recentPostSubmissions = new ConcurrentHashMap<>();
    private final AtomicInteger postSubmissionChecks = new AtomicInteger();
    private final ConcurrentMap<Long, Path> activeVideoProcessingSources =
            new ConcurrentHashMap<>();
    private final Semaphore videoProcessingSlots =
            new Semaphore(VIDEO_PROCESSING_SLOT_CAPACITY);
    private final ExecutorService videoProcessingExecutor =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(
                            VIDEO_PROCESSING_QUEUE_CAPACITY
                    ),
                    runnable -> {
                        Thread thread = new Thread(
                                runnable,
                                "board-video-processor"
                        );
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy()
            );

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final BoardUserService boardUserService;
    private final BoardAccessPolicy accessPolicy;
    private final BoardResponseMapper responseMapper;
    private final BoardReferenceQueryRepository referenceRepository;

    @Value("${board.best-window-days:30}")
    private int bestWindowDays = 30;

    public PostService(
            PostRepository postRepository,
            CommentRepository commentRepository,
            PostLikeRepository postLikeRepository,
            BoardUserService boardUserService,
            BoardAccessPolicy accessPolicy,
            BoardResponseMapper responseMapper,
            BoardReferenceQueryRepository referenceRepository
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.boardUserService = boardUserService;
        this.accessPolicy = accessPolicy;
        this.responseMapper = responseMapper;
        this.referenceRepository = referenceRepository;
    }

    @PreDestroy
    public void stopVideoProcessor() {
        videoProcessingExecutor.shutdownNow();
        try {
            if (!videoProcessingExecutor.awaitTermination(
                    5,
                    TimeUnit.SECONDS
            )) {
                LOGGER.warn("게시판 동영상 처리 작업이 종료되지 않았습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        Map<Long, Path> interruptedMedia = Map.copyOf(
                activeVideoProcessingSources
        );
        interruptedMedia.keySet().forEach(this::markStoppedVideoAsFailed);
        interruptedMedia.values().forEach(this::deleteStagedVideoQuietly);
        activeVideoProcessingSources.clear();
    }

    @Transactional(readOnly = true)
    public boolean canUseBusinessBoard(Long currentAccountId) {
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        return accessPolicy.isApprovedBusiness(currentAccount);
    }

    @Transactional(readOnly = true)
    public RestaurantNewsPageResponse getPublicRestaurantNews(
            Long publicRestaurantId,
            int page,
            int size
    ) {
        validatePage(page, size);
        validateId(publicRestaurantId, "공공데이터 음식점");
        if (!referenceRepository.publicRestaurantExists(publicRestaurantId)) {
            throw publicRestaurantNotFound();
        }

        Page<Post> result = postRepository.findPublicRestaurantNews(
                publicRestaurantId,
                BoardType.GENERAL,
                PostCategory.NEWS,
                PostStatus.ACTIVE,
                PageRequest.of(page, size)
        );
        return toRestaurantNewsPage(result);
    }

    @Transactional
    public RestaurantNewsItemResponse createPublicRestaurantNews(
            Long publicRestaurantId,
            String title,
            String content,
            Long currentAccountId
    ) {
        Account currentAccount = boardUserService.require(currentAccountId);
        accessPolicy.assertCanWritePublicRestaurantNews(
                publicRestaurantId,
                currentAccount
        );
        return createRestaurantNews(
                currentAccount,
                null,
                publicRestaurantId,
                title,
                content
        );
    }

    @Transactional(readOnly = true)
    public RestaurantNewsPageResponse getOwnedRestaurantNews(
            Long restaurantId,
            int page,
            int size
    ) {
        validatePage(page, size);
        validateId(restaurantId, "음식점");
        if (!referenceRepository.restaurantExists(restaurantId)) {
            throw restaurantNotFound();
        }

        Page<Post> result = postRepository.findOwnedRestaurantNews(
                restaurantId,
                BoardType.GENERAL,
                PostCategory.NEWS,
                PostStatus.ACTIVE,
                PageRequest.of(page, size)
        );
        return toRestaurantNewsPage(result);
    }

    @Transactional
    public RestaurantNewsItemResponse createOwnedRestaurantNews(
            Long restaurantId,
            String title,
            String content,
            Long currentAccountId
    ) {
        Account currentAccount = boardUserService.require(currentAccountId);
        accessPolicy.assertCanWriteOwnedRestaurantNews(
                restaurantId,
                currentAccount
        );
        return createRestaurantNews(
                currentAccount,
                restaurantId,
                null,
                title,
                content
        );
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
        if (category == PostCategory.NEWS) {
            throw new BoardException(
                    HttpStatus.BAD_REQUEST,
                    "BOARD_NEWS_DEDICATED_ENDPOINT_REQUIRED",
                    "식당 소식은 식당 소식 전용 API를 이용해 주세요."
            );
        }
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
    public List<PostListItemResponse> getPopularPosts(
            int size,
            Long currentAccountId
    ) {
        if (size < MIN_POPULAR_SIZE || size > MAX_POPULAR_SIZE) {
            throw badRequest(
                    "인기 이야기 개수는 " + MIN_POPULAR_SIZE + "~"
                            + MAX_POPULAR_SIZE + " 사이여야 합니다."
            );
        }
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        BoardType readableBoardType = accessPolicy.isApprovedBusiness(currentAccount)
                ? null
                : BoardType.GENERAL;
        int safeWindowDays = Math.max(1, Math.min(bestWindowDays, 365));
        List<Post> posts = postRepository.findBestPosts(
                readableBoardType,
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
        List<AuthorRecentCommentResponse> recentComments = commentRepository
                .findRecentActiveCommentsByAuthor(
                        authorAccountId,
                        CommentStatus.ACTIVE,
                        PostStatus.ACTIVE,
                        readableBoardType,
                        excludePostId,
                        PageRequest.of(0, MAX_AUTHOR_RECENT_COMMENTS)
                )
                .stream()
                .map(comment -> new AuthorRecentCommentResponse(
                        comment.getCommentId(),
                        comment.getPost().getPostId(),
                        comment.getPost().getTitle(),
                        comment.getContent(),
                        comment.getCreatedAt()
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
                recentPosts,
                recentComments
        );
    }

    @Transactional(readOnly = true)
    public PostDetailResponse getPost(Long postId, Long currentAccountId) {
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        Post post = getExistingPost(postId);
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);

        long viewCount = referenceRepository.increasePostViewCountImmediately(postId);
        if (viewCount < 0) {
            throw notFound("게시글을 찾을 수 없습니다.");
        }
        return responseMapper.toDetail(post, currentAccount, viewCount);
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
    public PostDetailResponse.MediaResponse uploadMedia(
            Long postId,
            String encodedOriginalName,
            String declaredContentType,
            byte[] mediaData,
            Long currentAccountId
    ) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Post post = getExistingPostForUpdate(postId);
        assertOwnerOrAdmin(post, currentAccount);

        if (mediaData == null || mediaData.length == 0) {
            throw badRequest("비어 있는 파일은 첨부할 수 없습니다.");
        }
        if (referenceRepository.countPostMedia(postId) >= MAX_MEDIA_COUNT) {
            throw badRequest(
                    "게시글에는 사진과 동영상을 합해 최대 "
                            + MAX_MEDIA_COUNT + "개까지 첨부할 수 있습니다."
            );
        }

        String originalName = normalizeOriginalName(encodedOriginalName);
        MediaTypeInfo mediaType = detectMediaType(
                originalName,
                declaredContentType,
                mediaData
        );
        if (mediaType.image() && mediaData.length > MAX_IMAGE_BYTES) {
            throw badRequest("사진은 한 파일당 20MB 이하만 첨부할 수 있습니다.");
        }
        if (!mediaType.image() && mediaData.length > MAX_VIDEO_BYTES) {
            throw badRequest("동영상은 한 파일당 100MB 이하만 첨부할 수 있습니다.");
        }

        int displayOrder = referenceRepository.nextPostMediaDisplayOrder(postId);
        Long postMediaId;
        if (mediaType.image()) {
            postMediaId = referenceRepository.savePostMedia(
                    postId,
                    mediaType.databaseType(),
                    mediaType.mimeType(),
                    originalName,
                    mediaData.length,
                    displayOrder,
                    mediaData
            );
        } else {
            if (!videoProcessingSlots.tryAcquire()) {
                throw new BoardException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "BOARD_VIDEO_PROCESSING_BUSY",
                        "동영상 처리 요청이 많습니다. 잠시 후 다시 시도해 주세요."
                );
            }
            Path stagedVideo = null;
            Long processingMediaId = null;
            try {
                stagedVideo = stageVideoMedia(mediaData);
                processingMediaId =
                        referenceRepository.createProcessingPostMedia(
                                postId,
                                mediaType.databaseType(),
                                mediaType.mimeType(),
                                originalName,
                                mediaData.length,
                                displayOrder
                        );
                postMediaId = processingMediaId;
                scheduleVideoProcessingAfterTransaction(
                        postMediaId,
                        mediaData.length,
                        stagedVideo
                );
            } catch (RuntimeException exception) {
                if (processingMediaId != null) {
                    activeVideoProcessingSources.remove(
                            processingMediaId,
                            stagedVideo
                    );
                }
                deleteStagedVideoQuietly(stagedVideo);
                videoProcessingSlots.release();
                throw exception;
            }
        }

        return new PostDetailResponse.MediaResponse(
                postMediaId,
                mediaType.databaseType(),
                mediaType.image()
                        ? "/api/board/posts/media/" + postMediaId
                        : null,
                mediaType.mimeType(),
                originalName,
                mediaData.length,
                displayOrder,
                mediaType.image() ? "READY" : "PROCESSING",
                mediaType.image() ? 100 : 0,
                mediaType.image()
                        ? null
                        : "동영상 원본을 서버에서 처리하고 있습니다."
        );
    }

    @Transactional(readOnly = true)
    public List<PostDetailResponse.MediaResponse> getMediaStatus(
            Long postId,
            Long currentAccountId
    ) {
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        Post post = getExistingPost(postId);
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);

        return responseMapper.toMediaResponses(
                referenceRepository.findPostMedia(postId)
        );
    }

    private Path stageVideoMedia(byte[] mediaData) {
        Path stagedVideo = null;
        try {
            stagedVideo = Files.createTempFile(
                    "fooduck-board-video-",
                    ".upload"
            );
            Files.write(stagedVideo, mediaData);
            if (Files.size(stagedVideo) != mediaData.length) {
                throw new IOException("임시 동영상 크기가 일치하지 않습니다.");
            }
            return stagedVideo;
        } catch (IOException exception) {
            deleteStagedVideoQuietly(stagedVideo);
            throw new BoardException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BOARD_MEDIA_STAGING_FAILED",
                    "동영상을 서버 처리용 파일로 준비하지 못했습니다."
            );
        }
    }

    private void scheduleVideoProcessingAfterTransaction(
            Long postMediaId,
            long fileSize,
            Path stagedVideo
    ) {
        activeVideoProcessingSources.put(postMediaId, stagedVideo);
        Runnable submitProcessing = () -> {
            try {
                videoProcessingExecutor.execute(() ->
                        processVideoMedia(
                                postMediaId,
                                fileSize,
                                stagedVideo
                        )
                );
            } catch (RuntimeException exception) {
                activeVideoProcessingSources.remove(
                        postMediaId,
                        stagedVideo
                );
                deleteStagedVideoQuietly(stagedVideo);
                markVideoProcessingFailed(postMediaId, exception);
                videoProcessingSlots.release();
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager
                .isActualTransactionActive()) {
            submitProcessing.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        submitProcessing.run();
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            activeVideoProcessingSources.remove(
                                    postMediaId,
                                    stagedVideo
                            );
                            deleteStagedVideoQuietly(stagedVideo);
                            videoProcessingSlots.release();
                        }
                    }
                }
        );
    }

    private void processVideoMedia(
            Long postMediaId,
            long fileSize,
            Path stagedVideo
    ) {
        try (InputStream inputStream = Files.newInputStream(stagedVideo)) {
            referenceRepository.storePostMediaData(
                    postMediaId,
                    fileSize,
                    inputStream
            );
        } catch (IOException | RuntimeException exception) {
            markVideoProcessingFailed(postMediaId, exception);
        } finally {
            activeVideoProcessingSources.remove(postMediaId, stagedVideo);
            deleteStagedVideoQuietly(stagedVideo);
            videoProcessingSlots.release();
        }
    }

    private void markVideoProcessingFailed(
            Long postMediaId,
            Throwable cause
    ) {
        LOGGER.error(
                "게시판 동영상 처리에 실패했습니다. postMediaId={}",
                postMediaId,
                cause
        );
        try {
            referenceRepository.markPostMediaFailed(postMediaId);
        } catch (RuntimeException updateException) {
            LOGGER.error(
                    "게시판 동영상 실패 상태를 저장하지 못했습니다. postMediaId={}",
                    postMediaId,
                    updateException
            );
        }
    }

    private void markStoppedVideoAsFailed(Long postMediaId) {
        try {
            referenceRepository.markPostMediaFailed(postMediaId);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "종료 중인 동영상의 실패 상태를 저장하지 못했습니다. "
                            + "postMediaId={}",
                    postMediaId,
                    exception
            );
        }
    }

    private void deleteStagedVideoQuietly(Path stagedVideo) {
        if (stagedVideo == null) {
            return;
        }
        try {
            Files.deleteIfExists(stagedVideo);
        } catch (IOException exception) {
            LOGGER.warn(
                    "게시판 동영상 임시 파일을 삭제하지 못했습니다. path={}",
                    stagedVideo,
                    exception
            );
        }
    }

    @Transactional
    public void deleteMedia(
            Long postId,
            Long postMediaId,
            Long currentAccountId
    ) {
        validateId(postMediaId, "첨부파일");
        Account currentAccount = boardUserService.require(currentAccountId);
        Post post = getExistingPostForUpdate(postId);
        assertOwnerOrAdmin(post, currentAccount);

        PostMediaFileReference media = referenceRepository
                .findPostMediaFile(postMediaId)
                .orElseThrow(() -> notFound("첨부파일을 찾을 수 없습니다."));
        if (!postId.equals(media.postId())) {
            throw notFound("첨부파일을 찾을 수 없습니다.");
        }
        if (referenceRepository.deletePostMedia(postId, postMediaId) != 1) {
            throw notFound("첨부파일을 찾을 수 없습니다.");
        }
    }

    @Transactional(readOnly = true)
    public MediaDownload getMedia(
            Long postMediaId,
            String rangeHeader,
            boolean download,
            Long currentAccountId
    ) {
        validateId(postMediaId, "첨부파일");
        PostMediaFileReference media = referenceRepository
                .findPostMediaFile(postMediaId)
                .orElseThrow(() -> notFound("첨부파일을 찾을 수 없습니다."));

        Account currentAccount = boardUserService.findOptional(currentAccountId);
        Post post = getExistingPost(media.postId());
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);

        if (BoardReferenceQueryRepository.MEDIA_URL_PROCESSING.equals(
                media.mediaUrl()
        )) {
            throw new BoardException(
                    HttpStatus.CONFLICT,
                    "BOARD_MEDIA_PROCESSING",
                    "동영상이 아직 서버에서 처리 중입니다."
            );
        }
        if (BoardReferenceQueryRepository.MEDIA_URL_FAILED.equals(
                media.mediaUrl()
        )) {
            throw new BoardException(
                    HttpStatus.CONFLICT,
                    "BOARD_MEDIA_PROCESSING_FAILED",
                    "동영상 처리에 실패했습니다."
            );
        }

        if (media.fileSize() < 1) {
            throw notFound("첨부파일 데이터가 없습니다.");
        }

        ByteRange range = resolveByteRange(rangeHeader, media.fileSize());
        int requestedLength = Math.toIntExact(range.end() - range.start() + 1);
        byte[] bytes = referenceRepository.readPostMediaBytes(
                postMediaId,
                range.start(),
                requestedLength
        );
        if (bytes == null || bytes.length == 0) {
            throw notFound("첨부파일 데이터가 없습니다.");
        }

        long actualEnd = range.start() + bytes.length - 1;
        return new MediaDownload(
                media.postMediaId(),
                media.mimeType(),
                media.originalName(),
                media.fileSize(),
                range.start(),
                actualEnd,
                range.partial(),
                download,
                bytes
        );
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

    private RestaurantNewsItemResponse createRestaurantNews(
            Account currentAccount,
            Long restaurantId,
            Long publicRestaurantId,
            String title,
            String content
    ) {
        String normalizedTitle = normalizeNewsTitle(title);
        String normalizedContent = normalizeNewsContent(content);
        PostSubmissionKey submissionKey = new PostSubmissionKey(
                currentAccount.getAccountId(),
                normalizedContent
        );
        long checkedAt = System.nanoTime();
        if (!reservePostSubmission(submissionKey, checkedAt)) {
            throw rapidDuplicatePost();
        }

        try {
            assertNotRapidDuplicate(
                    currentAccount.getAccountId(),
                    normalizedContent
            );
            Post post = Post.create(
                    currentAccount,
                    restaurantId,
                    publicRestaurantId,
                    BoardType.GENERAL,
                    PostCategory.NEWS,
                    normalizedTitle,
                    normalizedContent
            );
            postRepository.save(post);
            RestaurantNewsItemResponse response = toRestaurantNewsItem(post);
            completePostSubmissionAfterTransaction(submissionKey);
            return response;
        } catch (RuntimeException | Error exception) {
            releasePostSubmission(submissionKey);
            throw exception;
        }
    }

    private RestaurantNewsPageResponse toRestaurantNewsPage(Page<Post> page) {
        return new RestaurantNewsPageResponse(
                page.getContent().stream()
                        .map(this::toRestaurantNewsItem)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    private RestaurantNewsItemResponse toRestaurantNewsItem(Post post) {
        return new RestaurantNewsItemResponse(
                post.getPostId(),
                post.getTitle(),
                post.getContent(),
                post.getCreatedAt()
        );
    }

    private String normalizeNewsTitle(String title) {
        String normalized = title == null ? "" : title.strip();
        if (normalized.isBlank()) {
            throw badRequest("제목을 입력해 주세요.");
        }
        if (normalized.length() > MAX_NEWS_TITLE_LENGTH) {
            throw badRequest(
                    "제목은 " + MAX_NEWS_TITLE_LENGTH + "자 이하로 입력해 주세요."
            );
        }
        return normalized;
    }

    private String normalizeNewsContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isBlank()) {
            throw badRequest("내용을 입력해 주세요.");
        }
        if (normalized.length() > MAX_NEWS_CONTENT_LENGTH) {
            throw badRequest(
                    "내용은 " + MAX_NEWS_CONTENT_LENGTH + "자 이하로 입력해 주세요."
            );
        }
        return normalized;
    }

    private Post getExistingPost(Long postId) {
        validateId(postId, "게시글");
        Post post = postRepository.findByPostIdAndStatus(
                        postId,
                        PostStatus.ACTIVE
                )
                .orElseThrow(() -> notFound("게시글을 찾을 수 없습니다."));
        if (post.getCategory() == PostCategory.NEWS) {
            throw notFound("게시글을 찾을 수 없습니다.");
        }
        return post;
    }

    private Post getExistingPostForUpdate(Long postId) {
        validateId(postId, "게시글");
        Post post = postRepository.findByPostIdAndStatusForUpdate(
                        postId,
                        PostStatus.ACTIVE
                )
                .orElseThrow(() -> notFound("게시글을 찾을 수 없습니다."));
        if (post.getCategory() == PostCategory.NEWS) {
            throw notFound("게시글을 찾을 수 없습니다.");
        }
        return post;
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

    private String normalizeOriginalName(String encodedOriginalName) {
        if (encodedOriginalName == null || encodedOriginalName.isBlank()) {
            throw badRequest("첨부파일 이름이 없습니다.");
        }

        String decoded;
        try {
            decoded = URLDecoder.decode(
                    encodedOriginalName,
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest("첨부파일 이름 형식이 올바르지 않습니다.");
        }

        decoded = decoded.replace('\\', '/');
        int separator = decoded.lastIndexOf('/');
        String fileName = separator >= 0
                ? decoded.substring(separator + 1)
                : decoded;
        fileName = fileName
                .replaceAll("[\\p{Cntrl}]", "_")
                .strip();

        if (fileName.isBlank() || ".".equals(fileName) || "..".equals(fileName)) {
            throw badRequest("첨부파일 이름이 올바르지 않습니다.");
        }
        if (fileName.length() > MAX_ORIGINAL_NAME_LENGTH) {
            fileName = fileName.substring(0, MAX_ORIGINAL_NAME_LENGTH);
        }
        return fileName;
    }

    private MediaTypeInfo detectMediaType(
            String originalName,
            String declaredContentType,
            byte[] data
    ) {
        String extension = findExtension(originalName);
        String mimeType = MEDIA_MIME_TYPES.get(extension);
        if (mimeType == null) {
            throw badRequest(
                    "지원하지 않는 파일 형식입니다. 사진 또는 동영상 파일을 선택해 주세요."
            );
        }

        boolean image = IMAGE_EXTENSIONS.contains(extension);
        if (!image && !VIDEO_EXTENSIONS.contains(extension)) {
            throw badRequest("지원하지 않는 첨부파일 형식입니다.");
        }
        if (!matchesFileSignature(extension, data)) {
            throw badRequest(
                    "파일 확장자와 실제 파일 형식이 일치하지 않습니다."
            );
        }

        String normalizedDeclaredType = normalizeContentType(declaredContentType);
        if (normalizedDeclaredType != null
                && !"application/octet-stream".equals(normalizedDeclaredType)) {
            String expectedPrefix = image ? "image/" : "video/";
            if (!normalizedDeclaredType.startsWith(expectedPrefix)) {
                throw badRequest(
                        "파일의 Content-Type과 실제 미디어 형식이 일치하지 않습니다."
                );
            }
        }

        return new MediaTypeInfo(
                image,
                image ? "IMAGE" : "VIDEO_LINK",
                mimeType
        );
    }

    private String findExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 1 || dot == fileName.length() - 1) {
            throw badRequest("첨부파일 확장자를 확인해 주세요.");
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int separator = contentType.indexOf(';');
        String normalized = separator >= 0
                ? contentType.substring(0, separator)
                : contentType;
        return normalized.strip().toLowerCase(Locale.ROOT);
    }

    private boolean matchesFileSignature(String extension, byte[] data) {
        return switch (extension) {
            case "jpg", "jpeg" -> startsWith(data, 0xff, 0xd8, 0xff);
            case "png" -> startsWith(
                    data, 0x89, 0x50, 0x4e, 0x47,
                    0x0d, 0x0a, 0x1a, 0x0a
            );
            case "gif" -> asciiEquals(data, 0, "GIF87a")
                    || asciiEquals(data, 0, "GIF89a");
            case "webp" -> asciiEquals(data, 0, "RIFF")
                    && asciiEquals(data, 8, "WEBP");
            case "bmp" -> asciiEquals(data, 0, "BM");
            case "tif", "tiff" -> startsWith(data, 0x49, 0x49, 0x2a, 0x00)
                    || startsWith(data, 0x4d, 0x4d, 0x00, 0x2a);
            case "avif" -> isIsoBaseMediaFile(data)
                    && containsIsoBrand(data, "avif", "avis");
            case "heic", "heif" -> isIsoBaseMediaFile(data)
                    && containsIsoBrand(
                            data,
                            "heic", "heix", "hevc", "hevx",
                            "heim", "heis", "mif1", "msf1"
                    );
            case "mp4", "m4v", "3gp", "3g2" ->
                    isIsoBaseMediaFile(data) && !isIsoImageFile(data);
            case "mov" -> (isIsoBaseMediaFile(data) && !isIsoImageFile(data))
                    || asciiEquals(data, 4, "moov")
                    || asciiEquals(data, 4, "mdat");
            case "webm", "mkv" ->
                    startsWith(data, 0x1a, 0x45, 0xdf, 0xa3);
            case "avi" -> asciiEquals(data, 0, "RIFF")
                    && asciiEquals(data, 8, "AVI ");
            case "ogv" -> asciiEquals(data, 0, "OggS");
            case "wmv" -> startsWith(
                    data,
                    0x30, 0x26, 0xb2, 0x75,
                    0x8e, 0x66, 0xcf, 0x11,
                    0xa6, 0xd9, 0x00, 0xaa,
                    0x00, 0x62, 0xce, 0x6c
            );
            case "flv" -> asciiEquals(data, 0, "FLV");
            case "mpg", "mpeg" ->
                    startsWith(data, 0x00, 0x00, 0x01, 0xba)
                            || startsWith(data, 0x00, 0x00, 0x01, 0xb3);
            default -> false;
        };
    }

    private boolean isIsoBaseMediaFile(byte[] data) {
        return asciiEquals(data, 4, "ftyp");
    }

    private boolean isIsoImageFile(byte[] data) {
        return containsIsoBrand(
                data,
                "avif", "avis",
                "heic", "heix", "hevc", "hevx",
                "heim", "heis", "mif1", "msf1"
        );
    }

    private boolean containsIsoBrand(byte[] data, String... brands) {
        int maximum = Math.min(data.length, 64);
        for (String brand : brands) {
            byte[] expected = brand.getBytes(StandardCharsets.US_ASCII);
            for (int offset = 8; offset + expected.length <= maximum; offset++) {
                boolean matched = true;
                for (int index = 0; index < expected.length; index++) {
                    if (data[offset + index] != expected[index]) {
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean asciiEquals(byte[] data, int offset, String expected) {
        byte[] bytes = expected.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || data.length < offset + bytes.length) {
            return false;
        }
        for (int index = 0; index < bytes.length; index++) {
            if (data[offset + index] != bytes[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if ((data[index] & 0xff) != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private ByteRange resolveByteRange(String rangeHeader, long totalSize) {
        if (rangeHeader == null || rangeHeader.isBlank()) {
            return new ByteRange(0, totalSize - 1, false);
        }
        if (!rangeHeader.startsWith("bytes=") || rangeHeader.contains(",")) {
            throw rangeNotSatisfiable();
        }

        String value = rangeHeader.substring("bytes=".length()).strip();
        int dash = value.indexOf('-');
        if (dash < 0) {
            throw rangeNotSatisfiable();
        }

        String startValue = value.substring(0, dash).strip();
        String endValue = value.substring(dash + 1).strip();
        try {
            long start;
            long end;
            if (startValue.isEmpty()) {
                long suffixLength = Long.parseLong(endValue);
                if (suffixLength < 1) {
                    throw rangeNotSatisfiable();
                }
                suffixLength = Math.min(
                        suffixLength,
                        SUFFIX_MEDIA_RANGE_BYTES
                );
                start = Math.max(0, totalSize - suffixLength);
                end = totalSize - 1;
            } else {
                start = Long.parseLong(startValue);
                if (start < 0 || start >= totalSize) {
                    throw rangeNotSatisfiable();
                }
                int maximumRangeBytes = start == 0
                        ? INITIAL_MEDIA_RANGE_BYTES
                        : STREAM_MEDIA_RANGE_BYTES;
                long maximumEnd = Math.min(
                        totalSize - 1,
                        start + maximumRangeBytes - 1L
                );
                end = endValue.isEmpty()
                        ? maximumEnd
                        : Math.min(Long.parseLong(endValue), maximumEnd);
                if (end < start) {
                    throw rangeNotSatisfiable();
                }
            }
            return new ByteRange(start, end, true);
        } catch (NumberFormatException exception) {
            throw rangeNotSatisfiable();
        }
    }

    private BoardException rangeNotSatisfiable() {
        return new BoardException(
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "BOARD_MEDIA_RANGE_INVALID",
                "요청한 첨부파일 구간을 제공할 수 없습니다."
        );
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

    private BoardException publicRestaurantNotFound() {
        return new BoardException(
                HttpStatus.NOT_FOUND,
                "BOARD_PUBLIC_RESTAURANT_NOT_FOUND",
                "공공데이터 음식점을 찾을 수 없습니다."
        );
    }

    private BoardException restaurantNotFound() {
        return new BoardException(
                HttpStatus.NOT_FOUND,
                "BOARD_RESTAURANT_NOT_FOUND",
                "음식점을 찾을 수 없습니다."
        );
    }

    private BoardException notFound(String message) {
        return new BoardException(
                HttpStatus.NOT_FOUND,
                "BOARD_POST_NOT_FOUND",
                message
        );
    }

    private record MediaTypeInfo(
            boolean image,
            String databaseType,
            String mimeType
    ) {
    }

    private record ByteRange(
            long start,
            long end,
            boolean partial
    ) {
    }

    public record MediaDownload(
            Long postMediaId,
            String mimeType,
            String originalName,
            long totalSize,
            long start,
            long end,
            boolean partial,
            boolean download,
            byte[] data
    ) {
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
            List<AuthorRecentPostResponse> recentPosts,
            List<AuthorRecentCommentResponse> recentComments
    ) {
        public AuthorSummaryResponse {
            recentPosts = List.copyOf(recentPosts);
            recentComments = List.copyOf(recentComments);
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

    public record AuthorRecentCommentResponse(
            Long commentId,
            Long postId,
            String postTitle,
            String content,
            LocalDateTime createdAt
    ) {
    }

    public record RestaurantNewsItemResponse(
            Long postId,
            String title,
            String content,
            LocalDateTime createdAt
    ) {
    }

    public record RestaurantNewsPageResponse(
            List<RestaurantNewsItemResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
        public RestaurantNewsPageResponse {
            content = List.copyOf(content);
        }
    }
}
