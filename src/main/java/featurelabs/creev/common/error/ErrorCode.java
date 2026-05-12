package featurelabs.creev.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INVALID_ORDER_QUANTITY(HttpStatus.BAD_REQUEST, "주문 수량은 1개 이상이어야 합니다."),
    EXCEEDS_MAX_PURCHASE_QUANTITY(HttpStatus.BAD_REQUEST, "해당 상품은 1인당 구매 가능한 수량을 초과했습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "커서 값이 올바르지 않습니다."),

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    STOCK_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 재고 정보를 찾을 수 없습니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문 정보를 찾을 수 없습니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),

    DUPLICATE_ORDER(HttpStatus.CONFLICT, "이미 처리 중이거나 완료된 주문이 존재합니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "동일한 멱등키로 다른 주문 요청을 처리할 수 없습니다."),
    PRODUCT_OUT_OF_STOCK(HttpStatus.CONFLICT, "해당 상품의 재고가 소진되었습니다."),
    INVALID_ORDER_STATUS(HttpStatus.CONFLICT, "현재 주문 상태에서는 처리할 수 없습니다."),
    INVALID_PAYMENT_STATUS(HttpStatus.CONFLICT, "현재 결제 상태에서는 처리할 수 없습니다."),

    PAYMENT_FAILED(HttpStatus.BAD_GATEWAY, "결제 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    LOCK_TIMEOUT(HttpStatus.SERVICE_UNAVAILABLE, "주문 요청이 일시적으로 많아 처리하지 못했습니다. 잠시 후 다시 시도해 주세요."),

    INVALID_STOCK_STATE(HttpStatus.INTERNAL_SERVER_ERROR, "재고 상태가 올바르지 않습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
