package featurelabs.creev.feed.core.infrastructure;

import featurelabs.creev.feed.core.domain.Feed;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedRepository extends JpaRepository<Feed, Long>, FeedQueryRepository {
}
