package featurelabs.creev.order.core.domain;

import featurelabs.creev.common.error.BusinessException;
import featurelabs.creev.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity(name = "OrderEntity")
@Table(
        name = "orders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_order_user_idempotency_key",
                        columnNames = {"user_id", "idempotency_key"}
                )
        }
)
public class Order {

    private static final int DEFAULT_RESERVATION_MINUTES = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "reservation_expires_at", nullable = false)
    private LocalDateTime reservationExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    protected Order() {
    }

    private Order(
            Long userId,
            Long productId,
            Integer quantity,
            Long amount,
            String idempotencyKey,
            String requestHash,
            LocalDateTime reservationExpiresAt
    ) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.reservationExpiresAt = reservationExpiresAt;
        this.status = OrderStatus.PENDING_PAYMENT;
    }

    public static Order createPendingPayment(Long userId, Long productId, Integer quantity, Long amount, String idempotencyKey, String requestHash, LocalDateTime now) {
        return new Order(userId, productId, quantity, amount, idempotencyKey, requestHash, now.plusMinutes(DEFAULT_RESERVATION_MINUTES));
    }

    public boolean hasSameRequestHash(String requestHash) {
        return this.requestHash.equals(requestHash);
    }

    public boolean isPendingPayment() {
        return this.status == OrderStatus.PENDING_PAYMENT;
    }

    public boolean isActiveOrder() {
        return this.status == OrderStatus.PENDING_PAYMENT || this.status == OrderStatus.PAID;
    }

    public void markPaid() {
        validatePendingPaymentStatus();

        this.status = OrderStatus.PAID;
    }

    public void markPaymentFailed() {
        validatePendingPaymentStatus();

        this.status = OrderStatus.PAYMENT_FAILED;
    }

    public void markExpired() {
        validatePendingPaymentStatus();

        this.status = OrderStatus.EXPIRED;
    }

    public void markRecoveryRequired() {
        this.status = OrderStatus.RECOVERY_REQUIRED;
    }

    private void validatePendingPaymentStatus() {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public Long getAmount() {
        return amount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public LocalDateTime getReservationExpiresAt() {
        return reservationExpiresAt;
    }

    public OrderStatus getStatus() {
        return status;
    }
}
