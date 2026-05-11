package featurelabs.creev.order.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(

        @NotNull(message = "사용자 ID는 필수입니다.")
        Long userId,

        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @NotNull(message = "주문 수량은 필수입니다.")
        @Min(value = 1, message = "주문 수량은 1개 이상이어야 합니다.")
        Integer quantity,

        @NotBlank(message = "멱등키는 필수입니다.")
        @Size(max = 100, message = "멱등키는 100자를 초과할 수 없습니다.")
        String idempotencyKey
) {
}
