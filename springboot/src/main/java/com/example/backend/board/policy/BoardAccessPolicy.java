package com.example.backend.board.policy;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.PostCategory;
import com.example.backend.board.exception.BoardException;
import com.example.backend.board.query.BoardReferenceQueryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class BoardAccessPolicy {

    private static final String ROLE_BUSINESS = "ROLE_BUSINESS";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final BoardReferenceQueryRepository referenceRepository;

    public BoardAccessPolicy(BoardReferenceQueryRepository referenceRepository) {
        this.referenceRepository = referenceRepository;
    }

    public boolean isAdmin(Account account) {
        return account != null
                && "ADMIN".equals(
                        referenceRepository.findDisplayRole(account.getAccountId())
                );
    }

    public boolean isApprovedBusiness(Account account) {
        if (account == null) {
            return false;
        }
        String displayRole =
                referenceRepository.findDisplayRole(account.getAccountId());
        return "ADMIN".equals(displayRole) || "BUSINESS".equals(displayRole);
    }

    public String displayRole(Account account) {
        if (account == null) {
            return "USER";
        }
        return referenceRepository.findDisplayRole(account.getAccountId());
    }

    public String displayRole(Set<String> authorityCodes, boolean hasBusinessProfile) {
        if (authorityCodes.contains(ROLE_ADMIN)) {
            return "ADMIN";
        }
        if (authorityCodes.contains(ROLE_BUSINESS) && hasBusinessProfile) {
            return "BUSINESS";
        }
        return "USER";
    }

    /**
     * boardType을 생략하면 일반 커뮤니티를 기본으로 하며 BUSINESS는 승인된 계정만 읽는다.
     */
    public BoardType resolveReadableBoardType(BoardType requested, Account account) {
        BoardType resolved = requested == null ? BoardType.GENERAL : requested;
        assertCanRead(resolved, account);
        return resolved;
    }

    public void assertCanRead(BoardType boardType, Account account) {
        if (boardType == null) {
            throw badRequest("게시 공간이 올바르지 않습니다.");
        }
        if (boardType == BoardType.BUSINESS && !isApprovedBusiness(account)) {
            throw new BoardException(
                    HttpStatus.FORBIDDEN,
                    "BOARD_BUSINESS_READ_FORBIDDEN",
                    "사업자 커뮤니티는 승인된 사업자 또는 관리자만 볼 수 있습니다."
            );
        }
    }

    public void assertCanWrite(
            BoardType boardType,
            PostCategory category,
            Long restaurantId,
            Account account
    ) {
        if (account == null) {
            throw new BoardException(
                    HttpStatus.UNAUTHORIZED,
                    "BOARD_AUTHENTICATION_REQUIRED",
                    "로그인이 필요합니다."
            );
        }
        if (boardType == null || category == null) {
            throw badRequest("게시 공간과 카테고리를 선택해 주세요.");
        }

        if (category == PostCategory.NOTICE && !isAdmin(account)) {
            throw new BoardException(
                    HttpStatus.FORBIDDEN,
                    "BOARD_NOTICE_WRITE_FORBIDDEN",
                    "관리자만 공지를 작성할 수 있습니다."
            );
        }

        if (boardType == BoardType.BUSINESS && !isApprovedBusiness(account)) {
            throw new BoardException(
                    HttpStatus.FORBIDDEN,
                    "BOARD_BUSINESS_WRITE_FORBIDDEN",
                    "승인된 사업자 또는 관리자만 사업자 게시글을 작성할 수 있습니다."
            );
        }

        if (restaurantId != null && !referenceRepository.restaurantExists(restaurantId)) {
            throw new BoardException(
                    HttpStatus.NOT_FOUND,
                    "BOARD_RESTAURANT_NOT_FOUND",
                    "연결할 수 있는 음식점을 찾을 수 없습니다."
            );
        }

        if (restaurantId != null
                && boardType == BoardType.BUSINESS
                && !isAdmin(account)
                && !referenceRepository.restaurantOwnedBy(
                        restaurantId,
                        account.getAccountId()
                )) {
            throw new BoardException(
                    HttpStatus.FORBIDDEN,
                    "BOARD_RESTAURANT_OWNERSHIP_REQUIRED",
                    "본인이 등록한 음식점에 대해서만 사업자 게시글을 작성할 수 있습니다."
            );
        }
    }

    public void assertBoardTypeChangeAllowed(
            BoardType current,
            BoardType requested,
            Account account
    ) {
        if (current != requested && !isAdmin(account)) {
            throw new BoardException(
                    HttpStatus.FORBIDDEN,
                    "BOARD_TYPE_CHANGE_FORBIDDEN",
                    "게시 공간 변경은 관리자만 할 수 있습니다."
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
}
