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
import featurelabs.creev.support.PgClientTestConfig;
import featurelabs.creev.support.TestPgClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(PgClientTestConfig.class)
class OrderServicePaymentFailureTest {

    private static final int TOTAL_STOCK = 100;
    private static final long PRODUCT_PRICE = 59_000L;
    private static final String PG_FAILURE_MESSAGE = "테스트 PG 결제 실패";

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
        testPgClient.fail();

        Product product = productRepository.save(
                Product.create(
                        "남성 아우터 게릴라 기획전",
                        PRODUCT_PRICE,
                        1
                )
        );

        productStockRepository.save(
                ProductStock.create(
                        product.getId(),
                        TOTAL_STOCK
                )
        );

        productId = product.getId();
    }

    @Test
    void PG_결제_실패_시_주문과_결제를_실패_상태로_남기고_예약_재고를_복구한다() {
        // given
        CreateOrderCommand command = createCommand(
                1L,
                productId,
                1,
                "payment-failure-key"
        );

        // when & then
        assertPaymentFailed(command);

        assertThat(testPgClient.getCallCount())
                .as("PG는 주문 처리 중 한 번만 호출되어야 한다.")
                .isEqualTo(1);

        Order order = assertSingleOrder(OrderStatus.PAYMENT_FAILED);
        assertSinglePayment(order, PaymentStatus.FAILED, PG_FAILURE_MESSAGE);
        assertStockRestored(productId);
    }

    @Test
    void PG_실패_후_같은_멱등키로_재요청하면_기존_실패_주문을_반환하고_PG를_다시_호출하지_않는다() {
        // given
        CreateOrderCommand command = createCommand(
                1L,
                productId,
                1,
                "failed-idempotency-key"
        );

        assertPaymentFailed(command);

        assertThat(testPgClient.getCallCount())
                .as("첫 주문에서만 PG가 호출되어야 한다.")
                .isEqualTo(1);

        // when
        CreateOrderResult retryResult = orderService.createOrder(command);

        // then
        assertThat(retryResult.newOrder())
                .as("같은 멱등키 재요청은 신규 주문이 아니어야 한다.")
                .isFalse();

        assertThat(retryResult.status())
                .as("기존 실패 주문 상태를 그대로 반환해야 한다.")
                .isEqualTo(OrderStatus.PAYMENT_FAILED);

        assertThat(testPgClient.getCallCount())
                .as("기존 실패 주문을 반환할 때 PG를 다시 호출하면 안 된다.")
                .isEqualTo(1);

        Order order = assertSingleOrder(OrderStatus.PAYMENT_FAILED);

        assertThat(retryResult.orderId())
                .as("재요청 응답은 기존 실패 주문을 가리켜야 한다.")
                .isEqualTo(order.getId());

        assertSinglePayment(order, PaymentStatus.FAILED, PG_FAILURE_MESSAGE);
        assertStockRestored(productId);
    }

    private void assertPaymentFailed(CreateOrderCommand command) {
        assertThatThrownBy(() -> orderService.createOrder(command))
                .as("PG 실패 시 클라이언트 관점에서는 PAYMENT_FAILED 예외가 발생해야 한다.")
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.PAYMENT_FAILED)
                );
    }

    private Order assertSingleOrder(OrderStatus expectedStatus) {
        List<Order> orders = orderRepository.findAll();

        assertThat(orders)
                .as("PG 실패여도 실패 상태 주문은 이력으로 남아야 한다.")
                .hasSize(1);

        Order order = orders.get(0);

        assertThat(order.getStatus())
                .as("PG 실패 후 예약 재고 해제까지 완료되면 주문 상태는 PAYMENT_FAILED여야 한다.")
                .isEqualTo(expectedStatus);

        return order;
    }

    private Payment assertSinglePayment(
            Order order,
            PaymentStatus expectedStatus,
            String expectedFailureMessage
    ) {
        List<Payment> payments = paymentRepository.findAll();

        assertThat(payments)
                .as("PG 실패여도 실패 상태 결제 이력은 남아야 한다.")
                .hasSize(1);

        Payment payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow();

        assertThat(payment.getStatus())
                .as("PG 실패 후 결제 이력은 FAILED 상태여야 한다.")
                .isEqualTo(expectedStatus);

        assertThat(payment.getFailureMessage())
                .as("실패 원인이 결제 이력에 남아야 한다.")
                .isEqualTo(expectedFailureMessage);

        return payment;
    }

    private void assertStockRestored(Long productId) {
        ProductStock stock = productStockRepository.findByProductId(productId)
                .orElseThrow();

        assertThat(stock.getTotalStock())
                .as("전체 재고 수량은 변경되면 안 된다.")
                .isEqualTo(TOTAL_STOCK);

        assertThat(stock.getReservedStock())
                .as("PG 실패 후 예약 재고는 반드시 해제되어야 한다.")
                .isZero();

        assertThat(stock.getSoldStock())
                .as("PG 실패이므로 판매 확정 재고는 증가하면 안 된다.")
                .isZero();

        assertThat(stock.availableStock())
                .as("예약 재고가 복구되어 가용 재고는 원래대로 돌아와야 한다.")
                .isEqualTo(TOTAL_STOCK);
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
}
