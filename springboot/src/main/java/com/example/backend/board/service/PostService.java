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
import com.example.backend.board.dto.response.PostDetailResponse.MediaResponse;
import com.example.backend.board.dto.response.PostLikeResponse;
import com.example.backend.board.dto.response.PostListItemResponse;
import com.example.backend.board.dto.response.PostPageResponse;
import com.example.backend.board.exception.BoardException;
import com.example.backend.board.mapper.BoardResponseMapper;
import com.example.backend.board.policy.BoardAccessPolicy;
import com.example.backend.board.repository.CommentRepository;
import com.example.backend.board.repository.PostLikeRepository;
import com.example.backend.board.repository.PostRepository;
import com.example.backend.board.repository.PostRepository.PostMediaRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import jakarta.annotation.PreDestroy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PostService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_BEST_SIZE = 10;
    private static final int MAX_DISCOVERY_SIZE = 10;
    private static final int MAX_AUTHOR_RECENT_POSTS = 5;
    private static final int MAX_AUTHOR_RECENT_COMMENTS = 5;
    private static final int BEST_COMMUNITY_MINIMUM_LIKE_COUNT = 3;
    private static final Duration BEST_COMMUNITY_WINDOW = Duration.ofDays(7);
    private static final Duration RAPID_DUPLICATE_WINDOW =
            Duration.ofMillis(3_500);
    private static final long RAPID_DUPLICATE_WINDOW_NANOS =
            RAPID_DUPLICATE_WINDOW.toNanos();
    private static final long SUBMISSION_IN_PROGRESS = Long.MAX_VALUE;
    private static final int SUBMISSION_CLEANUP_INTERVAL = 256;
    private static final int MAX_MEDIA_COUNT = 5;
    private static final long MAX_IMAGE_BYTES = 25L * 1024L * 1024L;
    private static final long MAX_VIDEO_BYTES = 500L * 1024L * 1024L;
    private static final int MEDIA_COPY_BUFFER_SIZE = 64 * 1024;
    private static final String MEDIA_URL_PREFIX = "/api/board/posts/media/";
    private static final String PROCESSING_URL_PREFIX = "processing:";
    private static final String FAILED_URL_PREFIX = "failed:";
    private static final String IMAGE_DATABASE_TYPE = "IMAGE";
    private static final String VIDEO_DATABASE_TYPE = "VIDEO_LINK";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";

    private final ConcurrentMap<PostSubmissionKey, Long>
            recentPostSubmissions = new ConcurrentHashMap<>();
    private final AtomicInteger postSubmissionChecks = new AtomicInteger();
    private final ConcurrentMap<Long, MediaProcessingState>
            mediaProcessingStates = new ConcurrentHashMap<>();
    private final ExecutorService mediaConversionExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "board-webm-converter"
                );
                thread.setDaemon(true);
                return thread;
            });

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final BoardUserService boardUserService;
    private final BoardAccessPolicy accessPolicy;
    private final BoardResponseMapper responseMapper;
    private final Path mediaStorageRoot;
    private final String ffmpegCommand;
    private final String ffprobeCommand;

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
        this.mediaStorageRoot = resolveMediaStorageRoot();
        this.ffmpegCommand = resolveExternalCommand("FFMPEG_PATH", "ffmpeg");
        this.ffprobeCommand = resolveExternalCommand("FFPROBE_PATH", "ffprobe");
    }

    @PreDestroy
    public void stopMediaConverter() {
        mediaProcessingStates.values().forEach(MediaProcessingState::cancel);
        mediaConversionExecutor.shutdownNow();
    }

    @Transactional(readOnly = true)
    public boolean canUseBusinessBoard(Long currentAccountId) {
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        return accessPolicy.isApprovedBusiness(currentAccount);
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
        List<PostMediaRow> mediaRows = postRepository.findMediaRowsByPostId(postId);
        postRepository.delete(post);
        cleanupMediaAfterCommit(mediaRows);
    }

    @Transactional
    public MediaResponse uploadMedia(
            Long postId,
            String contentType,
            String originalFileName,
            long contentLength,
            InputStream inputStream,
            Long currentAccountId
    ) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Post post = getExistingPostForUpdate(postId);
        assertOwnerOrAdmin(post, currentAccount);

        if (postRepository.countMediaByPostId(postId) >= MAX_MEDIA_COUNT) {
            throw badRequest(
                    "사진과 동영상은 게시글당 최대 " + MAX_MEDIA_COUNT
                            + "개까지 등록할 수 있습니다."
            );
        }

        MediaFileType fileType = resolveMediaFileType(
                contentType,
                originalFileName
        );
        if (contentLength == 0) {
            throw badRequest("빈 파일은 등록할 수 없습니다.");
        }
        if (contentLength > fileType.maximumBytes()) {
            throw mediaSizeExceeded(fileType);
        }

        String jobId = UUID.randomUUID().toString();
        Path source = resolveMediaPath(jobId + ".upload");
        writeMediaFile(inputStream, source, fileType.maximumBytes());

        if (!fileType.video()) {
            return convertAndStoreImage(postId, jobId, source);
        }
        return queueVideoConversion(
                postId,
                jobId,
                source,
                fileType.webmInput()
        );
    }

    @Transactional
    public MediaResponse getMediaStatus(
            Long postId,
            Long postMediaId,
            Long currentAccountId
    ) {
        validateId(postMediaId, "미디어");
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        Post post = getExistingPost(postId);
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);
        PostMediaRow row = postRepository.findMediaByIdAndPostId(
                postMediaId,
                postId
        ).orElseThrow(() -> new BoardException(
                HttpStatus.NOT_FOUND,
                "BOARD_MEDIA_NOT_FOUND",
                "사진 또는 동영상을 찾을 수 없습니다."
        ));
        String storedUrl = row.getMediaUrl();
        if (storedUrl != null
                && storedUrl.startsWith(PROCESSING_URL_PREFIX)
                && !mediaProcessingStates.containsKey(postMediaId)) {
            String failedUrl = FAILED_URL_PREFIX
                    + storedUrl.substring(PROCESSING_URL_PREFIX.length());
            postRepository.updateMediaUrl(
                    postMediaId,
                    postId,
                    storedUrl,
                    failedUrl
            );
        }
        return toMediaResponse(row);
    }

    @Transactional
    public void deleteMedia(
            Long postId,
            Long postMediaId,
            Long currentAccountId
    ) {
        validateId(postMediaId, "미디어");
        Account currentAccount = boardUserService.require(currentAccountId);
        Post post = getExistingPostForUpdate(postId);
        assertOwnerOrAdmin(post, currentAccount);

        PostMediaRow media = postRepository.findMediaByIdAndPostId(
                postMediaId,
                postId
        ).orElseThrow(() -> new BoardException(
                HttpStatus.NOT_FOUND,
                "BOARD_MEDIA_NOT_FOUND",
                "삭제할 사진 또는 동영상을 찾을 수 없습니다."
        ));
        if (postRepository.deleteMediaByIdAndPostId(postMediaId, postId) != 1) {
            throw mediaStorageFailure();
        }
        cleanupMediaAfterCommit(List.of(media));
    }

    @Transactional(readOnly = true)
    public MediaResource getMedia(String fileName) {
        String normalizedFileName = normalizeStoredFileName(fileName);
        String mediaUrl = MEDIA_URL_PREFIX + normalizedFileName;
        PostMediaRow media = postRepository.findMediaByUrl(mediaUrl)
                .orElseThrow(() -> new BoardException(
                        HttpStatus.NOT_FOUND,
                        "BOARD_MEDIA_NOT_FOUND",
                        "사진 또는 동영상을 찾을 수 없습니다."
                ));
        Path path = resolveMediaPath(normalizedFileName);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new BoardException(
                    HttpStatus.NOT_FOUND,
                    "BOARD_MEDIA_FILE_NOT_FOUND",
                    "저장된 사진 또는 동영상 파일을 찾을 수 없습니다."
            );
        }
        return new MediaResource(
                new FileSystemResource(path),
                MediaType.parseMediaType(contentTypeFor(normalizedFileName)),
                media.getMediaType()
        );
    }

    private MediaResponse convertAndStoreImage(
            Long postId,
            String jobId,
            Path source
    ) {
        String storedFileName = jobId + ".webp";
        Path target = resolveMediaPath(storedFileName);
        try {
            convertImageToWebp(source, target);
            deleteQuietly(source);
            String mediaUrl = MEDIA_URL_PREFIX + storedFileName;
            int displayOrder = postRepository.findNextMediaDisplayOrder(postId);
            if (postRepository.insertMedia(
                    postId,
                    IMAGE_DATABASE_TYPE,
                    mediaUrl,
                    displayOrder
            ) != 1) {
                throw mediaStorageFailure();
            }
            PostMediaRow row = postRepository.findMediaByUrl(mediaUrl)
                    .orElseThrow(this::mediaStorageFailure);
            deleteFileAfterRollback(target);
            return toMediaResponse(row);
        } catch (RuntimeException exception) {
            deleteQuietly(source);
            deleteQuietly(target);
            throw exception;
        }
    }

    private MediaResponse queueVideoConversion(
            Long postId,
            String jobId,
            Path source,
            boolean webmInput
    ) {
        try {
            String processingUrl = PROCESSING_URL_PREFIX + jobId;
            int displayOrder = postRepository.findNextMediaDisplayOrder(postId);
            if (postRepository.insertMedia(
                    postId,
                    VIDEO_DATABASE_TYPE,
                    processingUrl,
                    displayOrder
            ) != 1) {
                throw mediaStorageFailure();
            }
            PostMediaRow row = postRepository.findMediaByUrl(processingUrl)
                    .orElseThrow(this::mediaStorageFailure);
            Path target = resolveMediaPath(jobId + ".webm");
            MediaProcessingState state = new MediaProcessingState(
                    source,
                    target,
                    STATUS_QUEUED,
                    0,
                    "WebM 변환을 기다리고 있습니다."
            );
            mediaProcessingStates.put(row.getPostMediaId(), state);
            scheduleVideoConversionAfterCommit(
                    row.getPostMediaId(),
                    postId,
                    processingUrl,
                    source,
                    target,
                    webmInput,
                    state
            );
            return toMediaResponse(row);
        } catch (RuntimeException exception) {
            deleteQuietly(source);
            throw exception;
        }
    }

    private void scheduleVideoConversionAfterCommit(
            Long postMediaId,
            Long postId,
            String processingUrl,
            Path source,
            Path target,
            boolean webmInput,
            MediaProcessingState state
    ) {
        Runnable conversion = () -> mediaConversionExecutor.execute(() ->
                runVideoConversion(
                        postMediaId,
                        postId,
                        processingUrl,
                        source,
                        target,
                        webmInput,
                        state
                )
        );
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            conversion.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        conversion.run();
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            mediaProcessingStates.remove(postMediaId);
                            deleteQuietly(source);
                            deleteQuietly(target);
                        }
                    }
                }
        );
    }

    private void runVideoConversion(
            Long postMediaId,
            Long postId,
            String processingUrl,
            Path source,
            Path target,
            boolean webmInput,
            MediaProcessingState state
    ) {
        if (state.cancelled()) {
            deleteQuietly(source);
            return;
        }
        state.update(
                STATUS_PROCESSING,
                1,
                webmInput
                        ? "WebM 영상을 확인하고 있습니다."
                        : "WebM으로 변환 중입니다."
        );
        try {
            boolean converted = webmInput && tryRemuxWebm(source, target, state);
            if (!converted) {
                deleteQuietly(target);
                transcodeVideoToWebm(source, target, state);
            }
            if (state.cancelled()) {
                deleteQuietly(source);
                deleteQuietly(target);
                return;
            }
            requireConvertedFile(target, "동영상");
            String finalUrl = MEDIA_URL_PREFIX + target.getFileName();
            int updated = postRepository.updateMediaUrl(
                    postMediaId,
                    postId,
                    processingUrl,
                    finalUrl
            );
            if (updated != 1) {
                deleteQuietly(target);
                mediaProcessingStates.remove(postMediaId);
                return;
            }
            state.update(STATUS_READY, 100, "WebM 변환이 완료되었습니다.");
            mediaProcessingStates.remove(postMediaId);
        } catch (RuntimeException exception) {
            deleteQuietly(target);
            String failedUrl = FAILED_URL_PREFIX
                    + processingUrl.substring(PROCESSING_URL_PREFIX.length());
            postRepository.updateMediaUrl(
                    postMediaId,
                    postId,
                    processingUrl,
                    failedUrl
            );
            state.update(
                    STATUS_FAILED,
                    state.progress(),
                    exception.getMessage() == null || exception.getMessage().isBlank()
                            ? "동영상 변환에 실패했습니다."
                            : exception.getMessage()
            );
        } finally {
            state.detachProcess();
            deleteQuietly(source);
        }
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

    private Path resolveMediaStorageRoot() {
        String configured = System.getenv("BOARD_MEDIA_DIR");
        String directory = configured == null || configured.isBlank()
                ? Path.of(
                        System.getProperty("user.home"),
                        ".fooduck",
                        "board-media"
                ).toString()
                : configured.strip();
        return Path.of(directory).toAbsolutePath().normalize();
    }

    private String resolveExternalCommand(
            String environmentName,
            String defaultCommand
    ) {
        String configured = System.getenv(environmentName);
        return configured == null || configured.isBlank()
                ? defaultCommand
                : configured.strip();
    }

    private MediaFileType resolveMediaFileType(
            String contentType,
            String originalFileName
    ) {
        String normalized = contentType == null
                ? ""
                : contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        String fileName = originalFileName == null
                ? ""
                : originalFileName.strip().toLowerCase(Locale.ROOT);
        boolean image = normalized.startsWith("image/")
                || fileName.matches(".*\\.(jpe?g|png|gif|webp|bmp|tiff?|avif|heic|heif)$");
        boolean video = normalized.startsWith("video/")
                || fileName.matches(".*\\.(mp4|m4v|mov|webm|mkv|avi|wmv|flv|mpeg|mpg|ts|mts|m2ts|3gp|ogv)$");
        if (image == video) {
            throw badRequest("브라우저가 인식할 수 있는 사진 또는 동영상 파일만 등록할 수 있습니다.");
        }
        if (image) {
            return new MediaFileType(
                    IMAGE_DATABASE_TYPE,
                    false,
                    false,
                    MAX_IMAGE_BYTES,
                    "사진"
            );
        }
        boolean webmInput = "video/webm".equals(normalized)
                || fileName.endsWith(".webm");
        return new MediaFileType(
                VIDEO_DATABASE_TYPE,
                true,
                webmInput,
                MAX_VIDEO_BYTES,
                "동영상"
        );
    }

    private void writeMediaFile(
            InputStream inputStream,
            Path target,
            long maximumBytes
    ) {
        if (inputStream == null) {
            throw badRequest("업로드할 파일이 없습니다.");
        }
        try {
            Files.createDirectories(mediaStorageRoot);
            long total = 0;
            byte[] buffer = new byte[MEDIA_COPY_BUFFER_SIZE];
            try (OutputStream outputStream = Files.newOutputStream(
                    target,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            )) {
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    total += read;
                    if (total > maximumBytes) {
                        throw new MediaFileTooLargeException();
                    }
                    outputStream.write(buffer, 0, read);
                }
            }
            if (total == 0) {
                throw badRequest("빈 파일은 등록할 수 없습니다.");
            }
        } catch (MediaFileTooLargeException exception) {
            deleteQuietly(target);
            throw badRequest(
                    "파일은 " + (maximumBytes / 1024L / 1024L)
                            + "MB 이하여야 합니다."
            );
        } catch (IOException exception) {
            deleteQuietly(target);
            throw mediaStorageFailure();
        } catch (RuntimeException exception) {
            deleteQuietly(target);
            throw exception;
        }
    }

    private void convertImageToWebp(Path source, Path target) {
        Process process = startProcess(new ProcessBuilder(
                ffmpegCommand,
                "-y",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-i", source.toString(),
                "-map", "0:v:0",
                "-map_metadata", "-1",
                "-c:v", "libwebp",
                "-quality", "82",
                "-compression_level", "3",
                "-loop", "0",
                target.toString()
        ));
        String output = readProcessOutput(process);
        int exitCode = waitForProcess(process);
        if (exitCode != 0) {
            throw conversionFailure(
                    "사진을 WebP로 변환하지 못했습니다.",
                    output
            );
        }
        requireConvertedFile(target, "사진");
    }

    private boolean tryRemuxWebm(
            Path source,
            Path target,
            MediaProcessingState state
    ) {
        Process process = startProcess(new ProcessBuilder(
                ffmpegCommand,
                "-y",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-i", source.toString(),
                "-map", "0:v:0",
                "-map", "0:a?",
                "-map_metadata", "-1",
                "-c", "copy",
                target.toString()
        ));
        state.attachProcess(process);
        readProcessOutput(process);
        int exitCode = waitForProcess(process);
        state.detachProcess();
        if (state.cancelled()) {
            return false;
        }
        if (exitCode == 0 && Files.isRegularFile(target)) {
            state.update(STATUS_PROCESSING, 99, "WebM 파일을 최종 확인 중입니다.");
            return true;
        }
        deleteQuietly(target);
        return false;
    }

    private void transcodeVideoToWebm(
            Path source,
            Path target,
            MediaProcessingState state
    ) {
        double durationSeconds = probeDurationSeconds(source);
        Process process = startProcess(new ProcessBuilder(
                ffmpegCommand,
                "-y",
                "-nostdin",
                "-hide_banner",
                "-loglevel", "error",
                "-i", source.toString(),
                "-map", "0:v:0",
                "-map", "0:a?",
                "-map_metadata", "-1",
                "-c:v", "libvpx-vp9",
                "-deadline", "realtime",
                "-cpu-used", "8",
                "-row-mt", "1",
                "-threads", "0",
                "-tile-columns", "2",
                "-crf", "35",
                "-b:v", "0",
                "-pix_fmt", "yuv420p",
                "-c:a", "libopus",
                "-b:a", "96k",
                "-progress", "pipe:1",
                "-nostats",
                target.toString()
        ));
        state.attachProcess(process);
        StringBuilder diagnostics = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (state.cancelled()) {
                    process.destroyForcibly();
                    break;
                }
                if (line.startsWith("out_time_us=")) {
                    updateVideoProgress(line, durationSeconds, state);
                } else if (line.startsWith("progress=end")) {
                    state.update(
                            STATUS_PROCESSING,
                            99,
                            "WebM 파일을 최종 확인 중입니다."
                    );
                } else if (!line.contains("=") && diagnostics.length() < 2_000) {
                    diagnostics.append(line).append('\n');
                }
            }
        } catch (IOException exception) {
            process.destroyForcibly();
            throw conversionFailure("동영상 변환 출력을 읽지 못했습니다.", null);
        }
        int exitCode = waitForProcess(process);
        state.detachProcess();
        if (state.cancelled()) {
            return;
        }
        if (exitCode != 0) {
            throw conversionFailure(
                    "동영상을 WebM으로 변환하지 못했습니다.",
                    diagnostics.toString()
            );
        }
    }

    private void updateVideoProgress(
            String progressLine,
            double durationSeconds,
            MediaProcessingState state
    ) {
        if (durationSeconds <= 0) {
            return;
        }
        try {
            long outTimeMicroseconds = Long.parseLong(
                    progressLine.substring("out_time_us=".length()).strip()
            );
            int progress = (int) Math.floor(
                    outTimeMicroseconds / (durationSeconds * 1_000_000D) * 100D
            );
            progress = Math.max(1, Math.min(99, progress));
            state.update(
                    STATUS_PROCESSING,
                    progress,
                    "WebM으로 변환 중입니다."
            );
        } catch (NumberFormatException ignored) {
            // FFmpeg가 일부 컨테이너에서 진행 시간을 제공하지 않아도 변환은 계속한다.
        }
    }

    private double probeDurationSeconds(Path source) {
        Process process;
        try {
            process = new ProcessBuilder(
                    ffprobeCommand,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1",
                    source.toString()
            ).redirectErrorStream(true).start();
        } catch (IOException exception) {
            return -1;
        }
        String output = readProcessOutput(process).strip();
        if (waitForProcess(process) != 0) {
            return -1;
        }
        try {
            return Double.parseDouble(output);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private Process startProcess(ProcessBuilder processBuilder) {
        processBuilder.redirectErrorStream(true);
        try {
            return processBuilder.start();
        } catch (IOException exception) {
            throw new BoardException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "BOARD_MEDIA_CONVERTER_UNAVAILABLE",
                    "FFmpeg를 실행할 수 없습니다. FFMPEG_PATH와 FFPROBE_PATH를 확인해 주세요."
            );
        }
    }

    private String readProcessOutput(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes());
        } catch (IOException exception) {
            process.destroyForcibly();
            return "";
        }
    }

    private int waitForProcess(Process process) {
        try {
            return process.waitFor();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw conversionFailure("미디어 변환이 중단되었습니다.", null);
        }
    }

    private void requireConvertedFile(Path target, String label) {
        try {
            if (!Files.isRegularFile(target) || Files.size(target) == 0) {
                throw conversionFailure(
                        label + " 변환 결과가 생성되지 않았습니다.",
                        null
                );
            }
        } catch (IOException exception) {
            throw conversionFailure(
                    label + " 변환 결과를 확인하지 못했습니다.",
                    null
            );
        }
    }

    private BoardException conversionFailure(String message, String detail) {
        return new BoardException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "BOARD_MEDIA_CONVERSION_FAILED",
                message
        );
    }

    private Path resolveMediaPath(String fileName) {
        Path path = mediaStorageRoot.resolve(fileName).normalize();
        if (!path.startsWith(mediaStorageRoot)) {
            throw badRequest("미디어 파일 경로가 올바르지 않습니다.");
        }
        return path;
    }

    private String normalizeStoredFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.strip();
        if (!normalized.matches("[0-9a-fA-F-]{36}\\.(webp|webm)")) {
            throw new BoardException(
                    HttpStatus.NOT_FOUND,
                    "BOARD_MEDIA_NOT_FOUND",
                    "사진 또는 동영상을 찾을 수 없습니다."
            );
        }
        return normalized;
    }

    private String contentTypeFor(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".webm")) return "video/webm";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private MediaResponse toMediaResponse(PostMediaRow row) {
        String storedUrl = row.getMediaUrl();
        MediaProcessingState state = mediaProcessingStates.get(
                row.getPostMediaId()
        );
        String status;
        int progress;
        String message;
        String exposedUrl;
        if (storedUrl != null && storedUrl.startsWith(PROCESSING_URL_PREFIX)) {
            if (state == null) {
                status = STATUS_FAILED;
                progress = 0;
                message = "서버 재시작으로 동영상 변환이 중단되었습니다.";
            } else {
                status = state.status();
                progress = state.progress();
                message = state.message();
            }
            exposedUrl = null;
        } else if (storedUrl != null && storedUrl.startsWith(FAILED_URL_PREFIX)) {
            status = STATUS_FAILED;
            progress = state == null ? 0 : state.progress();
            message = state == null
                    ? "동영상 변환에 실패했습니다."
                    : state.message();
            exposedUrl = null;
        } else {
            status = STATUS_READY;
            progress = 100;
            message = null;
            exposedUrl = storedUrl;
        }
        return new MediaResponse(
                row.getPostMediaId(),
                row.getMediaType(),
                exposedUrl,
                row.getDisplayOrder() == null ? 0 : row.getDisplayOrder(),
                row.getCreatedAt(),
                status,
                progress,
                message
        );
    }

    private BoardException mediaSizeExceeded(MediaFileType fileType) {
        return badRequest(
                fileType.label() + " 파일은 "
                        + (fileType.maximumBytes() / 1024L / 1024L)
                        + "MB 이하여야 합니다."
        );
    }

    private BoardException mediaStorageFailure() {
        return new BoardException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "BOARD_MEDIA_STORAGE_FAILED",
                "사진 또는 동영상을 저장하지 못했습니다."
        );
    }

    private void deleteFileAfterRollback(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            deleteQuietly(path);
                        }
                    }
                }
        );
    }

    private void cleanupMediaAfterCommit(List<PostMediaRow> mediaRows) {
        if (mediaRows == null || mediaRows.isEmpty()) {
            return;
        }
        Runnable cleanup = () -> mediaRows.forEach(media -> {
            MediaProcessingState state = mediaProcessingStates.remove(
                    media.getPostMediaId()
            );
            if (state != null) {
                state.cancel();
                deleteQuietly(state.source());
                deleteQuietly(state.target());
            }

            String mediaUrl = media.getMediaUrl();
            if (mediaUrl == null) {
                return;
            }
            if (mediaUrl.startsWith(MEDIA_URL_PREFIX)) {
                String fileName = mediaUrl.substring(MEDIA_URL_PREFIX.length());
                try {
                    deleteQuietly(resolveMediaPath(fileName));
                } catch (BoardException ignored) {
                    // DB 삭제는 유지하고 비정상 경로의 파일 삭제만 건너뛴다.
                }
                return;
            }
            String jobId = null;
            if (mediaUrl.startsWith(PROCESSING_URL_PREFIX)) {
                jobId = mediaUrl.substring(PROCESSING_URL_PREFIX.length());
            } else if (mediaUrl.startsWith(FAILED_URL_PREFIX)) {
                jobId = mediaUrl.substring(FAILED_URL_PREFIX.length());
            }
            if (jobId != null && jobId.matches("[0-9a-fA-F-]{36}")) {
                deleteQuietly(resolveMediaPath(jobId + ".upload"));
                deleteQuietly(resolveMediaPath(jobId + ".webm"));
            }
        });
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cleanup.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        cleanup.run();
                    }
                }
        );
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // DB 상태를 되돌릴 수 없는 정리 단계이므로 다음 요청에서 재시도하지 않는다.
        }
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

    public record MediaResource(
            Resource resource,
            MediaType contentType,
            String mediaType
    ) {
    }

    private record MediaFileType(
            String databaseType,
            boolean video,
            boolean webmInput,
            long maximumBytes,
            String label
    ) {
    }

    private static final class MediaProcessingState {
        private final Path source;
        private final Path target;
        private volatile String status;
        private volatile int progress;
        private volatile String message;
        private volatile Process process;
        private volatile boolean cancelled;

        private MediaProcessingState(
                Path source,
                Path target,
                String status,
                int progress,
                String message
        ) {
            this.source = source;
            this.target = target;
            this.status = status;
            this.progress = progress;
            this.message = message;
        }

        private Path source() {
            return source;
        }

        private Path target() {
            return target;
        }

        private String status() {
            return status;
        }

        private int progress() {
            return progress;
        }

        private String message() {
            return message;
        }

        private boolean cancelled() {
            return cancelled;
        }

        private void update(String status, int progress, String message) {
            if (cancelled) {
                return;
            }
            this.status = status;
            this.progress = Math.max(0, Math.min(100, progress));
            this.message = message;
        }

        private void attachProcess(Process process) {
            this.process = process;
            if (cancelled && process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }

        private void detachProcess() {
            this.process = null;
        }

        private void cancel() {
            cancelled = true;
            Process running = process;
            if (running != null && running.isAlive()) {
                running.destroyForcibly();
            }
        }
    }

    private static final class MediaFileTooLargeException
            extends RuntimeException {
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
}