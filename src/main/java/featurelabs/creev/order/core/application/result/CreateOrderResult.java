package featurelabs.creev.order.core.application.result;


import featurelabs.creev.order.core.domain.Order;
import featurelabs.creev.order.core.domain.OrderStatus;

public record CreateOrderResult(
        Long orderId,
        Long productId,
        Integer quantity,
        Long amount,
        OrderStatus status,
        boolean newOrder
) {

    public static CreateOrderResult from(
            Order order,
            boolean newOrder
    ) {
        return new CreateOrderResult(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getAmount(),
                order.getStatus(),
                newOrder
        );
    }
}
