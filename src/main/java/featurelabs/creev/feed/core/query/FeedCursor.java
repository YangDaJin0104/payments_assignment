package featurelabs.creev.feed.core.query;

import java.time.LocalDateTime;

public record FeedCursor(
        LocalDateTime createdAt,
        Long id
) {
}
