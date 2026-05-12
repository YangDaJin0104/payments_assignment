package featurelabs.creev.feed.application;

import featurelabs.creev.common.error.BusinessException;
import featurelabs.creev.common.error.ErrorCode;
import featurelabs.creev.feed.api.dto.FeedSliceResponse;
import featurelabs.creev.feed.core.application.FeedService;
import featurelabs.creev.feed.core.domain.Feed;
import featurelabs.creev.feed.core.infrastructure.FeedRepository;
import featurelabs.creev.feed.core.support.FeedCursorCodec;
import featurelabs.creev.product.domain.Product;
import featurelabs.creev.product.infrastructure.ProductRepository;
import featurelabs.creev.support.PgClientTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(PgClientTestConfig.class)
class FeedServiceTest {

    @Autowired
    private FeedService feedService;

    @Autowired
    private FeedRepository feedRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private FeedCursorCodec feedCursorCodec;

    private Long productId;

    @BeforeEach
    void setUp() {
        feedRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        Product product = productRepository.save(
                Product.create("남성 아우터 게릴라 기획전", 59000L, 1)
        );

        productId = product.getId();

        saveFeed("피드 1", "2026-05-11T10:00:00");
        saveFeed("피드 2", "2026-05-11T10:01:00");
        saveFeed("피드 3", "2026-05-11T10:02:00");
        saveFeed("피드 4", "2026-05-11T10:03:00");
        saveFeed("피드 5", "2026-05-11T10:04:00");
    }

    @Test
    void 첫_페이지는_createdAt_desc_id_desc_순서로_조회한다() {
        // when
        FeedSliceResponse response = feedService.getFeeds(null, 2);

        // then
        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isNotBlank();

        assertThat(response.items())
                .extracting(item -> item.title())
                .containsExactly(
                        "피드 5",
                        "피드 4"
                );
    }

    @Test
    void nextCursor로_다음_페이지를_조회하면_중복_없이_이어진다() {
        // given
        FeedSliceResponse firstPage = feedService.getFeeds(null, 2);

        // when
        FeedSliceResponse secondPage = feedService.getFeeds(firstPage.nextCursor(), 2);

        // then
        assertThat(secondPage.items()).hasSize(2);
        assertThat(secondPage.hasNext()).isTrue();

        assertThat(secondPage.items())
                .extracting(item -> item.title())
                .containsExactly(
                        "피드 3",
                        "피드 2"
                );

        assertThat(secondPage.items())
                .extracting(item -> item.feedId())
                .doesNotContain(
                        firstPage.items().get(0).feedId(),
                        firstPage.items().get(1).feedId()
                );
    }

    @Test
    void 마지막_페이지는_hasNext_false이고_nextCursor가_null이다() {
        // given
        FeedSliceResponse firstPage = feedService.getFeeds(null, 2);
        FeedSliceResponse secondPage = feedService.getFeeds(firstPage.nextCursor(), 2);

        // when
        FeedSliceResponse lastPage = feedService.getFeeds(secondPage.nextCursor(), 2);

        // then
        assertThat(lastPage.items()).hasSize(1);
        assertThat(lastPage.hasNext()).isFalse();
        assertThat(lastPage.nextCursor()).isNull();

        assertThat(lastPage.items())
                .extracting(item -> item.title())
                .containsExactly("피드 1");
    }

    @Test
    void 같은_createdAt을_가진_피드는_id_desc_순서로_조회한다() {
        // given
        feedRepository.deleteAllInBatch();

        LocalDateTime sameTime = LocalDateTime.parse("2026-05-11T10:00:00");

        Feed olderIdFeed = feedRepository.save(
                Feed.create(
                        productId,
                        "같은 시간 피드 A",
                        "https://example.com/video/a.mp4",
                        "https://example.com/thumb/a.jpg",
                        sameTime
                )
        );

        Feed newerIdFeed = feedRepository.save(
                Feed.create(
                        productId,
                        "같은 시간 피드 B",
                        "https://example.com/video/b.mp4",
                        "https://example.com/thumb/b.jpg",
                        sameTime
                )
        );

        // when
        FeedSliceResponse response = feedService.getFeeds(null, 2);

        // then
        assertThat(response.items())
                .extracting(item -> item.feedId())
                .containsExactly(
                        newerIdFeed.getId(),
                        olderIdFeed.getId()
                );
    }

    @Test
    void 잘못된_cursor는_INVALID_CURSOR로_실패한다() {
        assertThatThrownBy(() -> feedService.getFeeds("invalid-cursor", 20))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_CURSOR)
                );
    }

    @Test
    void size가_최대값을_넘으면_최대값으로_제한한다() {
        // when
        FeedSliceResponse response = feedService.getFeeds(null, 1_000);

        // then
        assertThat(response.items()).hasSize(5);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void size가_1보다_작으면_INVALID_REQUEST로_실패한다() {
        assertThatThrownBy(() -> feedService.getFeeds(null, 0))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_REQUEST)
                );
    }

    private void saveFeed(
            String title,
            String createdAt
    ) {
        feedRepository.save(
                Feed.create(
                        productId,
                        title,
                        "https://example.com/video/" + title + ".mp4",
                        "https://example.com/thumb/" + title + ".jpg",
                        LocalDateTime.parse(createdAt)
                )
        );
    }
}
