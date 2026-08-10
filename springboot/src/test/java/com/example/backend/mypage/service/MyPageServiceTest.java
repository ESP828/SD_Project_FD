package com.example.backend.mypage.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.type.Gender;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.service.AuthorityService;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.mypage.dto.request.MyPageProfileUpdateRequest;
import com.example.backend.mypage.query.MyPageActivityQueryRepository;
import com.example.backend.mypage.query.MyPageActivityQueryRepository.ActivityCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPageServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AuthorityService authorityService;

    @Mock
    private MyPageActivityQueryRepository activityQueryRepository;

    private MyPageService myPageService;

    @BeforeEach
    void setUp() {
        myPageService = new MyPageService(
                accountRepository,
                authorityService,
                activityQueryRepository
        );
    }

    @Test
    void updatesOnlyEditableProfileFields() {
        Account account = account();
        LocalDate birthDate = LocalDate.of(1995, 5, 20);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.existsByNickname("새 닉네임")).thenReturn(false);
        when(authorityService.findCodes(1L)).thenReturn(List.of("ROLE_USER"));
        when(activityQueryRepository.findCounts(1L)).thenReturn(new ActivityCounts(1, 2, 3, 4, 5));

        var response = myPageService.updateProfile(
                1L,
                new MyPageProfileUpdateRequest("  새   닉네임  ", Gender.FEMALE, birthDate)
        );

        assertEquals("local-user", account.getLoginId());
        assertEquals("local-user@example.com", account.getEmail());
        assertEquals("새 닉네임", account.getNickname());
        assertEquals(Gender.FEMALE, account.getGender());
        assertEquals(birthDate, account.getBirthDate());
        assertEquals("새 닉네임", response.nickname());
        assertEquals(5, response.unreadNotificationCount());
    }

    @Test
    void rejectsNicknameOwnedByAnotherAccount() {
        Account account = account();
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.existsByNickname("중복 닉네임")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> myPageService.updateProfile(
                        1L,
                        new MyPageProfileUpdateRequest("중복 닉네임", Gender.UNSPECIFIED, null)
                )
        );

        assertEquals(ErrorCode.DUPLICATE_NICKNAME, exception.getErrorCode());
        assertEquals("기존닉네임", account.getNickname());
    }

    private Account account() {
        Account account = Account.local(
                "local-user",
                "local-user@example.com",
                "기존닉네임"
        );
        ReflectionTestUtils.setField(account, "accountId", 1L);
        return account;
    }
}
