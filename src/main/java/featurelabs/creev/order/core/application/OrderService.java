package featurelabs.creev.order.core.application;

import featurelabs.creev.common.error.BusinessException;
import featurelabs.creev.common.error.ErrorCode;
import featurelabs.creev.order.core.application.command.CreateOrderCommand;
import featurelabs.creev.order.core.application.result.CreateOrderResult;
import featurelabs.creev.order.core.application.result.PreparedOrder;
import featurelabs.creev.order.core.domain.Order;
import featurelabs.creev.payment.infrastructure.PaymentFailureException;
import featurelabs.creev.payment.infrastructure.PgClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String DEFAULT_PAYMENT_FAILURE_MESSAGE = "PG 결제 실패";
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 255;

    private final OrderTransactionService orderTransactionService;
    private final PgClient pgClient;

    public OrderService(
            OrderTransactionService orderTransactionService,
            PgClient pgClient
    ) {
        this.orderTransactionService = orderTransactionService;
        this.pgClient = pgClient;
    }

    public CreateOrderResult createOrder(CreateOrderCommand command) {
        PreparedOrder preparedOrder = prepareOrder(command);

        if (!preparedOrder.newOrder()) {
            return CreateOrderResult.from(
                    preparedOrder.order(),
                    false
            );
        }

        Order paidOrder = processPayment(
                preparedOrder.orderId(),
                preparedOrder.amount()
        );

        return CreateOrderResult.from(
                paidOrder,
                true
        );
    }

    private PreparedOrder prepareOrder(CreateOrderCommand command) {
        try {
            return orderTransactionService.reserveStockAndCreateOrder(command);
        } catch (DataIntegrityViolationException e) {
            log.info(
                    "Data integrity violation occurred while creating order. Try to resolve as idempotent request. userId={}, productId={}, idempotencyKey={}",
                    command.userId(),
                    command.productId(),
                    command.idempotencyKey(),
                    e
            );

            return orderTransactionService.findIdempotentOrder(command);
        }
    }

    private Order processPayment(
            Long orderId,
            Long amount
    ) {
        try {
            pgClient.pay(amount);
        } catch (PaymentFailureException e) {
            throw handlePaymentFailure(orderId, e);
        }

        return confirmPaymentAfterPgSuccess(orderId);
    }

    private Order confirmPaymentAfterPgSuccess(Long orderId) {
        try {
            return orderTransactionService.confirmPayment(orderId);
        } catch (BusinessException e) {
            log.error(
                    "CRITICAL: payment succeeded but DB confirmation failed. orderId={}",
                    orderId,
                    e
            );

            markRecoveryRequiredSafely(orderId);

            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private BusinessException handlePaymentFailure(
            Long orderId,
            PaymentFailureException e
    ) {
        try {
            orderTransactionService.cancelPayment(
                    orderId,
                    normalizeFailureMessage(e)
            );
        } catch (Exception rollbackException) {
            log.error(
                    "CRITICAL: payment compensation failed. orderId={}",
                    orderId,
                    rollbackException
            );

            markRecoveryRequiredSafely(orderId);

            return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return new BusinessException(ErrorCode.PAYMENT_FAILED);
    }

    private String normalizeFailureMessage(PaymentFailureException e) {
        String message = e.getMessage();

        if (message == null || message.isBlank()) {
            return DEFAULT_PAYMENT_FAILURE_MESSAGE;
        }

        if (message.length() <= MAX_FAILURE_MESSAGE_LENGTH) {
            return message;
        }

        return message.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }

    private void markRecoveryRequiredSafely(Long orderId) {
        try {
            orderTransactionService.markRecoveryRequiredIfPossible(orderId);
        } catch (Exception e) {
            log.error(
                    "CRITICAL: failed to mark recovery required. orderId={}",
                    orderId,
                    e
            );
        }
    }
}
