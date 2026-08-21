package com.example.backend.review.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.exception.RestaurantNotFoundException;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import com.example.backend.restaurant.repository.RestaurantRepository;
import com.example.backend.review.domain.entity.Review;
import com.example.backend.review.dto.request.ReviewCreateRequest;
import com.example.backend.review.dto.request.ReviewUpdateRequest;
import com.example.backend.review.dto.response.ReviewMediaResponse;
import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.exception.ReviewAlreadyExistsException;
import com.example.backend.review.repository.ReviewMediaRepository;
import com.example.backend.review.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ReviewService {

    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final int MAX_PAGE_SIZE = 20;

    private final ReviewRepository reviewRepository;
    private final RestaurantRepository restaurantRepository;
    private final PublicRestaurantRepository publicRestaurantRepository;
    private final AccountRepository accountRepository;
    private final ReviewMediaRepository reviewMediaRepository;

    @Autowired
    public ReviewService(
            ReviewRepository reviewRepository,
            RestaurantRepository restaurantRepository,
            PublicRestaurantRepository publicRestaurantRepository,
            AccountRepository accountRepository,
            ReviewMediaRepository reviewMediaRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.restaurantRepository = restaurantRepository;
        this.publicRestaurantRepository = publicRestaurantRepository;
        this.accountRepository = accountRepository;
        this.reviewMediaRepository = reviewMediaRepository;
    }

    // 기존 단위 테스트/직접 생성 코드가 4개 인자 생성자를 사용하므로 호환성을 유지한다.
    ReviewService(
            ReviewRepository reviewRepository,
            RestaurantRepository restaurantRepository,
            PublicRestaurantRepository publicRestaurantRepository,
            AccountRepository accountRepository
    ) {
        this(reviewRepository, restaurantRepository, publicRestaurantRepository, accountRepository, null);
    }

    @Transactional(readOnly = true)
    public ReviewResponse.PageResponse getReviewPage(
            Long restaurantId,
            Long viewerAccountId,
            int page,
            int size
    ) {
        requireReadableRestaurant(restaurantId, viewerAccountId);
        int safePage = Math.max(page, 0);
        int safeSize = normalizePageSize(size);
        Page<Review> result = reviewRepository.findActivePageByRestaurantId(
                restaurantId,
                PageRequest.of(safePage, safeSize)
        );
        ReviewResponse myReview = viewerAccountId == null
                ? null
                : reviewRepository.findActiveByRestaurantIdAndAccountId(restaurantId, viewerAccountId)
                        .map(review -> ReviewResponse.from(review, viewerAccountId))
                        .orElse(null);
        return toPageResponse(result, viewerAccountId, myReview);
    }

    @Transactional
    public ReviewResponse createReview(Long restaurantId, Long accountId, ReviewCreateRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .filter(Restaurant::isActive)
                .orElseThrow(RestaurantNotFoundException::new);

        if (reviewRepository.existsActiveByRestaurantIdAndAccountId(restaurantId, accountId)) {
            throw new ReviewAlreadyExistsException();
        }

        Account account = accountRepository.getReferenceById(accountId);
        Review review = Review.create(restaurant, account, request.rating().byteValue(), request.content());
        reviewRepository.save(review);

        return ReviewResponse.from(review, accountId);
    }

    private Restaurant requireReadableRestaurant(Long restaurantId, Long viewerAccountId) {
        return restaurantRepository.findById(restaurantId)
                .filter(restaurant -> restaurant.isReadableBy(viewerAccountId))
                .orElseThrow(RestaurantNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public ReviewResponse.PageResponse getReviewPageForPublicRestaurant(
            Long publicRestaurantId,
            Long viewerAccountId,
            int page,
            int size
    ) {
        publicRestaurantRepository.findById(publicRestaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));
        int safePage = Math.max(page, 0);
        int safeSize = normalizePageSize(size);
        Page<Review> result = reviewRepository.findActivePageByPublicRestaurantId(
                publicRestaurantId,
                PageRequest.of(safePage, safeSize)
        );
        ReviewResponse myReview = viewerAccountId == null
                ? null
                : reviewRepository.findActiveByPublicRestaurantIdAndAccountId(publicRestaurantId, viewerAccountId)
                        .map(review -> ReviewResponse.from(review, viewerAccountId))
                        .orElse(null);
        return toPageResponse(result, viewerAccountId, myReview);
    }

    // 감성분석 요청용. 본문 텍스트가 애매하거나("ㄹㅇㅎ" 같은 무의미한 입력) 사전에 없는 단어뿐이라
    // 감성 판단이 안 될 때는 별점으로 대신 판단해야 해서, 리뷰 엔티티(본문+별점)를 그대로 내려준다.
    @Transactional(readOnly = true)
    public List<Review> getAllReviewsForSentimentForPublicRestaurant(Long publicRestaurantId) {
        return reviewRepository.findAllActiveForSentimentByPublicRestaurantId(publicRestaurantId);
    }

    @Transactional(readOnly = true)
    public List<Review> getAllReviewsForSentimentForRestaurant(Long restaurantId) {
        return reviewRepository.findAllActiveForSentimentByRestaurantId(restaurantId);
    }

    @Transactional
    public ReviewResponse createReviewForPublicRestaurant(Long publicRestaurantId, Long accountId, ReviewCreateRequest request) {
        PublicRestaurant publicRestaurant = publicRestaurantRepository.findById(publicRestaurantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESTAURANT_NOT_FOUND));

        if (reviewRepository.existsActiveByPublicRestaurantIdAndAccountId(publicRestaurantId, accountId)) {
            throw new ReviewAlreadyExistsException();
        }

        Account account = accountRepository.getReferenceById(accountId);
        Review review = Review.createForPublicRestaurant(
                publicRestaurant, account, request.rating().byteValue(), request.content()
        );
        reviewRepository.save(review);

        return ReviewResponse.from(review, accountId);
    }

    @Transactional
    public ReviewResponse updateReview(Long reviewId, Long accountId, ReviewUpdateRequest request) {
        Review review = requireActiveOwnedReview(reviewId, accountId);
        review.update(request.rating().byteValue(), request.content());
        return ReviewResponse.from(review, accountId);
    }

    @Transactional
    public void deleteReview(Long reviewId, Long accountId) {
        Review review = requireActiveOwnedReview(reviewId, accountId);
        // 실제 DB 행은 아래 delete로 완전 삭제되고, review_media는 FK ON DELETE CASCADE로 정리된다.
        // 삭제 직전 상태도 갱신해 기존 단위 테스트/직접 생성 코드와의 호환성을 유지한다.
        review.delete();
        reviewRepository.delete(review);
        reviewRepository.flush();
    }


    private int normalizePageSize(int size) {
        if (size <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private ReviewResponse.PageResponse toPageResponse(
            Page<Review> result,
            Long viewerAccountId,
            ReviewResponse myReview
    ) {
        List<ReviewResponse> items = result.getContent().stream()
                .map(review -> ReviewResponse.from(review, viewerAccountId))
                .toList();

        Set<Long> reviewIds = new LinkedHashSet<>();
        items.forEach(review -> reviewIds.add(review.reviewId()));
        if (myReview != null) {
            reviewIds.add(myReview.reviewId());
        }

        Map<Long, List<ReviewMediaResponse>> mediaByReviewId = findMediaByReviewIds(reviewIds);
        List<ReviewResponse> itemsWithMedia = items.stream()
                .map(review -> review.withMedia(
                        mediaByReviewId.getOrDefault(review.reviewId(), List.of())
                ))
                .toList();
        ReviewResponse myReviewWithMedia = myReview == null
                ? null
                : myReview.withMedia(
                        mediaByReviewId.getOrDefault(myReview.reviewId(), List.of())
                );

        return new ReviewResponse.PageResponse(
                itemsWithMedia,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize(),
                result.isFirst(),
                result.isLast(),
                myReviewWithMedia
        );
    }

    private Map<Long, List<ReviewMediaResponse>> findMediaByReviewIds(Set<Long> reviewIds) {
        if (reviewIds == null || reviewIds.isEmpty() || reviewMediaRepository == null) {
            return Map.of();
        }

        Map<Long, List<ReviewMediaResponse>> result = new HashMap<>();
        reviewMediaRepository.findByReviewIds(reviewIds).forEach(media ->
                result.computeIfAbsent(media.reviewId(), ignored -> new ArrayList<>())
                        .add(new ReviewMediaResponse(
                                media.reviewMediaId(),
                                media.mediaType(),
                                "/api/public/reviews/media/" + media.reviewMediaId(),
                                media.mimeType(),
                                media.originalName(),
                                media.fileSize(),
                                media.displayOrder()
                        ))
        );
        return result;
    }

    private Review requireActiveOwnedReview(Long reviewId, Long accountId) {
        return reviewRepository.findActiveOwnedReview(reviewId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }
}
