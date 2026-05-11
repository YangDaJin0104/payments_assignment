package featurelabs.creev.order.api;

import featurelabs.creev.common.error.BusinessException;
import featurelabs.creev.common.error.ErrorCode;
import featurelabs.creev.order.core.application.result.CreateOrderResult;
import featurelabs.creev.order.core.domain.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderResponsePolicy {

    public ResponseSpec resolve(CreateOrderResult result) {
        if (result.status() == OrderStatus.RECOVERY_REQUIRED) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        if (result.newOrder()) {
            return new ResponseSpec(
                    HttpStatus.CREATED,
                    "주문 결제가 완료되었습니다."
            );
        }

        return switch (result.status()) {
            case PENDING_PAYMENT -> new ResponseSpec(
                    HttpStatus.ACCEPTED,
                    "이미 접수된 주문이 결제 처리 중입니다."
            );

            case PAID -> new ResponseSpec(
                    HttpStatus.OK,
                    "이미 결제가 완료된 주문입니다."
            );

            case PAYMENT_FAILED -> new ResponseSpec(
                    HttpStatus.OK,
                    "이미 접수된 주문이며 결제 실패 상태입니다. 재시도하려면 새로운 멱등키로 요청해 주세요."
            );

            case EXPIRED -> new ResponseSpec(
                    HttpStatus.OK,
                    "이미 접수된 주문이며 예약이 만료된 상태입니다. 재시도하려면 새로운 멱등키로 요청해 주세요."
            );

            case RECOVERY_REQUIRED -> throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        };
    }

    public record ResponseSpec(
            HttpStatus httpStatus,
            String message
    ) {
    }
}
