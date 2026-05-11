package featurelabs.creev.order.api.dto;

import featurelabs.creev.order.core.application.result.CreateOrderResult;

public record CreateOrderResponse(
        Long orderId,
        Long productId,
        Integer quantity,
        Long amount,
        String status
) {

    public static CreateOrderResponse from(
            CreateOrderResult result
    ) {
        return new CreateOrderResponse(
                result.orderId(),
                result.productId(),
                result.quantity(),
                result.amount(),
                result.status().name()
        );
    }
}
