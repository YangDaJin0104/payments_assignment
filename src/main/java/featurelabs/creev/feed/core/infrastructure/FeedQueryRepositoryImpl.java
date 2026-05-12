package featurelabs.creev.feed.core.infrastructure;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import featurelabs.creev.feed.core.domain.Feed;
import featurelabs.creev.feed.core.domain.QFeed;
import featurelabs.creev.feed.core.query.FeedCursor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FeedQueryRepositoryImpl implements FeedQueryRepository {

    private final JPAQueryFactory queryFactory;

    public FeedQueryRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public List<Feed> findFeedsByCursor(
            FeedCursor cursor,
            int limit
    ) {
        QFeed feed = QFeed.feed;

        JPAQuery<Feed> query = queryFactory
                .selectFrom(feed)
                .orderBy(
                        feed.createdAt.desc(),
                        feed.id.desc()
                )
                .limit(limit);

        BooleanExpression cursorCondition = cursorCondition(feed, cursor);

        if (cursorCondition != null) {
            query.where(cursorCondition);
        }

        return query.fetch();
    }

    private BooleanExpression cursorCondition(
            QFeed feed,
            FeedCursor cursor
    ) {
        if (cursor == null) {
            return null;
        }

        return feed.createdAt.lt(cursor.createdAt())
                .or(
                        feed.createdAt.eq(cursor.createdAt())
                                .and(feed.id.lt(cursor.id()))
                );
    }
}
