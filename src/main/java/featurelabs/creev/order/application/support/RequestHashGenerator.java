package featurelabs.creev.order.application.support;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RequestHashGenerator {

    private static final String DELIMITER = ":";

    public String generate(
            Long userId,
            Long productId,
            int quantity
    ) {
        String rawValue = userId
                + DELIMITER
                + productId
                + DELIMITER
                + quantity;

        return sha256(rawValue);
    }

    private String sha256(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", e);
        }
    }
}
