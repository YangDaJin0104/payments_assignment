package featurelabs.creev.order.core.application.command;

public record CreateOrderCommand(
        Long userId,
        Long productId,
        int quantity,
        String idempotencyKey,
        String requestHash
) {

    public CreateOrderCommand {
        idempotencyKey = idempotencyKey.trim();
    }
}
