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

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_message")
    private String failureMessage;

    protected Payment() {
    }

    private Payment(
            Long orderId,
            Long amount
    ) {
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.READY;
    }

    public static Payment createReady(Long orderId, Long amount) {
        return new Payment(orderId, amount);
    }

    public void markSuccess() {
        validateReadyStatus();

        this.status = PaymentStatus.SUCCESS;
        this.failureMessage = null;
    }

    public void markFailed(String failureMessage) {
        validateReadyStatus();

        this.status = PaymentStatus.FAILED;
        this.failureMessage = failureMessage;
    }

    private void validateReadyStatus() {
        if (this.status != PaymentStatus.READY) {
            throw new BusinessException(ErrorCode.INVALID_PAYMENT_STATUS);
        }
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureMessage() {
        return failureMessage;
    }
}
