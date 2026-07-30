package com.example.backend.board.policy;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.PostCategory;
import com.example.backend.board.exception.BoardException;
import com.example.backend.board.query.BoardReferenceQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardAccessPolicyTest {

    @Mock
    private BoardReferenceQueryRepository referenceRepository;

    private BoardAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new BoardAccessPolicy(referenceRepository);
    }

    @Test
    @DisplayName("비회원과 일반 회원의 기본 게시 공간은 GENERAL이다")
    void defaultsToGeneralBoard() {
        assertEquals(BoardType.GENERAL, policy.resolveReadableBoardType(null, null));
        assertEquals(
                BoardType.GENERAL,
                policy.resolveReadableBoardType(BoardType.GENERAL, account(1L))
        );
    }

    @Test
    @DisplayName("비회원은 BUSINESS 게시판을 읽을 수 없다")
    void anonymousCannotReadBusinessBoard() {
        BoardException exception = assertThrows(
                BoardException.class,
                () -> policy.resolveReadableBoardType(BoardType.BUSINESS, null)
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    @DisplayName("추천과 리뷰 공유 글은 음식점 번호 직접 입력 없이 작성할 수 있다")
    void recommendationDoesNotRequireRestaurantNumber() {
        Account user = account(1L);
        assertDoesNotThrow(
                () -> policy.assertCanWrite(
                        BoardType.GENERAL,
                        PostCategory.RECOMMENDATION,
                        null,
                        user
                )
        );
    }

    @Test
    @DisplayName("승인된 사업자는 음식점 번호 직접 입력 없이 사업자 글을 작성할 수 있다")
    void businessPostDoesNotRequireRestaurantNumber() {
        Account business = account(2L);
        when(referenceRepository.hasAuthority(2L, "ROLE_ADMIN")).thenReturn(false);
        when(referenceRepository.hasAuthority(2L, "ROLE_BUSINESS")).thenReturn(true);
        when(referenceRepository.hasBusinessProfile(2L)).thenReturn(true);

        assertDoesNotThrow(
                () -> policy.assertCanWrite(
                        BoardType.BUSINESS,
                        PostCategory.GENERAL,
                        null,
                        business
                )
        );
    }

    @Test
    @DisplayName("일반 사용자는 공지를 작성할 수 없다")
    void onlyAdminCanWriteNotice() {
        Account user = account(1L);
        when(referenceRepository.hasAuthority(1L, "ROLE_ADMIN")).thenReturn(false);

        BoardException exception = assertThrows(
                BoardException.class,
                () -> policy.assertCanWrite(
                        BoardType.GENERAL,
                        PostCategory.NOTICE,
                        null,
                        user
                )
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    @DisplayName("사업자는 승인 프로필과 음식점 소유권이 모두 필요하다")
    void businessCanWriteOnlyOwnRestaurant() {
        Account business = account(2L);
        when(referenceRepository.hasAuthority(2L, "ROLE_ADMIN")).thenReturn(false);
        when(referenceRepository.hasAuthority(2L, "ROLE_BUSINESS")).thenReturn(true);
        when(referenceRepository.hasBusinessProfile(2L)).thenReturn(true);
        when(referenceRepository.restaurantExists(10L)).thenReturn(true);
        when(referenceRepository.restaurantOwnedBy(10L, 2L)).thenReturn(false);

        BoardException exception = assertThrows(
                BoardException.class,
                () -> policy.assertCanWrite(
                        BoardType.BUSINESS,
                        PostCategory.GENERAL,
                        10L,
                        business
                )
        );
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
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
}
