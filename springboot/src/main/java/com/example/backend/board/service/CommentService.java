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
import com.example.backend.board.repository.CommentRepository;
import com.example.backend.board.repository.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CommentService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_COMMENT_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final int COMMENT_IMAGE_CHUNK_BYTES = 1024 * 1024;
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

    public CommentService(
            CommentRepository commentRepository,
            PostRepository postRepository,
            BoardUserService boardUserService,
            BoardAccessPolicy accessPolicy,
            BoardResponseMapper responseMapper
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.boardUserService = boardUserService;
        this.accessPolicy = accessPolicy;
        this.responseMapper = responseMapper;
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
            Long parentCommentId = resolveRootParentCommentId(
                    request.parentCommentId(),
                    post.getPostId(),
                    currentAccount
            );
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

    @Transactional
    public void uploadCommentImage(
            Long commentId,
            String encodedOriginalName,
            String declaredContentType,
            byte[] imageData,
            Long currentAccountId
    ) {
        Account currentAccount = boardUserService.require(currentAccountId);
        Comment comment = getExistingCommentForUpdate(commentId);
        assertParentPostReadable(comment, currentAccount);
        assertOwnerOrAdmin(comment, currentAccount);

        if (imageData == null || imageData.length == 0) {
            throw badRequest("비어 있는 사진은 첨부할 수 없습니다.");
        }
        if (imageData.length > MAX_COMMENT_IMAGE_BYTES) {
            throw badRequest("댓글 사진은 한 파일당 5MB 이하만 첨부할 수 있습니다.");
        }

        String originalName = normalizeCommentImageName(encodedOriginalName);
        String extension = findCommentImageExtension(originalName);
        String mimeType = COMMENT_IMAGE_MIME_TYPES.get(extension);
        if (mimeType == null || !COMMENT_IMAGE_EXTENSIONS.contains(extension)) {
            throw badRequest("댓글에는 JPG, PNG, WEBP, GIF 사진만 첨부할 수 있습니다.");
        }
        if (!matchesCommentImageSignature(extension, imageData)) {
            throw badRequest("파일 확장자와 실제 사진 형식이 일치하지 않습니다.");
        }

        String normalizedContentType = normalizeContentType(declaredContentType);
        if (normalizedContentType != null
                && !"application/octet-stream".equals(normalizedContentType)
                && !normalizedContentType.startsWith("image/")) {
            throw badRequest("사진 파일의 Content-Type이 올바르지 않습니다.");
        }

        if (commentRepository.initializeCommentImage(
                commentId,
                mimeType,
                originalName,
                imageData.length
        ) != 1) {
            throw notFoundCommentImage();
        }

        for (int offset = 0; offset < imageData.length;
             offset += COMMENT_IMAGE_CHUNK_BYTES) {
            int end = Math.min(
                    imageData.length,
                    offset + COMMENT_IMAGE_CHUNK_BYTES
            );
            byte[] chunk = Arrays.copyOfRange(imageData, offset, end);
            if (commentRepository.appendCommentImageChunk(commentId, chunk) != 1) {
                throw new BoardException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "BOARD_COMMENT_IMAGE_SAVE_FAILED",
                        "댓글 사진을 저장하지 못했습니다."
                );
            }
        }

        Long storedSize = commentRepository.findCommentImageStoredSize(commentId);
        if (storedSize == null || storedSize != imageData.length) {
            throw new BoardException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "BOARD_COMMENT_IMAGE_SIZE_MISMATCH",
                    "댓글 사진 저장 크기가 일치하지 않습니다."
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
        byte[] imageData = commentRepository.findCommentImageData(commentId);
        if (imageData == null || imageData.length == 0) {
            throw notFoundCommentImage();
        }

        return new CommentImageDownload(
                commentId,
                comment.getImageMimeType(),
                comment.getImageOriginalName(),
                comment.getImageFileSize(),
                imageData
        );
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
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);
        return post;
    }

    private Long resolveRootParentCommentId(
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
        return target.getParentCommentId() == null
                ? target.getCommentId()
                : target.getParentCommentId();
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
        if (post.isDeleted()) {
            throw new BoardException(
                    HttpStatus.NOT_FOUND,
                    "BOARD_POST_NOT_FOUND",
                    "게시글을 찾을 수 없습니다."
            );
        }
        accessPolicy.assertCanRead(post.getBoardType(), currentAccount);
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
            byte[] data
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
