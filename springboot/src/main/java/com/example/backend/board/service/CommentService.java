package com.example.backend.board.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.board.domain.entity.Comment;
import com.example.backend.board.domain.entity.Post;
import com.example.backend.board.domain.type.CommentStatus;
import com.example.backend.board.domain.type.PostStatus;
import com.example.backend.board.dto.request.CommentCreateRequest;
import com.example.backend.board.dto.request.CommentUpdateRequest;
import com.example.backend.board.dto.response.CommentPageResponse;
import com.example.backend.board.dto.response.CommentResponse;
import com.example.backend.board.exception.BoardException;
import com.example.backend.board.mapper.BoardResponseMapper;
import com.example.backend.board.policy.BoardAccessPolicy;
import com.example.backend.board.query.BoardReferenceQueryRepository;
import com.example.backend.board.repository.CommentRepository;
import com.example.backend.board.repository.PostRepository;
import com.example.backend.notification.service.NotificationService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CommentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommentService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_COMMENT_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int COMMENT_IMAGE_READ_CHUNK_BYTES = 1024 * 1024;
    private static final int MAX_COMMENT_IMAGE_NAME_LENGTH = 255;
    private static final Set<String> COMMENT_IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp"
    );
    private static final Map<String, String> COMMENT_IMAGE_MIME_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp"
    );
    private static final Duration RAPID_DUPLICATE_WINDOW =
            Duration.ofMillis(3_500);
    private static final long RAPID_DUPLICATE_WINDOW_NANOS =
            RAPID_DUPLICATE_WINDOW.toNanos();
    private static final long SUBMISSION_IN_PROGRESS = Long.MAX_VALUE;
    private static final int SUBMISSION_CLEANUP_INTERVAL = 256;

    private final ConcurrentMap<CommentSubmissionKey, Long>
            recentCommentSubmissions = new ConcurrentHashMap<>();
    private final AtomicInteger commentSubmissionChecks = new AtomicInteger();

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final BoardUserService boardUserService;
    private final BoardAccessPolicy accessPolicy;
    private final BoardResponseMapper responseMapper;
    private final NotificationService notificationService;
    private BoardReferenceQueryRepository referenceRepository;
    private TransactionTemplate commentImageTransactionTemplate;

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            BoardUserService boardUserService,
            BoardAccessPolicy accessPolicy,
            BoardResponseMapper responseMapper,
            NotificationService notificationService
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.boardUserService = boardUserService;
        this.accessPolicy = accessPolicy;
        this.responseMapper = responseMapper;
        this.notificationService = notificationService;
    }

    @Autowired
    void configureCommentImageInfrastructure(
            PlatformTransactionManager transactionManager,
            BoardReferenceQueryRepository referenceRepository
    ) {
        this.commentImageTransactionTemplate = new TransactionTemplate(
                transactionManager
        );
        this.referenceRepository = referenceRepository;
    }

    @PostConstruct
    void cleanAbandonedCommentImageStaging() {
        Path tempDirectory = Path.of(System.getProperty("java.io.tmpdir"));
        if (!Files.isDirectory(tempDirectory)) {
            return;
        }
        try (DirectoryStream<Path> stagedFiles = Files.newDirectoryStream(
                tempDirectory,
                "fooduck-board-comment-image-*.upload"
        )) {
            for (Path stagedFile : stagedFiles) {
                try {
                    Files.deleteIfExists(stagedFile);
                } catch (IOException ignored) {
                    // 다음 실행 또는 운영체제 임시 파일 정리에 맡긴다.
                }
            }
        } catch (IOException ignored) {
            // 임시 폴더를 읽지 못해도 게시판 기동은 계속한다.
        }
    }

    @Transactional(readOnly = true)
    public CommentPageResponse getComments(
            Long postId,
            int page,
            int size,
            Long currentAccountId
    ) {
        validatePage(page, size);
        Account currentAccount = boardUserService.findOptional(currentAccountId);
        Post post = getReadablePost(postId, currentAccount);
        Page<Comment> result = commentRepository.findRootActiveCommentsByPostId(
                post.getPostId(),
                CommentStatus.ACTIVE,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                Sort.Order.asc("createdAt"),
                                Sort.Order.asc("commentId")
                        )
                )
        );

        List<Comment> pageComments = new ArrayList<>(result.getContent());
        List<Long> parentCommentIds = result.getContent().stream()
                .map(Comment::getCommentId)
                .toList();
        if (!parentCommentIds.isEmpty()) {
            pageComments.addAll(commentRepository.findActiveRepliesByParentIds(
                    post.getPostId(),
                    parentCommentIds,
                    CommentStatus.ACTIVE
            ));
        }

        long totalCommentCount = commentRepository.countByPostPostIdAndStatus(
                post.getPostId(),
                CommentStatus.ACTIVE
        );
        return new CommentPageResponse(
                responseMapper.toComments(pageComments, currentAccount),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast(),
                totalCommentCount
        );
    }

    @Transactional
    public CommentResponse createComment(
            Long postId,
            CommentCreateRequest request,
            Long currentAccountId
    ) {
        validateId(postId, "게시글");
        Account currentAccount = boardUserService.require(currentAccountId);
        String content = request.content().strip();
        CommentSubmissionKey submissionKey = new CommentSubmissionKey(
                currentAccount.getAccountId(),
                postId,
                request.parentCommentId(),
                content
        );
        long checkedAt = System.nanoTime();
        if (!reserveCommentSubmission(submissionKey, checkedAt)) {
            throw rapidDuplicateComment();
        }

        try {
            Post post = getReadablePostForCommentCreate(postId, currentAccount);
            ReplyTarget replyTarget = resolveReplyTarget(
                    request.parentCommentId(),
                    post.getPostId(),
                    currentAccount
            );
            Long parentCommentId = replyTarget == null
                    ? null
                    : replyTarget.rootParentCommentId();
            if (replyTarget != null) {
                assertReplyHasBody(content, replyTarget.targetName());
            }
            assertNotRapidDuplicate(
                    postId,
                    currentAccount.getAccountId(),
                    parentCommentId,
                    content
            );
            String authorRole = accessPolicy.displayRole(currentAccount);
            Comment comment = parentCommentId == null
                    ? Comment.create(post, currentAccount, content)
                    : Comment.createReply(
                            post,
                            currentAccount,
                            content,
                            parentCommentId
                    );
            commentRepository.save(comment);
            CommentResponse response = responseMapper.toComment(
                    comment,
                    currentAccount,
                    authorRole
            );
            if (!post.getAuthor().getAccountId().equals(currentAccount.getAccountId())) {
                createCommentNotificationAfterCommit(
                        post.getAuthor(),
                        currentAccount.getNickname(),
                        post.getPostId()
                );
            }
            completeCommentSubmissionAfterTransaction(submissionKey);
            return response;
        } catch (RuntimeException | Error exception) {
            releaseCommentSubmission(submissionKey);
            throw exception;
        }
    }

    @Transactional
    public CommentResponse updateComment(
            Long commentId,
            CommentUpdateRequest request,
            Long currentAccountId
    ) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Comment comment = getExistingCommentForUpdate(commentId);
        assertParentPostReadable(comment, currentAccount);
        assertOwnerOrAdmin(comment, currentAccount);
        comment.update(request.content().strip());
        return responseMapper.toComment(comment, currentAccount);
    }

    @Transactional
    public void deleteComment(Long commentId, Long currentAccountId) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Comment comment = getExistingCommentForUpdate(commentId);
        assertParentPostReadable(comment, currentAccount);
        assertOwnerOrAdmin(comment, currentAccount);
        commentRepository.delete(comment);
    }

    public void uploadCommentImage(
            Long commentId,
            String encodedOriginalName,
            String declaredContentType,
            InputStream imageData,
            long declaredContentLength,
            Long currentAccountId
    ) {
        TransactionTemplate transactionTemplate =
                requireCommentImageTransactionTemplate();
        transactionTemplate.executeWithoutResult(status ->
                preflightCommentImageUpload(commentId, currentAccountId)
        );

        String originalName = normalizeCommentImageName(encodedOriginalName);
        String extension = findCommentImageExtension(originalName);
        String mimeType = COMMENT_IMAGE_MIME_TYPES.get(extension);
        if (mimeType == null || !COMMENT_IMAGE_EXTENSIONS.contains(extension)) {
            throw badRequest(
                    "댓글에는 JPG, PNG, WEBP, GIF 사진만 첨부할 수 있습니다."
            );
        }

        String normalizedContentType = normalizeContentType(declaredContentType);
        if (normalizedContentType != null
                && !"application/octet-stream".equals(normalizedContentType)
                && !normalizedContentType.startsWith("image/")) {
            throw badRequest("사진 파일의 Content-Type이 올바르지 않습니다.");
        }

        validateCommentImageLength(declaredContentLength);
        StagedCommentImage stagedImage = stageCommentImage(imageData);
        try {
            validateCommentImageSignature(extension, stagedImage.path());
            transactionTemplate.executeWithoutResult(status ->
                    persistStagedCommentImage(
                            commentId,
                            mimeType,
                            originalName,
                            stagedImage,
                            currentAccountId
                    )
            );
        } finally {
            deleteStagedCommentImageQuietly(stagedImage.path());
        }
    }

    private void preflightCommentImageUpload(
            Long commentId,
            Long currentAccountId
    ) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Comment comment = getExistingCommentForUpdate(commentId);
        assertParentPostReadable(comment, currentAccount);
        assertOwnerOrAdmin(comment, currentAccount);
    }

    private void persistStagedCommentImage(
            Long commentId,
            String mimeType,
            String originalName,
            StagedCommentImage stagedImage,
            Long currentAccountId
    ) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Comment comment = getExistingCommentForUpdate(commentId);
        assertParentPostReadable(comment, currentAccount);
        assertOwnerOrAdmin(comment, currentAccount);

        try (InputStream inputStream =
                     Files.newInputStream(stagedImage.path())) {
            requireReferenceRepository().storeCommentImageData(
                    commentId,
                    mimeType,
                    originalName,
                    stagedImage.fileSize(),
                    inputStream
            );
        } catch (IOException exception) {
            throw new BoardException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BOARD_COMMENT_IMAGE_SAVE_FAILED",
                    "댓글 사진을 저장하지 못했습니다."
            );
        } catch (RuntimeException exception) {
            throw new BoardException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BOARD_COMMENT_IMAGE_SAVE_FAILED",
                    exception.getMessage() == null
                            ? "댓글 사진을 저장하지 못했습니다."
                            : exception.getMessage()
            );
        }
    }

    @Transactional(readOnly = true)
    public CommentImageDownload getCommentImage(
            Long commentId,
            Long currentAccountId
    ) {
        validateId(commentId, "댓글");
        Comment comment = commentRepository.findById(commentId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(this::notFoundCommentImage);

        Account currentAccount = boardUserService.findOptional(currentAccountId);
        assertParentPostReadable(comment, currentAccount);

        if (!comment.hasImage()) {
            throw notFoundCommentImage();
        }
        Long declaredSize = comment.getImageFileSize();
        Long storedSize = commentRepository.findCommentImageStoredSize(commentId);
        if (declaredSize == null || declaredSize <= 0
                || storedSize == null || storedSize <= 0) {
            throw notFoundCommentImage();
        }
        if (storedSize.longValue() != declaredSize.longValue()) {
            throw new BoardException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BOARD_COMMENT_IMAGE_SIZE_MISMATCH",
                    "댓글 사진 저장 크기가 일치하지 않습니다."
            );
        }

        return new CommentImageDownload(
                commentId,
                comment.getImageMimeType(),
                comment.getImageOriginalName(),
                declaredSize,
                comment.getUpdatedAt()
        );
    }

    public void streamCommentImage(
            Long commentId,
            long fileSize,
            OutputStream outputStream
    ) throws IOException {
        long written = 0;
        while (written < fileSize) {
            int requestedLength = (int) Math.min(
                    COMMENT_IMAGE_READ_CHUNK_BYTES,
                    fileSize - written
            );
            byte[] chunk = requireReferenceRepository().readCommentImageChunk(
                    commentId,
                    written,
                    requestedLength
            );
            if (chunk.length == 0) {
                break;
            }
            outputStream.write(chunk);
            written += chunk.length;
            if (chunk.length != requestedLength) {
                break;
            }
        }
        if (written != fileSize) {
            throw new BoardException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BOARD_COMMENT_IMAGE_SIZE_MISMATCH",
                    "댓글 사진 저장 크기가 일치하지 않습니다."
            );
        }
    }

    private void validateCommentImageLength(long declaredContentLength) {
        if (declaredContentLength == 0) {
            throw badRequest("비어 있는 사진은 첨부할 수 없습니다.");
        }
        if (declaredContentLength > MAX_COMMENT_IMAGE_BYTES) {
            throw badRequest(
                    "댓글 사진은 한 파일당 5MB 이하만 첨부할 수 있습니다."
            );
        }
    }

    private StagedCommentImage stageCommentImage(InputStream imageData) {
        if (imageData == null) {
            throw badRequest("비어 있는 사진은 첨부할 수 없습니다.");
        }

        Path stagedImage = null;
        long fileSize = 0;
        try {
            stagedImage = Files.createTempFile(
                    "fooduck-board-comment-image-",
                    ".upload"
            );
            try (OutputStream outputStream = Files.newOutputStream(stagedImage)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = imageData.read(buffer)) != -1) {
                    if (fileSize + read > MAX_COMMENT_IMAGE_BYTES) {
                        throw badRequest(
                                "댓글 사진은 한 파일당 5MB 이하만 첨부할 수 있습니다."
                        );
                    }
                    outputStream.write(buffer, 0, read);
                    fileSize += read;
                }
            }
            if (fileSize == 0) {
                throw badRequest("비어 있는 사진은 첨부할 수 없습니다.");
            }
            return new StagedCommentImage(stagedImage, fileSize);
        } catch (BoardException exception) {
            deleteStagedCommentImageQuietly(stagedImage);
            throw exception;
        } catch (IOException exception) {
            deleteStagedCommentImageQuietly(stagedImage);
            throw new BoardException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BOARD_COMMENT_IMAGE_STAGING_FAILED",
                    "댓글 사진을 서버 처리용 파일로 준비하지 못했습니다."
            );
        }
    }

    private void validateCommentImageSignature(
            String extension,
            Path stagedImage
    ) {
        byte[] signature;
        try (InputStream inputStream = Files.newInputStream(stagedImage)) {
            signature = inputStream.readNBytes(16);
        } catch (IOException exception) {
            throw new BoardException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BOARD_COMMENT_IMAGE_READ_FAILED",
                    "댓글 사진 형식을 확인하지 못했습니다."
            );
        }
        if (!matchesCommentImageSignature(extension, signature)) {
            throw badRequest("파일 확장자와 실제 사진 형식이 일치하지 않습니다.");
        }
    }

    private void deleteStagedCommentImageQuietly(Path stagedImage) {
        if (stagedImage == null) {
            return;
        }
        try {
            Files.deleteIfExists(stagedImage);
        } catch (IOException ignored) {
            // 요청 처리는 이미 끝났으므로 임시 파일 정리 실패는 무시한다.
        }
    }

    private TransactionTemplate requireCommentImageTransactionTemplate() {
        if (commentImageTransactionTemplate == null) {
            throw new IllegalStateException(
                    "댓글 사진 트랜잭션이 초기화되지 않았습니다."
            );
        }
        return commentImageTransactionTemplate;
    }

    private BoardReferenceQueryRepository requireReferenceRepository() {
        if (referenceRepository == null) {
            throw new IllegalStateException(
                    "댓글 사진 저장소가 초기화되지 않았습니다."
            );
        }
        return referenceRepository;
    }

    private String normalizeCommentImageName(String encodedOriginalName) {
        if (encodedOriginalName == null || encodedOriginalName.isBlank()) {
            throw badRequest("댓글 사진 이름이 없습니다.");
        }

        String decoded;
        try {
            decoded = URLDecoder.decode(
                    encodedOriginalName,
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException exception) {
            throw badRequest("댓글 사진 이름이 올바르지 않습니다.");
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
            throw badRequest("댓글 사진 이름이 올바르지 않습니다.");
        }
        if (fileName.length() > MAX_COMMENT_IMAGE_NAME_LENGTH) {
            fileName = fileName.substring(0, MAX_COMMENT_IMAGE_NAME_LENGTH);
        }
        return fileName;
    }

    private String findCommentImageExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 1 || dot == fileName.length() - 1) {
            throw badRequest("댓글 사진 확장자를 확인해 주세요.");
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

    private boolean matchesCommentImageSignature(
            String extension,
            byte[] data
    ) {
        return switch (extension) {
            case "jpg", "jpeg" -> startsWith(data, 0xff, 0xd8, 0xff);
            case "png" -> startsWith(
                    data,
                    0x89, 0x50, 0x4e, 0x47,
                    0x0d, 0x0a, 0x1a, 0x0a
            );
            case "gif" -> asciiEquals(data, 0, "GIF87a")
                    || asciiEquals(data, 0, "GIF89a");
            case "webp" -> asciiEquals(data, 0, "RIFF")
                    && asciiEquals(data, 8, "WEBP");
            default -> false;
        };
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

    private BoardException notFoundCommentImage() {
        return new BoardException(
                HttpStatus.NOT_FOUND,
                "BOARD_COMMENT_IMAGE_NOT_FOUND",
                "댓글 사진을 찾을 수 없습니다."
        );
    }

    private Post getReadablePost(Long postId, Account currentAccount) {
        validateId(postId, "게시글");
        Post post = postRepository.findByPostIdAndStatus(postId, PostStatus.ACTIVE)
                .orElseThrow(() -> new BoardException(
                        HttpStatus.NOT_FOUND,
                        "BOARD_POST_NOT_FOUND",
                        "게시글을 찾을 수 없습니다."
                ));
        assertCommunityPost(post);
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);
        return post;
    }

    private Post getReadablePostForCommentCreate(
            Long postId,
            Account currentAccount
    ) {
        Post post = postRepository.findByPostIdAndStatusForUpdate(
                        postId,
                        PostStatus.ACTIVE
                )
                .orElseThrow(() -> new BoardException(
                        HttpStatus.NOT_FOUND,
                        "BOARD_POST_NOT_FOUND",
                        "게시글을 찾을 수 없습니다."
                ));
        assertCommunityPost(post);
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);
        return post;
    }

    private ReplyTarget resolveReplyTarget(
            Long requestedParentCommentId,
            Long postId,
            Account currentAccount
    ) {
        if (requestedParentCommentId == null) {
            return null;
        }
        validateId(requestedParentCommentId, "부모 댓글");
        Comment target = commentRepository.findActiveCommentForUpdate(
                        requestedParentCommentId,
                        CommentStatus.ACTIVE
                )
                .orElseThrow(() -> new BoardException(
                        HttpStatus.NOT_FOUND,
                        "BOARD_PARENT_COMMENT_NOT_FOUND",
                        "답글을 남길 댓글을 찾을 수 없습니다."
                ));
        assertParentPostReadable(target, currentAccount);
        if (!target.getPost().getPostId().equals(postId)) {
            throw badRequest("같은 게시글의 댓글에만 답글을 남길 수 있습니다.");
        }
        Long rootParentCommentId = target.getParentCommentId() == null
                ? target.getCommentId()
                : target.getParentCommentId();
        return new ReplyTarget(rootParentCommentId, replyTargetName(target));
    }

    private void assertReplyHasBody(String content, String targetName) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isBlank() || normalized.equals("@" + targetName)) {
            throw badRequest("답글 내용을 입력해 주세요.");
        }
    }

    private String replyTargetName(Comment target) {
        String nickname = target.getAuthor().getNickname();
        String rawName = nickname != null && !nickname.isBlank()
                ? nickname
                : target.getAuthor().getLoginId();
        if (rawName == null || rawName.isBlank()) {
            return "작성자";
        }
        String normalized = rawName.strip();
        while (normalized.startsWith("@")) {
            normalized = normalized.substring(1).stripLeading();
        }
        return normalized.isBlank() ? "작성자" : normalized;
    }

    private Comment getExistingCommentForUpdate(Long commentId) {
        validateId(commentId, "댓글");
        return commentRepository.findActiveCommentForUpdate(
                        commentId,
                        CommentStatus.ACTIVE
                )
                .orElseThrow(() -> new BoardException(
                        HttpStatus.NOT_FOUND,
                        "BOARD_COMMENT_NOT_FOUND",
                        "댓글을 찾을 수 없습니다."
                ));
    }

    private void assertParentPostReadable(Comment comment, Account currentAccount) {
        Post post = comment.getPost();
        assertCommunityPost(post);
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);
    }

    private void assertCommunityPost(Post post) {
        if (post.isDeleted()) {
            throw new BoardException(
                    HttpStatus.NOT_FOUND,
                    "BOARD_POST_NOT_FOUND",
                    "게시글을 찾을 수 없습니다."
            );
        }
    }

    private void assertOwnerOrAdmin(Comment comment, Account account) {
        boolean owned = comment.getAuthor().getAccountId().equals(account.getAccountId());
        if (!owned && !accessPolicy.isAdmin(account)) {
            throw new BoardException(
                    HttpStatus.FORBIDDEN,
                    "BOARD_COMMENT_OWNER_REQUIRED",
                    "댓글 작성자 또는 관리자만 수정·삭제할 수 있습니다."
            );
        }
    }

    private void validateId(Long id, String field) {
        if (id == null || id < 1) {
            throw badRequest(field + " 번호는 1 이상의 숫자여야 합니다.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0) {
            throw badRequest("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw badRequest(
                    "댓글 페이지 크기는 1~" + MAX_PAGE_SIZE + " 사이여야 합니다."
            );
        }
    }

    private BoardException badRequest(String message) {
        return new BoardException(
                HttpStatus.BAD_REQUEST,
                "BOARD_INVALID_INPUT",
                message
        );
    }

    private void assertNotRapidDuplicate(
            Long postId,
            Long accountId,
            Long parentCommentId,
            String content
    ) {
        LocalDateTime createdAfter = LocalDateTime.now()
                .minus(RAPID_DUPLICATE_WINDOW);
        if (commentRepository.existsRapidDuplicate(
                postId,
                accountId,
                parentCommentId,
                content,
                createdAfter
        )) {
            throw rapidDuplicateComment();
        }
    }

    private boolean reserveCommentSubmission(
            CommentSubmissionKey submissionKey,
            long checkedAt
    ) {
        cleanExpiredCommentSubmissions(checkedAt);
        while (true) {
            Long previous = recentCommentSubmissions.putIfAbsent(
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
            if (recentCommentSubmissions.replace(
                    submissionKey,
                    previous,
                    SUBMISSION_IN_PROGRESS
            )) {
                return true;
            }
        }
    }

    private void createCommentNotificationAfterCommit(
            Account recipient,
            String commenterNickname,
            Long postId
    ) {
        Runnable createNotification = () -> {
            try {
                notificationService.createCommentNotification(
                        recipient,
                        commenterNickname,
                        postId
                );
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "댓글 알림 생성에 실패했습니다. recipientAccountId={}, postId={}",
                        recipient == null ? null : recipient.getAccountId(),
                        postId,
                        exception
                );
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            createNotification.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        createNotification.run();
                    }
                }
        );
    }

    private void completeCommentSubmissionAfterTransaction(
            CommentSubmissionKey submissionKey
    ) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager
                .isActualTransactionActive()) {
            completeCommentSubmission(submissionKey);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_COMMITTED) {
                            completeCommentSubmission(submissionKey);
                        } else {
                            releaseCommentSubmission(submissionKey);
                        }
                    }
                }
        );
    }

    private void completeCommentSubmission(
            CommentSubmissionKey submissionKey
    ) {
        recentCommentSubmissions.replace(
                submissionKey,
                SUBMISSION_IN_PROGRESS,
                System.nanoTime()
        );
    }

    private void releaseCommentSubmission(
            CommentSubmissionKey submissionKey
    ) {
        recentCommentSubmissions.remove(
                submissionKey,
                SUBMISSION_IN_PROGRESS
        );
    }

    private void cleanExpiredCommentSubmissions(long checkedAt) {
        if ((commentSubmissionChecks.incrementAndGet()
                & (SUBMISSION_CLEANUP_INTERVAL - 1)) != 0) {
            return;
        }
        recentCommentSubmissions.forEach((submissionKey, completedAt) -> {
            if (completedAt != SUBMISSION_IN_PROGRESS
                    && checkedAt - completedAt
                    >= RAPID_DUPLICATE_WINDOW_NANOS) {
                recentCommentSubmissions.remove(submissionKey, completedAt);
            }
        });
    }

    private BoardException rapidDuplicateComment() {
        return new BoardException(
                HttpStatus.TOO_MANY_REQUESTS,
                "BOARD_RAPID_DUPLICATE",
                "같은 내용을 연달아 등록할 수 없습니다. 잠시 후 다시 시도해 주세요."
        );
    }

    public record CommentImageDownload(
            Long commentId,
            String mimeType,
            String originalName,
            long fileSize,
            LocalDateTime updatedAt
    ) {
    }

    private record StagedCommentImage(Path path, long fileSize) {
    }

    private record ReplyTarget(
            Long rootParentCommentId,
            String targetName
    ) {
    }

    private record CommentSubmissionKey(
            Long accountId,
            Long postId,
            Long parentCommentId,
            String content
    ) {
    }
}
