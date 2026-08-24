package com.example.backend.board.domain.entity;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.PostCategory;
import com.example.backend.board.domain.type.PostStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account author;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    @Column(name = "public_restaurant_id")
    private Long publicRestaurantId;

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    private List<Comment> comments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 20)
    private BoardType boardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PostCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_edited", nullable = false)
    private boolean edited;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    @Column(name = "best_override")
    private Boolean bestOverride;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Post() {
    }

    private Post(
            Account author,
            Long restaurantId,
            Long publicRestaurantId,
            BoardType boardType,
            PostCategory category,
            String title,
            String content
    ) {
        this.author = Objects.requireNonNull(author);
        BoardType resolvedBoardType = Objects.requireNonNull(boardType);
        PostCategory resolvedCategory = Objects.requireNonNull(category);
        validateRestaurantReference(
                resolvedBoardType,
                resolvedCategory,
                restaurantId,
                publicRestaurantId
        );
        this.restaurantId = restaurantId;
        this.publicRestaurantId = publicRestaurantId;
        this.boardType = resolvedBoardType;
        this.category = resolvedCategory;
        this.pinned = resolvedCategory == PostCategory.NOTICE;
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.status = PostStatus.ACTIVE;
    }

    public static Post create(
            Account author,
            Long restaurantId,
            BoardType boardType,
            PostCategory category,
            String title,
            String content
    ) {
        return create(author, restaurantId, null, boardType, category, title, content);
    }

    public static Post create(
            Account author,
            Long restaurantId,
            Long publicRestaurantId,
            BoardType boardType,
            PostCategory category,
            String title,
            String content
    ) {
        return new Post(
                author,
                restaurantId,
                publicRestaurantId,
                boardType,
                category,
                title,
                content
        );
    }

    public void update(
            Long restaurantId,
            BoardType boardType,
            PostCategory category,
            String title,
            String content
    ) {
        update(restaurantId, null, boardType, category, title, content);
    }

    public void update(
            Long restaurantId,
            Long publicRestaurantId,
            BoardType boardType,
            PostCategory category,
            String title,
            String content
    ) {
        BoardType nextBoardType = Objects.requireNonNull(boardType);
        PostCategory nextCategory = Objects.requireNonNull(category);
        String nextTitle = Objects.requireNonNull(title);
        String nextContent = Objects.requireNonNull(content);
        validateRestaurantReference(
                nextBoardType,
                nextCategory,
                restaurantId,
                publicRestaurantId
        );
        boolean changed = !Objects.equals(this.restaurantId, restaurantId)
                || !Objects.equals(this.publicRestaurantId, publicRestaurantId)
                || this.boardType != nextBoardType
                || this.category != nextCategory
                || !Objects.equals(this.title, nextTitle)
                || !Objects.equals(this.content, nextContent);

        this.restaurantId = restaurantId;
        this.publicRestaurantId = publicRestaurantId;
        this.boardType = nextBoardType;
        this.category = nextCategory;
        this.title = nextTitle;
        this.content = nextContent;
        this.updatedAt = LocalDateTime.now();
        if (changed) {
            this.edited = true;
        }
    }

    public void updatePinnedState(PostCategory category, boolean pinned) {
        PostCategory nextCategory = Objects.requireNonNull(category);
        if (nextCategory == PostCategory.NEWS || nextCategory == PostCategory.NOTICE) {
            throw new IllegalArgumentException(
                    "공지 고정 상태에서는 일반 게시글 카테고리를 선택해야 합니다."
            );
        }
        this.category = nextCategory;
        this.pinned = pinned;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateBestOverride(Boolean bestOverride) {
        this.bestOverride = bestOverride;
        this.updatedAt = LocalDateTime.now();
    }

    private static void validateRestaurantReference(
            BoardType boardType,
            PostCategory category,
            Long restaurantId,
            Long publicRestaurantId
    ) {
        if (category == PostCategory.NEWS) {
            if (boardType != BoardType.GENERAL) {
                throw new IllegalArgumentException(
                        "식당 소식은 일반 게시 공간에만 저장할 수 있습니다."
                );
            }
            if ((restaurantId == null) == (publicRestaurantId == null)) {
                throw new IllegalArgumentException(
                        "식당 소식은 자체 등록 음식점 또는 공공데이터 음식점 중 하나만 연결해야 합니다."
                );
            }
            return;
        }
        if (restaurantId != null && publicRestaurantId != null) {
            throw new IllegalArgumentException(
                    "게시글에는 자체 등록 음식점과 공공데이터 음식점을 동시에 연결할 수 없습니다."
            );
        }
    }

    public void increaseLikeCount() {
        likeCount++;
    }

    public void decreaseLikeCount() {
        if (likeCount > 0) {
            likeCount--;
        }
    }

    public void clearLikeCount() {
        likeCount = 0;
    }

    public void softDelete(LocalDateTime deletedAt) {
        this.status = PostStatus.DELETED;
        this.deletedAt = Objects.requireNonNull(deletedAt);
        this.updatedAt = deletedAt;
    }

    public boolean isDeleted() {
        return status == PostStatus.DELETED;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = PostStatus.ACTIVE;
        }
        if (category == PostCategory.NOTICE) {
            pinned = true;
        }
        createdAt = now;
        updatedAt = now;
    }

    public Long getPostId() {
        return postId;
    }

    public Account getAuthor() {
        return author;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public Long getPublicRestaurantId() {
        return publicRestaurantId;
    }

    public BoardType getBoardType() {
        return boardType;
    }

    public PostCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public PostStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public boolean isEdited() {
        return edited;
    }

    public boolean isPinned() {
        return pinned;
    }

    public Boolean getBestOverride() {
        return bestOverride;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
