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
import com.example.backend.board.dto.response.PostLikeResponse;
import com.example.backend.board.dto.response.PostPageResponse;
import com.example.backend.board.exception.BoardException;
import com.example.backend.board.mapper.BoardResponseMapper;
import com.example.backend.board.policy.BoardAccessPolicy;
import com.example.backend.board.query.BoardReferenceQueryRepository;
import com.example.backend.board.repository.CommentRepository;
import com.example.backend.board.repository.PostLikeRepository;
import com.example.backend.board.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private BoardUserService boardUserService;
    @Mock
    private BoardAccessPolicy accessPolicy;
    @Mock
    private BoardResponseMapper responseMapper;
    @Mock
    private BoardReferenceQueryRepository referenceRepository;

    private PostService postService;

    @BeforeEach
    void setUp() {
        postService = new PostService(
                postRepository,
                commentRepository,
                postLikeRepository,
                boardUserService,
                accessPolicy,
                responseMapper,
                referenceRepository
        );
    }

    @Test
    @DisplayName("boardType 생략 목록은 GENERAL로 제한한다")
    void defaultsListToGeneral() {
        when(accessPolicy.resolveReadableBoardType(null, null))
                .thenReturn(BoardType.GENERAL);
        when(postRepository.search(
                eq(BoardType.GENERAL),
                eq(null),
                eq(null),
                eq(PostStatus.ACTIVE),
                eq(PostCategory.NOTICE),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));
        when(responseMapper.toListItems(List.of(), null)).thenReturn(List.of());

        PostPageResponse response = postService.getPosts(
                null,
                null,
                null,
                "LATEST",
                0,
                10,
                null
        );

        assertEquals(0, response.totalElements());
        verify(postRepository).search(
                eq(BoardType.GENERAL),
                eq(null),
                eq(null),
                eq(PostStatus.ACTIVE),
                eq(PostCategory.NOTICE),
                any(Pageable.class)
        );
    }

    @Test
    @DisplayName("로그인 사용자가 일반 게시글을 작성하며 공백을 정리한다")
    void createsGeneralPost() {
        Account author = account(1L);
        PostCreateRequest request = new PostCreateRequest(
                BoardType.GENERAL,
                PostCategory.QUESTION,
                null,
                "  강남 혼밥 질문  ",
                "  조용한 식당을 찾고 있습니다.  "
        );
        when(boardUserService.require(1L)).thenReturn(author);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        postService.createPost(request, 1L);

        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(captor.capture());
        assertEquals("강남 혼밥 질문", captor.getValue().getTitle());
        assertEquals("조용한 식당을 찾고 있습니다.", captor.getValue().getContent());
        verify(accessPolicy).assertCanWrite(
                BoardType.GENERAL,
                PostCategory.QUESTION,
                null,
                author
        );
    }

    @Test
    @DisplayName("게시글 삭제 시 연결 데이터와 함께 영구 삭제한다")
    void deletesPostPermanently() {
        Account author = account(1L);
        Post post = post(10L, author);
        when(boardUserService.require(1L)).thenReturn(author);
        when(postRepository.findByPostIdAndStatusForUpdate(
                10L,
                PostStatus.ACTIVE
        )).thenReturn(Optional.of(post));

        postService.deletePost(10L, 1L);

        verify(postRepository).delete(post);
    }

    @Test
    @DisplayName("같은 사용자의 반복 추천은 한 번만 증가한다")
    void duplicateLikeDoesNotIncreaseAgain() {
        Account user = account(1L);
        Post post = post(10L, account(2L));
        PostLikeId likeId = new PostLikeId(10L, 1L);
        when(boardUserService.require(1L)).thenReturn(user);
        when(postRepository.findByPostIdAndStatusForUpdate(
                10L,
                PostStatus.ACTIVE
        )).thenReturn(Optional.of(post));
        when(postLikeRepository.existsById(likeId)).thenReturn(false, true);

        PostLikeResponse first = postService.likePost(10L, 1L);
        PostLikeResponse second = postService.likePost(10L, 1L);

        assertEquals(1, first.likeCount());
        assertEquals(1, second.likeCount());
        verify(postLikeRepository, times(1)).save(any(PostLike.class));
    }

    @Test
    @DisplayName("페이지 번호와 크기를 먼저 검증한다")
    void validatesPageRequest() {
        BoardException exception = assertThrows(
                BoardException.class,
                () -> postService.getPosts(
                        null,
                        null,
                        null,
                        "LATEST",
                        -1,
                        10,
                        null
                )
        );
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verifyNoInteractions(postRepository);
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
