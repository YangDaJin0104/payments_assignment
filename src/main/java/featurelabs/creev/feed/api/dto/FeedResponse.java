package featurelabs.creev.feed.api.dto;


import featurelabs.creev.feed.core.domain.Feed;

import java.time.LocalDateTime;

public record FeedResponse(
        Long feedId,
        Long productId,
        String title,
        String videoUrl,
        String thumbnailUrl,
        LocalDateTime createdAt
) {

    public static FeedResponse from(Feed feed) {
        return new FeedResponse(
                feed.getId(),
                feed.getProductId(),
                feed.getTitle(),
                feed.getVideoUrl(),
                feed.getThumbnailUrl(),
                feed.getCreatedAt()
        );
    }
}
