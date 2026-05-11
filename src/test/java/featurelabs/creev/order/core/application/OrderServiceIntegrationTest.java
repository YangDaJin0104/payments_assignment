package featurelabs.creev.order.core.application;

import featurelabs.creev.common.error.BusinessException;
import featurelabs.creev.common.error.ErrorCode;
import featurelabs.creev.order.core.application.command.CreateOrderCommand;
import featurelabs.creev.order.core.application.result.CreateOrderResult;
import featurelabs.creev.order.core.application.support.RequestHashGenerator;
import featurelabs.creev.order.core.domain.Order;
import featurelabs.creev.order.core.domain.OrderStatus;
import featurelabs.creev.order.core.domain.Payment;
import featurelabs.creev.order.core.domain.PaymentStatus;
import featurelabs.creev.order.core.infrastructure.OrderRepository;
import featurelabs.creev.order.core.infrastructure.PaymentRepository;
import featurelabs.creev.product.domain.Product;
import featurelabs.creev.product.domain.ProductStock;
import featurelabs.creev.product.infrastructure.ProductRepository;
import featurelabs.creev.product.infrastructure.ProductStockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import featurelabs.creev.support.PgClientTestConfig;
import featurelabs.creev.support.TestPgClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(PgClientTestConfig.class)
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductStockRepository productStockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RequestHashGenerator requestHashGenerator;

    @Autowired
    private TestPgClient testPgClient;

    private Long productId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        productStockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        testPgClient.reset();

        Product product = productRepository.save(
                Product.create("남성 아우터 게릴라 기획전", 59000L, 1)
        );

        productStockRepository.save(
                ProductStock.create(product.getId(), 100)
        );

        productId = product.getId();
    }

    @Test
    void 결제_성공_시_주문을_PAID로_변경하고_예약_재고를_판매_재고로_확정한다() {
        // given
        testPgClient.success();

        CreateOrderCommand command = createCommand(
                1L,
                productId,
                1,
                "success-key"
        );

        // when
        CreateOrderResult result = orderService.createOrder(command);

        // then
        assertThat(result.newOrder()).isTrue();
        assertThat(result.status()).isEqualTo(OrderStatus.PAID);

        Order order = getOnlyOrder();
        Payment payment = getPayment(order);
        ProductStock stock = getStock(productId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(stock.getReservedStock()).isZero();
        assertThat(stock.getSoldStock()).isEqualTo(1);
        assertThat(stock.availableStock()).isEqualTo(99);
        assertThat(testPgClient.getCallCount()).isEqualTo(1);
    }

    @Test
    void PG_결제_실패_시_주문과_결제를_실패_처리하고_예약_재고를_복구한다() {
        // given
        testPgClient.fail();

        CreateOrderCommand command = createCommand(
                1L,
                productId,
                1,
                "payment-fail-key"
        );

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(command))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED)
                );

        Order order = getOnlyOrder();
        Payment payment = getPayment(order);
        ProductStock stock = getStock(productId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(stock.getReservedStock()).isZero();
        assertThat(stock.getSoldStock()).isZero();
        assertThat(stock.availableStock()).isEqualTo(100);
        assertThat(testPgClient.getCallCount()).isEqualTo(1);
    }

    @Test
    void 같은_멱등키의_동일_요청은_기존_주문을_반환하고_PG를_재호출하지_않는다() {
        // given
        testPgClient.success();

        CreateOrderCommand command = createCommand(
                1L,
                productId,
                1,
                "same-idempotency-key"
        );

        // when
        CreateOrderResult first = orderService.createOrder(command);
        CreateOrderResult second = orderService.createOrder(command);

        // then
        assertThat(first.orderId()).isEqualTo(second.orderId());
        assertThat(first.newOrder()).isTrue();
        assertThat(second.newOrder()).isFalse();

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(testPgClient.getCallCount()).isEqualTo(1);

        ProductStock stock = getStock(productId);

        assertThat(stock.getReservedStock()).isZero();
        assertThat(stock.getSoldStock()).isEqualTo(1);
        assertThat(stock.availableStock()).isEqualTo(99);
    }

    private CreateOrderCommand createCommand(
            Long userId,
            Long productId,
            int quantity,
            String idempotencyKey
    ) {
        String requestHash = requestHashGenerator.generate(
                userId,
                productId,
                quantity
        );

        return new CreateOrderCommand(
                userId,
                productId,
                quantity,
                idempotencyKey,
                requestHash
        );
    }

    private Order getOnlyOrder() {
        return orderRepository.findAll()
                .stream()
                .findFirst()
                .orElseThrow();
    }

    private Payment getPayment(Order order) {
        return paymentRepository.findByOrderId(order.getId())
                .orElseThrow();
    }

    private ProductStock getStock(Long productId) {
        return productStockRepository.findByProductId(productId)
                .orElseThrow();
    }
}
