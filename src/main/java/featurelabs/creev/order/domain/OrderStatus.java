package featurelabs.creev.order.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PAYMENT_FAILED,
    EXPIRED,
    RECOVERY_REQUIRED
}
