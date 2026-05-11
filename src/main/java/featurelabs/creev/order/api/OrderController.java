package featurelabs.creev.order.api;

import featurelabs.creev.common.response.ApiResponse;
import featurelabs.creev.order.api.dto.CreateOrderRequest;
import featurelabs.creev.order.api.dto.CreateOrderResponse;
import featurelabs.creev.order.core.application.OrderService;
import featurelabs.creev.order.core.application.command.CreateOrderCommand;
import featurelabs.creev.order.core.application.result.CreateOrderResult;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderCommandMapper orderCommandMapper;
    private final OrderResponsePolicy orderResponsePolicy;

    public OrderController(
            OrderService orderService,
            OrderCommandMapper orderCommandMapper,
            OrderResponsePolicy orderResponsePolicy
    ) {
        this.orderService = orderService;
        this.orderCommandMapper = orderCommandMapper;
        this.orderResponsePolicy = orderResponsePolicy;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CreateOrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request
    ) {
        CreateOrderCommand command = orderCommandMapper.toCommand(request);

        CreateOrderResult result = orderService.createOrder(command);

        OrderResponsePolicy.ResponseSpec responseSpec = orderResponsePolicy.resolve(result);

        return ResponseEntity
                .status(responseSpec.httpStatus())
                .body(ApiResponse.success(
                        CreateOrderResponse.from(result),
                        responseSpec.message(),
                        responseSpec.httpStatus().value()
                ));
    }
}
