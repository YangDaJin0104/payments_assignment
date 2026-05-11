package featurelabs.creev.order.api.dto;

import featurelabs.creev.order.core.domain.Order;

public record CreateOrderResponse(
        Long orderId,
        Long productId,
        Integer quantity,
        Long amount,
        String status
) {

    public static CreateOrderResponse from(Order order) {
        return new CreateOrderResponse(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getAmount(),
                order.getStatus().name()
        );
    }
}
