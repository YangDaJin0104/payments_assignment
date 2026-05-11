package featurelabs.creev.order.core.application;

import featurelabs.creev.common.error.BusinessException;
import featurelabs.creev.common.error.ErrorCode;
import featurelabs.creev.order.core.application.command.CreateOrderCommand;
import featurelabs.creev.order.core.application.result.PreparedOrder;
import featurelabs.creev.order.core.domain.Order;
import featurelabs.creev.order.core.domain.OrderStatus;
import featurelabs.creev.order.core.domain.Payment;
import featurelabs.creev.order.core.infrastructure.OrderRepository;
import featurelabs.creev.order.core.infrastructure.PaymentRepository;
import featurelabs.creev.product.domain.Product;
import featurelabs.creev.product.domain.ProductStock;
import featurelabs.creev.product.infrastructure.ProductRepository;
import featurelabs.creev.product.infrastructure.ProductStockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderTransactionService {

    private static final List<OrderStatus> ACTIVE_ORDER_STATUSES = List.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PAID
    );

    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public OrderTransactionService(
            ProductRepository productRepository,
            ProductStockRepository productStockRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository
    ) {
        this.productRepository = productRepository;
        this.productStockRepository = productStockRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PreparedOrder reserveStockAndCreateOrder(
            CreateOrderCommand command
    ) {
        Order existingOrder = findExistingOrderOrNull(command);

        if (existingOrder != null) {
            validateSameRequestHash(existingOrder, command.requestHash());
            return PreparedOrder.existingOrder(existingOrder);
        }

        Product product = productRepository.findById(command.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        validateMaxPurchaseQuantity(product, command.quantity());

        ProductStock stock = productStockRepository.findByProductIdForUpdate(command.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        validateNotDuplicateActiveOrder(
                command.userId(),
                command.productId()
        );

        stock.reserve(command.quantity());

        Long amount = product.calculateAmount(command.quantity());

        Order order = Order.createPendingPayment(
                command.userId(),
                command.productId(),
                command.quantity(),
                amount,
                command.idempotencyKey(),
                command.requestHash(),
                LocalDateTime.now()
        );

        Order savedOrder = orderRepository.save(order);

        Payment payment = Payment.createReady(
                savedOrder.getId(),
                amount
        );

        paymentRepository.save(payment);

        return PreparedOrder.newOrder(savedOrder);
    }

    @Transactional(readOnly = true)
    public PreparedOrder findIdempotentOrder(
            CreateOrderCommand command
    ) {
        Order existingOrder = orderRepository.findByUserIdAndIdempotencyKey(
                        command.userId(),
                        command.idempotencyKey()
                )
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR));

        validateSameRequestHash(existingOrder, command.requestHash());

        return PreparedOrder.existingOrder(existingOrder);
    }

    @Transactional
    public Order confirmPayment(
            Long orderId
    ) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        ProductStock stock = productStockRepository.findByProductIdForUpdate(order.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        stock.confirm(order.getQuantity());
        order.markPaid();
        payment.markSuccess();

        return order;
    }

    @Transactional
    public void cancelPayment(
            Long orderId,
            String failureMessage
    ) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        ProductStock stock = productStockRepository.findByProductIdForUpdate(order.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STOCK_NOT_FOUND));

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        stock.release(order.getQuantity());
        order.markPaymentFailed();
        payment.markFailed(failureMessage);
    }

    @Transactional
    public void markRecoveryRequiredIfPossible(
            Long orderId
    ) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        order.markRecoveryRequired();
    }

    private Order findExistingOrderOrNull(
            CreateOrderCommand command
    ) {
        return orderRepository.findByUserIdAndIdempotencyKey(
                        command.userId(),
                        command.idempotencyKey()
                )
                .orElse(null);
    }

    private void validateSameRequestHash(
            Order existingOrder,
            String requestHash
    ) {
        if (!existingOrder.hasSameRequestHash(requestHash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
    }

    private void validateMaxPurchaseQuantity(
            Product product,
            int quantity
    ) {
        if (product.exceedsMaxPurchaseQuantity(quantity)) {
            throw new BusinessException(ErrorCode.EXCEEDS_MAX_PURCHASE_QUANTITY);
        }
    }

    private void validateNotDuplicateActiveOrder(
            Long userId,
            Long productId
    ) {
        boolean existsActiveOrder = orderRepository.existsByUserIdAndProductIdAndStatusIn(
                userId,
                productId,
                ACTIVE_ORDER_STATUSES
        );

        if (existsActiveOrder) {
            throw new BusinessException(ErrorCode.DUPLICATE_ORDER);
        }
    }
}
