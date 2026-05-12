package featurelabs.creev.feed.core.infrastructure;

import featurelabs.creev.feed.core.domain.Feed;
import featurelabs.creev.feed.core.query.FeedCursor;

import java.util.List;

public interface FeedQueryRepository {

    List<Feed> findFeedsByCursor(FeedCursor cursor, int limit);
}
