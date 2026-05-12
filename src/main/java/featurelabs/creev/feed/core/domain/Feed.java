package featurelabs.creev.feed.core.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "feeds",
        indexes = {
                @Index(
                        name = "idx_feeds_created_at_id",
                        columnList = "created_at, id"
                )
        }
)
public class Feed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String title;

    @Column(name = "video_url", nullable = false)
    private String videoUrl;

    @Column(name = "thumbnail_url", nullable = false)
    private String thumbnailUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Feed() {
    }

    private Feed(
            Long productId,
            String title,
            String videoUrl,
            String thumbnailUrl,
            LocalDateTime createdAt
    ) {
        this.productId = productId;
        this.title = title;
        this.videoUrl = videoUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.createdAt = createdAt;
    }

    public static Feed create(
            Long productId,
            String title,
            String videoUrl,
            String thumbnailUrl,
            LocalDateTime createdAt
    ) {
        return new Feed(
                productId,
                title,
                videoUrl,
                thumbnailUrl,
                createdAt
        );
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getTitle() {
        return title;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
