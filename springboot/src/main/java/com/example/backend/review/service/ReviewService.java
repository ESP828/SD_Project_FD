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
import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.exception.ReviewAlreadyExistsException;
import com.example.backend.review.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewService {

    private static final int MAX_RESULTS = 50;
    private static final int DEFAULT_PAGE_SIZE = 5;
    private static final int MAX_PAGE_SIZE = 20;

    private final ReviewRepository reviewRepository;
    private final RestaurantRepository restaurantRepository;
    private final PublicRestaurantRepository publicRestaurantRepository;
    private final AccountRepository accountRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            RestaurantRepository restaurantRepository,
            PublicRestaurantRepository publicRestaurantRepository,
            AccountRepository accountRepository
    ) {
        this.reviewRepository = reviewRepository;
        this.restaurantRepository = restaurantRepository;
        this.publicRestaurantRepository = publicRestaurantRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviews(Long restaurantId, Long viewerAccountId) {
        requireReadableRestaurant(restaurantId, viewerAccountId);
        return reviewRepository.findActiveByRestaurantId(restaurantId, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(review -> ReviewResponse.from(review, viewerAccountId))
                .toList();
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
    public List<ReviewResponse> getReviewsForPublicRestaurant(Long publicRestaurantId) {
        return reviewRepository.findActiveByPublicRestaurantId(publicRestaurantId, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(ReviewResponse::from)
                .toList();
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

    @Transactional(readOnly = true)
    public List<String> getAllReviewTextsForPublicRestaurant(Long publicRestaurantId) {
        return reviewRepository.findAllActiveContentsByPublicRestaurantId(publicRestaurantId);
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
        return new ReviewResponse.PageResponse(
                items,
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize(),
                result.isFirst(),
                result.isLast(),
                myReview
        );
    }

    private Review requireActiveOwnedReview(Long reviewId, Long accountId) {
        return reviewRepository.findActiveOwnedReview(reviewId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));
    }
}
