package featurelabs.creev.order.api;

import featurelabs.creev.order.api.dto.CreateOrderRequest;
import featurelabs.creev.order.core.application.command.CreateOrderCommand;
import featurelabs.creev.order.core.application.support.RequestHashGenerator;
import org.springframework.stereotype.Component;

@Component
public class OrderCommandMapper {

    private final RequestHashGenerator requestHashGenerator;

    public OrderCommandMapper(RequestHashGenerator requestHashGenerator) {
        this.requestHashGenerator = requestHashGenerator;
    }

    public CreateOrderCommand toCommand(CreateOrderRequest request) {
        String requestHash = requestHashGenerator.generate(
                request.userId(),
                request.productId(),
                request.quantity()
        );

        return new CreateOrderCommand(
                request.userId(),
                request.productId(),
                request.quantity(),
                request.idempotencyKey().trim(),
                requestHash
        );
    }
}
