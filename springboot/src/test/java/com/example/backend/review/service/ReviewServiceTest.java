package com.example.backend.review.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import com.example.backend.restaurant.repository.RestaurantRepository;
import com.example.backend.review.domain.entity.Review;
import com.example.backend.review.dto.request.ReviewUpdateRequest;
import com.example.backend.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock ReviewRepository reviewRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock PublicRestaurantRepository publicRestaurantRepository;
    @Mock AccountRepository accountRepository;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewRepository,
                restaurantRepository,
                publicRestaurantRepository,
                accountRepository
        );
    }

    @Test
    void ownerCanUpdateActiveReview() {
        Review review = review();
        when(reviewRepository.findActiveOwnedReview(10L, 1L)).thenReturn(Optional.of(review));

        var response = reviewService.updateReview(
                10L,
                1L,
                new ReviewUpdateRequest(3, "수정한 리뷰")
        );

        assertEquals(3, review.getRating());
        assertEquals("수정한 리뷰", review.getContent());
        assertEquals(Review.Status.ACTIVE, review.getStatus());
        assertEquals(3, response.rating());
        assertEquals("수정한 리뷰", response.content());
    }

    @Test
    void ownerCanSoftDeleteActiveReview() {
        Review review = review();
        when(reviewRepository.findActiveOwnedReview(10L, 1L)).thenReturn(Optional.of(review));

        reviewService.deleteReview(10L, 1L);

        assertEquals(Review.Status.DELETED, review.getStatus());
        assertNotNull(review.getDeletedAt());
    }

    @Test
    void missingDeletedOrOtherUsersReviewUsesSameNotFoundError() {
        when(reviewRepository.findActiveOwnedReview(10L, 2L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> reviewService.updateReview(10L, 2L, new ReviewUpdateRequest(4, "변경 시도"))
        );

        assertEquals(ErrorCode.REVIEW_NOT_FOUND, exception.getErrorCode());
    }

    private Review review() {
        Account account = mock(Account.class);
        return Review.create(mock(Restaurant.class), account, (byte) 5, "기존 리뷰");
    }
}
