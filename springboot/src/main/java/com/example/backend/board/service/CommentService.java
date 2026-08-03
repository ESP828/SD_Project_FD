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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CommentService {

    private static final int MAX_PAGE_SIZE = 100;
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
        Page<Comment> result = commentRepository.findActiveCommentsByPostId(
                post.getPostId(),
                CommentStatus.ACTIVE,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("createdAt")))
        );
        return new CommentPageResponse(
                result.getContent().stream()
                        .map(comment -> responseMapper.toComment(comment, currentAccount))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
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
                content
        );
        long checkedAt = System.nanoTime();
        if (!reserveCommentSubmission(submissionKey, checkedAt)) {
            throw rapidDuplicateComment();
        }

        try {
            Post post = getReadablePostForCommentCreate(postId, currentAccount);
            assertNotRapidDuplicate(
                    postId,
                    currentAccount.getAccountId(),
                    content
            );
            String authorRole = accessPolicy.displayRole(currentAccount);
            Comment comment = Comment.create(
                    post,
                    currentAccount,
                    content
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
            String content
    ) {
        LocalDateTime createdAfter = LocalDateTime.now()
                .minus(RAPID_DUPLICATE_WINDOW);
        if (commentRepository
                .existsByPostPostIdAndAuthorAccountIdAndContentAndCreatedAtAfter(
                        postId,
                        accountId,
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

    private record CommentSubmissionKey(
            Long accountId,
            Long postId,
            String content
    ) {
    }
}
