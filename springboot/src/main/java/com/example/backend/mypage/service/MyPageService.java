package com.example.backend.mypage.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.service.AuthorityService;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.mypage.dto.request.MyPageProfileUpdateRequest;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.CommentItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.FavoriteItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.NotificationItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.PostItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.ReviewItem;
import com.example.backend.mypage.dto.response.MyPageOverviewResponse;
import com.example.backend.mypage.query.MyPageActivityQueryRepository;
import com.example.backend.global.response.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MyPageService {

    private final AccountRepository accountRepository;
    private final AuthorityService authorityService;
    private final MyPageActivityQueryRepository activityQueryRepository;

    public MyPageService(
            AccountRepository accountRepository,
            AuthorityService authorityService,
            MyPageActivityQueryRepository activityQueryRepository
    ) {
        this.accountRepository = accountRepository;
        this.authorityService = authorityService;
        this.activityQueryRepository = activityQueryRepository;
    }

    @Transactional(readOnly = true)
    public MyPageOverviewResponse getOverview(Long accountId) {
        Account account = requireActiveAccount(accountId);
        return createOverview(accountId, account);
    }

    @Transactional
    public MyPageOverviewResponse updateProfile(Long accountId, MyPageProfileUpdateRequest request) {
        Account account = requireActiveAccount(accountId);
        String nickname = normalizeNickname(request.nickname());
        if (!nickname.equals(account.getNickname()) && accountRepository.existsByNickname(nickname)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
        account.updateProfile(nickname, request.gender(), request.birthDate());
        return createOverview(accountId, account);
    }

    private MyPageOverviewResponse createOverview(Long accountId, Account account) {
        var counts = activityQueryRepository.findCounts(accountId);
        return new MyPageOverviewResponse(
                account.getAccountId(),
                account.getLoginId(),
                account.getEmail(),
                account.getNickname(),
                account.getGender(),
                account.getBirthDate(),
                account.getProfileImageUrl(),
                account.getStatus(),
                account.getCreatedAt(),
                authorityService.findCodes(accountId),
                counts.favorites(),
                counts.presets(),
                counts.reviews(),
                counts.posts(),
                counts.comments(),
                counts.unreadNotifications()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<FavoriteItem> getFavorites(Long accountId, int page, int size) {
        requireActiveAccount(accountId);
        List<FavoriteItem> content = activityQueryRepository.findFavorites(accountId, page, size);
        long total = activityQueryRepository.countFavorites(accountId);
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewItem> getReviews(Long accountId, int page, int size) {
        requireActiveAccount(accountId);
        List<ReviewItem> content = activityQueryRepository.findReviews(accountId, page, size);
        long total = activityQueryRepository.countReviews(accountId);
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostItem> getPosts(Long accountId, int page, int size) {
        requireActiveAccount(accountId);
        List<PostItem> content = activityQueryRepository.findPosts(accountId, page, size);
        long total = activityQueryRepository.countPosts(accountId);
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentItem> getComments(Long accountId, int page, int size) {
        requireActiveAccount(accountId);
        List<CommentItem> content = activityQueryRepository.findComments(accountId, page, size);
        long total = activityQueryRepository.countComments(accountId);
        return PageResponse.of(content, page, size, total);
    }

    @Transactional(readOnly = true)
    public List<NotificationItem> getUnreadNotifications(Long accountId) {
        requireActiveAccount(accountId);
        return activityQueryRepository.findUnreadNotifications(accountId);
    }

    private Account requireActiveAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .filter(Account::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private String normalizeNickname(String value) {
        String nickname = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (nickname.length() < 2 || nickname.length() > 30) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return nickname;
    }
}
