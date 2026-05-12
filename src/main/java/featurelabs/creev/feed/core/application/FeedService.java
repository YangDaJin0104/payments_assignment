package featurelabs.creev.feed.core.application;

import featurelabs.creev.common.error.BusinessException;
import featurelabs.creev.common.error.ErrorCode;
import featurelabs.creev.feed.api.dto.FeedResponse;
import featurelabs.creev.feed.api.dto.FeedSliceResponse;
import featurelabs.creev.feed.core.domain.Feed;
import featurelabs.creev.feed.core.infrastructure.FeedRepository;
import featurelabs.creev.feed.core.query.FeedCursor;
import featurelabs.creev.feed.core.support.FeedCursorCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FeedService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final FeedRepository feedRepository;
    private final FeedCursorCodec feedCursorCodec;

    public FeedService(
            FeedRepository feedRepository,
            FeedCursorCodec feedCursorCodec
    ) {
        this.feedRepository = feedRepository;
        this.feedCursorCodec = feedCursorCodec;
    }

    @Transactional(readOnly = true)
    public FeedSliceResponse getFeeds(
            String cursor,
            Integer size
    ) {
        int normalizedSize = normalizeSize(size);
        FeedCursor feedCursor = feedCursorCodec.decodeNullable(cursor);

        List<Feed> fetchedFeeds = feedRepository.findFeedsByCursor(
                feedCursor,
                normalizedSize + 1
        );

        boolean hasNext = fetchedFeeds.size() > normalizedSize;

        List<Feed> contents = hasNext
                ? fetchedFeeds.subList(0, normalizedSize)
                : fetchedFeeds;

        String nextCursor = createNextCursor(contents, hasNext);

        List<FeedResponse> items = contents.stream()
                .map(FeedResponse::from)
                .toList();

        return new FeedSliceResponse(items, nextCursor, hasNext);
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }

        if (size < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        return Math.min(size, MAX_SIZE);
    }

    private String createNextCursor(
            List<Feed> contents,
            boolean hasNext
    ) {
        if (!hasNext || contents.isEmpty()) {
            return null;
        }

        Feed lastFeed = contents.get(contents.size() - 1);

        return feedCursorCodec.encode(lastFeed.getCreatedAt(), lastFeed.getId());
    }
}
