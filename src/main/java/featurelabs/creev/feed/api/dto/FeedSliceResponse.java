package featurelabs.creev.feed.api.dto;

import java.util.List;

public record FeedSliceResponse(
        List<FeedResponse> items,
        String nextCursor,
        boolean hasNext
) {
}
