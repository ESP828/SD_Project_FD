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

    @Transactional(readOnly = true)
    public List<String> getAllReviewTextsForPublicRestaurant(Long publicRestaurantId) {
        return reviewRepository.findAllActiveContentsByPublicRestaurantId(publicRestaurantId);
    }

    /** 맛집 랭킹 화면 등 여러 매장을 한 번에 다룰 때 쓰는 리뷰 개수/평균 평점 집계 결과. */
    public record RestaurantReviewStats(long reviewCount, double averageRating) {}

    /**
     * 후보 매장 여러 곳의 리뷰 개수·평균 평점을 한 번의 쿼리로 집계한다(랭킹 화면에서
     * 매장마다 따로 조회하면 N+1이 되므로 배치로 처리한다). 리뷰가 없는 매장은 결과 맵에
     * 아예 나타나지 않는다.
     */
    public Map<Long, RestaurantReviewStats> getReviewStatsForPublicRestaurants(List<Long> publicRestaurantIds) {
        Map<Long, RestaurantReviewStats> stats = new HashMap<>();
        if (publicRestaurantIds == null || publicRestaurantIds.isEmpty()) {
            return stats;
        }
        for (Object[] row : reviewRepository.aggregateActiveByPublicRestaurantIds(publicRestaurantIds)) {
            Long restaurantId = (Long) row[0];
            long count = (Long) row[1];
            double averageRating = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            stats.put(restaurantId, new RestaurantReviewStats(count, averageRating));
        }
        return stats;
    }

    /**
     * 후보 매장 여러 곳의 리뷰 본문을 매장별로 묶어서 반환한다(AI 감성분석 배치 호출용).
     * 리뷰가 없는 매장은 결과 맵에 아예 나타나지 않는다.
     */
    public Map<Long, List<String>> getReviewTextsForPublicRestaurants(List<Long> publicRestaurantIds) {
        Map<Long, List<String>> textsByRestaurantId = new HashMap<>();
        if (publicRestaurantIds == null || publicRestaurantIds.isEmpty()) {
            return textsByRestaurantId;
        }
        for (Object[] row : reviewRepository.findAllActiveContentsByPublicRestaurantIds(publicRestaurantIds)) {
            Long restaurantId = (Long) row[0];
            String content = (String) row[1];
            textsByRestaurantId.computeIfAbsent(restaurantId, id -> new ArrayList<>()).add(content);
        }
        return textsByRestaurantId;
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
