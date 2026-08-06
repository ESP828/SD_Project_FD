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
import com.example.backend.review.dto.response.ReviewResponse;
import com.example.backend.review.exception.ReviewAlreadyExistsException;
import com.example.backend.review.repository.ReviewRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReviewService {

    private static final int MAX_RESULTS = 50;

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
    public List<ReviewResponse> getReviews(Long restaurantId) {
        return reviewRepository.findActiveByRestaurantId(restaurantId, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(ReviewResponse::from)
                .toList();
    }

    @Transactional
    public ReviewResponse createReview(Long restaurantId, Long accountId, ReviewCreateRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new);

        if (reviewRepository.existsActiveByRestaurantIdAndAccountId(restaurantId, accountId)) {
            throw new ReviewAlreadyExistsException();
        }

        Account account = accountRepository.getReferenceById(accountId);
        Review review = Review.create(restaurant, account, request.rating().byteValue(), request.content());
        reviewRepository.save(review);

        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsForPublicRestaurant(Long publicRestaurantId) {
        return reviewRepository.findActiveByPublicRestaurantId(publicRestaurantId, PageRequest.of(0, MAX_RESULTS)).stream()
                .map(ReviewResponse::from)
                .toList();
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

        return ReviewResponse.from(review);
    }
}
