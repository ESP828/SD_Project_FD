package com.example.backend.board.mapper;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.type.AccountStatus;
import com.example.backend.board.domain.entity.Comment;
import com.example.backend.board.domain.entity.Post;
import com.example.backend.board.domain.type.CommentStatus;
import com.example.backend.board.domain.type.PostCategory;
import com.example.backend.board.dto.response.CommentResponse;
import com.example.backend.board.dto.response.PostDetailResponse;
import com.example.backend.board.dto.response.PostListItemResponse;
import com.example.backend.board.dto.response.RestaurantSummaryResponse;
import com.example.backend.board.policy.BoardAccessPolicy;
import com.example.backend.board.query.BoardReferenceQueryRepository;
import com.example.backend.board.query.BoardReferenceQueryRepository.AuthorRoleReference;
import com.example.backend.board.query.BoardReferenceQueryRepository.PostMediaReference;
import com.example.backend.board.repository.CommentRepository;
import com.example.backend.board.repository.PostLikeRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BoardResponseMapper {

    private static final int PREVIEW_LENGTH = 180;
    private static final AuthorRoleReference DEFAULT_AUTHOR_ROLE =
            new AuthorRoleReference(Set.of(), false);

    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final BoardReferenceQueryRepository referenceRepository;
    private final BoardAccessPolicy accessPolicy;

    public BoardResponseMapper(
            CommentRepository commentRepository,
            PostLikeRepository postLikeRepository,
            BoardReferenceQueryRepository referenceRepository,
            BoardAccessPolicy accessPolicy
    ) {
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.referenceRepository = referenceRepository;
        this.accessPolicy = accessPolicy;
    }

    public List<PostListItemResponse> toListItems(
            List<Post> posts,
            Account currentAccount
    ) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<Long> postIds = posts.stream().map(Post::getPostId).toList();
        Map<Long, Long> commentCounts = loadCommentCounts(postIds);
        Set<Long> likedPostIds = loadLikedPostIds(postIds, currentAccount);
        Map<Long, RestaurantSummaryResponse> restaurants =
                referenceRepository.findRestaurants(
                        posts.stream()
                                .map(Post::getRestaurantId)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet())
                );

        Set<Long> authorIds = posts.stream()
                .map(post -> post.getAuthor().getAccountId())
                .collect(Collectors.toSet());
        Map<Long, AuthorRoleReference> authorRoles =
                referenceRepository.findAuthorRoleReferences(authorIds);

        return posts.stream().map(post -> {
            Long authorId = post.getAuthor().getAccountId();
            Long restaurantId = post.getRestaurantId();
            RestaurantSummaryResponse restaurant = restaurantId == null
                    ? null
                    : restaurants.get(restaurantId);
            return new PostListItemResponse(
                    post.getPostId(),
                    post.getTitle(),
                    makePreview(post.getContent()),
                    authorId,
                    post.getAuthor().getLoginId(),
                    displayNickname(post.getAuthor()),
                    displayAuthorRole(authorId, authorRoles),
                    post.getBoardType(),
                    post.getCategory(),
                    restaurantId,
                    restaurant,
                    post.getViewCount(),
                    commentCounts.getOrDefault(post.getPostId(), 0L),
                    post.getLikeCount(),
                    likedPostIds.contains(post.getPostId()),
                    isOwned(post.getAuthor(), currentAccount),
                    post.getCreatedAt(),
                    post.getUpdatedAt(),
                    post.isEdited()
            );
        }).toList();
    }

    public PostDetailResponse toDetail(Post post, Account currentAccount) {
        return toDetail(
                post,
                currentAccount,
                accessPolicy.displayRole(post.getAuthor()),
                post.getViewCount()
        );
    }

    public PostDetailResponse toDetail(
            Post post,
            Account currentAccount,
            long viewCount
    ) {
        return toDetail(
                post,
                currentAccount,
                accessPolicy.displayRole(post.getAuthor()),
                viewCount
        );
    }

    public PostDetailResponse toDetail(
            Post post,
            Account currentAccount,
            String authorRole
    ) {
        return toDetail(post, currentAccount, authorRole, post.getViewCount());
    }

    private PostDetailResponse toDetail(
            Post post,
            Account currentAccount,
            String authorRole,
            long viewCount
    ) {
        RestaurantSummaryResponse restaurant = post.getRestaurantId() == null
                ? null
                : referenceRepository.findRestaurant(post.getRestaurantId()).orElse(null);
        List<PostDetailResponse.MediaResponse> media = toMediaResponses(
                referenceRepository.findPostMedia(post.getPostId())
        );
        return new PostDetailResponse(
                post.getPostId(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getAccountId(),
                post.getAuthor().getLoginId(),
                displayNickname(post.getAuthor()),
                authorRole,
                post.getBoardType(),
                post.getCategory(),
                post.getRestaurantId(),
                post.getPublicRestaurantId(),
                restaurant,
                viewCount,
                commentRepository.countByPostPostIdAndStatus(
                        post.getPostId(),
                        CommentStatus.ACTIVE
                ),
                post.getLikeCount(),
                isLiked(post, currentAccount),
                isOwned(post.getAuthor(), currentAccount),
                isNewsManageable(post, currentAccount),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.isEdited(),
                media
        );
    }

    private boolean isNewsManageable(Post post, Account currentAccount) {
        if (post.getCategory() != PostCategory.NEWS || currentAccount == null) {
            return false;
        }

        Long restaurantId = post.getRestaurantId();
        Long publicRestaurantId = post.getPublicRestaurantId();
        if ((restaurantId == null) == (publicRestaurantId == null)) {
            return false;
        }
        if (publicRestaurantId != null) {
            return accessPolicy.isAdmin(currentAccount);
        }
        return referenceRepository.restaurantOwnedBy(
                restaurantId,
                currentAccount.getAccountId()
        );
    }

    public List<PostDetailResponse.MediaResponse> toMediaResponses(
            List<PostMediaReference> mediaRows
    ) {
        return mediaRows.stream().map(row -> {
            String status;
            String exposedUrl;
            String message;
            int progress;
            if (BoardReferenceQueryRepository.MEDIA_URL_PROCESSING.equals(
                    row.mediaUrl()
            )) {
                status = "PROCESSING";
                exposedUrl = null;
                progress = calculateProcessingProgress(row);
                message = "동영상 원본을 서버에서 처리하고 있습니다.";
            } else if (BoardReferenceQueryRepository.MEDIA_URL_FAILED.equals(
                    row.mediaUrl()
            )) {
                status = "FAILED";
                exposedUrl = null;
                progress = calculateProcessingProgress(row);
                message = "동영상 처리에 실패했습니다. 다시 첨부해 주세요.";
            } else {
                status = "READY";
                exposedUrl = row.mediaUrl();
                progress = 100;
                message = null;
            }
            return new PostDetailResponse.MediaResponse(
                    row.postMediaId(),
                    row.mediaType(),
                    exposedUrl,
                    row.mimeType(),
                    row.originalName(),
                    row.fileSize(),
                    row.displayOrder(),
                    status,
                    progress,
                    message
            );
        }).toList();
    }

    private int calculateProcessingProgress(PostMediaReference media) {
        if (media.fileSize() < 1 || media.storedSize() < 1) {
            return 0;
        }
        return (int) Math.min(
                99,
                media.storedSize() * 100L / media.fileSize()
        );
    }

    public CommentResponse toComment(Comment comment, Account currentAccount) {
        return toComment(
                comment,
                currentAccount,
                accessPolicy.displayRole(comment.getAuthor())
        );
    }

    public List<CommentResponse> toComments(
            List<Comment> comments,
            Account currentAccount
    ) {
        if (comments.isEmpty()) {
            return List.of();
        }

        Set<Long> authorIds = comments.stream()
                .map(comment -> comment.getAuthor().getAccountId())
                .collect(Collectors.toSet());
        Map<Long, AuthorRoleReference> authorRoles =
                referenceRepository.findAuthorRoleReferences(authorIds);

        return comments.stream().map(comment -> {
            Long authorId = comment.getAuthor().getAccountId();
            String authorRole = displayAuthorRole(authorId, authorRoles);
            return toComment(comment, currentAccount, authorRole);
        }).toList();
    }

    public CommentResponse toComment(
            Comment comment,
            Account currentAccount,
            String authorRole
    ) {
        boolean hasImage = comment.hasImage();
        return new CommentResponse(
                comment.getCommentId(),
                comment.getPost().getPostId(),
                comment.getParentCommentId(),
                comment.getAuthor().getAccountId(),
                comment.getAuthor().getLoginId(),
                displayNickname(comment.getAuthor()),
                authorRole,
                comment.getContent(),
                hasImage,
                hasImage
                        ? "/api/board/comments/" + comment.getCommentId() + "/image"
                        : null,
                hasImage ? comment.getImageOriginalName() : null,
                hasImage ? comment.getImageFileSize() : null,
                isOwned(comment.getAuthor(), currentAccount),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.isEdited()
        );
    }

    private Map<Long, Long> loadCommentCounts(List<Long> postIds) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : commentRepository.countActiveCommentsByPostIds(
                postIds,
                CommentStatus.ACTIVE
        )) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    private Set<Long> loadLikedPostIds(
            List<Long> postIds,
            Account currentAccount
    ) {
        if (currentAccount == null) {
            return Set.of();
        }
        return new HashSet<>(postLikeRepository.findLikedPostIds(
                currentAccount.getAccountId(),
                postIds
        ));
    }

    private boolean isLiked(Post post, Account currentAccount) {
        return currentAccount != null
                && postLikeRepository.existsByIdPostIdAndIdAccountId(
                        post.getPostId(),
                        currentAccount.getAccountId()
                );
    }

    private boolean isOwned(Account author, Account currentAccount) {
        return currentAccount != null
                && author.getAccountId().equals(currentAccount.getAccountId());
    }

    private String displayAuthorRole(
            Long authorId,
            Map<Long, AuthorRoleReference> authorRoles
    ) {
        AuthorRoleReference authorRole = authorRoles.getOrDefault(
                authorId,
                DEFAULT_AUTHOR_ROLE
        );
        return accessPolicy.displayRole(
                authorRole.authorityCodes(),
                authorRole.hasBusinessProfile()
        );
    }

    /**
     * 관리자가 계정을 삭제하면 실제로는 소프트 삭제(status=WITHDRAWN)되어 계정 행과
     * 작성자 연관관계는 그대로 남는다. 그래서 다른 이용자에게는 탈퇴 여부와 무관하게
     * 실명(닉네임)이 계속 노출되지 않도록, 커뮤니티 화면에 보여줄 닉네임은 이 메서드를
     * 거쳐서 탈퇴 상태면 "탈퇴한 회원"으로 가려준다.
     */
    private String displayNickname(Account author) {
        if (author == null || author.getStatus() == AccountStatus.WITHDRAWN) {
            return "탈퇴한 회원";
        }
        return author.getNickname();
    }

    private String makePreview(String content) {
        String normalized = content.replaceAll("\\s+", " ").strip();
        return normalized.length() <= PREVIEW_LENGTH
                ? normalized
                : normalized.substring(0, PREVIEW_LENGTH) + "…";
    }
}
