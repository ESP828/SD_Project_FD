package com.example.backend.board.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.board.domain.entity.Comment;
import com.example.backend.board.domain.entity.Post;
import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.CommentStatus;
import com.example.backend.board.domain.type.PostCategory;
import com.example.backend.board.domain.type.PostStatus;
import com.example.backend.board.dto.request.CommentCreateRequest;
import com.example.backend.board.exception.BoardException;
import com.example.backend.board.mapper.BoardResponseMapper;
import com.example.backend.board.policy.BoardAccessPolicy;
import com.example.backend.board.repository.CommentRepository;
import com.example.backend.board.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private BoardUserService boardUserService;
    @Mock
    private BoardAccessPolicy accessPolicy;
    @Mock
    private BoardResponseMapper responseMapper;

    private CommentService commentService;

    @BeforeEach
    void setUp() {
        commentService = new CommentService(
                commentRepository,
                postRepository,
                boardUserService,
                accessPolicy,
                responseMapper
        );
    }

    @Test
    @DisplayName("로그인 사용자의 댓글 내용을 정리해 저장한다")
    void createsComment() {
        Account author = account(1L);
        Post post = post(10L, author);
        when(boardUserService.require(1L)).thenReturn(author);
        when(postRepository.findByPostIdAndStatusForUpdate(10L, PostStatus.ACTIVE))
                .thenReturn(Optional.of(post));

        commentService.createComment(
                10L,
                new CommentCreateRequest("  감사합니다.  "),
                1L
        );

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(captor.capture());
        assertEquals("감사합니다.", captor.getValue().getContent());
    }

    @Test
    @DisplayName("댓글 작성자가 아니면 수정할 수 없다")
    void nonOwnerCannotUpdate() {
        Account author = account(1L);
        Account stranger = account(2L);
        Post post = post(10L, author);
        Comment comment = Comment.create(post, author, "원문");
        ReflectionTestUtils.setField(comment, "commentId", 100L);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(comment, "updatedAt", LocalDateTime.now());
        when(boardUserService.require(2L)).thenReturn(stranger);
        when(commentRepository.findActiveCommentForUpdate(
                100L,
                CommentStatus.ACTIVE
        )).thenReturn(Optional.of(comment));
        when(accessPolicy.isAdmin(stranger)).thenReturn(false);

        BoardException exception = assertThrows(
                BoardException.class,
                () -> commentService.updateComment(
                        100L,
                        new com.example.backend.board.dto.request.CommentUpdateRequest("수정"),
                        2L
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("원문", comment.getContent());
    }

    private Account account(Long accountId) {
        Account account = Account.local(
                "user" + accountId,
                "user" + accountId + "@example.com",
                "사용자" + accountId
        );
        ReflectionTestUtils.setField(account, "accountId", accountId);
        return account;
    }

    private Post post(Long postId, Account author) {
        Post post = Post.create(
                author,
                null,
                BoardType.GENERAL,
                PostCategory.GENERAL,
                "제목",
                "내용"
        );
        ReflectionTestUtils.setField(post, "postId", postId);
        ReflectionTestUtils.setField(post, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(post, "updatedAt", LocalDateTime.now());
        return post;
    }
}
