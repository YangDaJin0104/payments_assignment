package featurelabs.creev.feed.core.support;

import featurelabs.creev.common.error.BusinessException;
import featurelabs.creev.common.error.ErrorCode;
import featurelabs.creev.feed.core.query.FeedCursor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
public class FeedCursorCodec {

    private static final String RAW_DELIMITER = "|";
    private static final String SPLIT_DELIMITER = "\\|";

    public String encode(
            LocalDateTime createdAt,
            Long id
    ) {
        if (createdAt == null || id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }

        String rawCursor = createdAt + RAW_DELIMITER + id;

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
    }

    public FeedCursor decodeNullable(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8
            );

            String[] parts = decoded.split(SPLIT_DELIMITER, -1);

            if (parts.length != 2) {
                throw new IllegalArgumentException("커서 형식이 올바르지 않습니다.");
            }

            LocalDateTime createdAt = LocalDateTime.parse(parts[0]);
            Long id = Long.parseLong(parts[1]);

            if (id <= 0) {
                throw new IllegalArgumentException("커서 ID는 1 이상이어야 합니다.");
            }

            return new FeedCursor(
                    createdAt,
                    id
            );
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
