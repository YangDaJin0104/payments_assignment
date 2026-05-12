package featurelabs.creev.feed.api;

import featurelabs.creev.common.response.ApiResponse;
import featurelabs.creev.feed.api.dto.FeedSliceResponse;
import featurelabs.creev.feed.core.application.FeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feeds")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FeedSliceResponse>> getFeeds(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        FeedSliceResponse response = feedService.getFeeds(cursor, size);

        return ResponseEntity.ok(ApiResponse.success(response, "피드 목록 조회에 성공했습니다.", 200));
    }
}
