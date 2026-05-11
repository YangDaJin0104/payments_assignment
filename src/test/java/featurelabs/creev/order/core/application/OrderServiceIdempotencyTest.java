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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(PgClientTestConfig.class)
@Execution(ExecutionMode.SAME_THREAD)
class OrderServiceIdempotencyTest {

    private static final int TOTAL_STOCK = 100;

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
    private Long anotherProductId;

    @BeforeEach
    void setUp() {
        paymentRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        productStockRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();

        testPgClient.reset();
        testPgClient.success();

        Product product = productRepository.save(
                Product.create(
                        "남성 아우터 게릴라 기획전",
                        59000L,
                        1
                )
        );

        Product anotherProduct = productRepository.save(
                Product.create(
                        "다른 유효 상품",
                        39000L,
                        1
                )
        );

        productStockRepository.save(
                ProductStock.create(
                        product.getId(),
                        TOTAL_STOCK
                )
        );

        productStockRepository.save(
                ProductStock.create(
                        anotherProduct.getId(),
                        TOTAL_STOCK
                )
        );

        productId = product.getId();
        anotherProductId = anotherProduct.getId();
    }

    @Test
    void 같은_멱등키의_동일_요청은_기존_주문을_반환하고_PG를_다시_호출하지_않는다() {
        // given
        CreateOrderCommand command = createCommand(
                1L,
                productId,
                1,
                "same-idempotency-key"
        );

        // when
        CreateOrderResult firstResult = orderService.createOrder(command);
        CreateOrderResult secondResult = orderService.createOrder(command);

        // then
        assertThat(firstResult.newOrder())
                .as("첫 요청은 신규 주문이어야 한다.")
                .isTrue();

        assertThat(secondResult.newOrder())
                .as("동일 멱등 요청은 기존 주문 반환이어야 한다.")
                .isFalse();

        assertThat(firstResult.orderId())
                .as("동일 멱등 요청은 같은 주문을 가리켜야 한다.")
                .isEqualTo(secondResult.orderId());

        assertThat(firstResult.status()).isEqualTo(OrderStatus.PAID);
        assertThat(secondResult.status()).isEqualTo(OrderStatus.PAID);

        assertOrdersAndPayments(
                1,
                OrderStatus.PAID,
                PaymentStatus.SUCCESS
        );

        assertStock(
                productId,
                0,
                1,
                TOTAL_STOCK - 1
        );

        assertThat(testPgClient.getCallCount())
                .as("동일 멱등 요청은 PG를 다시 호출하지 않아야 한다.")
                .isEqualTo(1);
    }

    @Test
    void 같은_멱등키로_다른_유효한_요청이_들어오면_멱등키_충돌로_처리한다() {
        // given
        Long userId = 1L;
        String idempotencyKey = "conflict-idempotency-key";

        CreateOrderCommand originalCommand = createCommand(
                userId,
                productId,
                1,
                idempotencyKey
        );

        orderService.createOrder(originalCommand);

        CreateOrderCommand differentCommand = createCommand(
                userId,
                anotherProductId,
                1,
                idempotencyKey
        );

        // when & then
        assertThatThrownBy(() -> orderService.createOrder(differentCommand))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .as("같은 멱등키로 다른 요청을 보내면 멱등키 충돌이어야 한다.")
                                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT)
                );

        assertOrdersAndPayments(
                1,
                OrderStatus.PAID,
                PaymentStatus.SUCCESS
        );

        assertStock(
                productId,
                0,
                1,
                TOTAL_STOCK - 1
        );

        assertStock(
                anotherProductId,
                0,
                0,
                TOTAL_STOCK
        );

        assertThat(testPgClient.getCallCount())
                .as("멱등키 충돌 요청은 PG를 호출하면 안 된다.")
                .isEqualTo(1);
    }

    @Test
    void 같은_멱등키의_동일_요청이_동시에_여러_번_들어와도_주문과_PG호출은_한_번만_발생한다() throws InterruptedException {
        // given
        int requestCount = 20;
        int threadCount = 20;

        IntFunction<CreateOrderCommand> commandFactory = index -> createCommand(
                1L,
                productId,
                1,
                "concurrent-same-idempotency-key"
        );

        // when
        ConcurrentOrderExecution execution = executeConcurrently(
                requestCount,
                threadCount,
                commandFactory
        );

        // then
        assertNoUnexpectedError(execution);

        assertThat(execution.businessErrors())
                .as("동일 멱등 요청은 비즈니스 에러가 아니라 기존 주문 반환이어야 한다.")
                .isEmpty();

        assertThat(execution.successResults())
                .as("모든 동일 멱등 요청은 응답을 받아야 한다.")
                .hasSize(requestCount);

        Set<Long> responseOrderIds = execution.successResults()
                .stream()
                .map(CreateOrderResult::orderId)
                .collect(Collectors.toSet());

        assertThat(responseOrderIds)
                .as("모든 응답은 같은 주문을 가리켜야 한다.")
                .hasSize(1);

        long newOrderCount = execution.successResults()
                .stream()
                .filter(CreateOrderResult::newOrder)
                .count();

        long existingOrderCount = execution.successResults()
                .stream()
                .filter(result -> !result.newOrder())
                .count();

        assertThat(newOrderCount)
                .as("실제 신규 주문 생성은 한 번이어야 한다.")
                .isEqualTo(1);

        assertThat(existingOrderCount)
                .as("나머지 동일 멱등 요청은 기존 주문 반환이어야 한다.")
                .isEqualTo(requestCount - 1);

        assertOrdersAndPayments(
                1,
                OrderStatus.PAID,
                PaymentStatus.SUCCESS
        );

        assertStock(
                productId,
                0,
                1,
                TOTAL_STOCK - 1
        );

        assertThat(testPgClient.getCallCount())
                .as("동시에 같은 멱등 요청이 들어와도 PG는 한 번만 호출되어야 한다.")
                .isEqualTo(1);
    }

    private ConcurrentOrderExecution executeConcurrently(
            int requestCount,
            int threadCount,
            IntFunction<CreateOrderCommand> commandFactory
    ) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        CountDownLatch readyLatch = new CountDownLatch(Math.min(requestCount, threadCount));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);

        Queue<CreateOrderResult> successResults = new ConcurrentLinkedQueue<>();
        Queue<ErrorCode> businessErrors = new ConcurrentLinkedQueue<>();
        Queue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                final int index = i;

                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        CreateOrderCommand command = commandFactory.apply(index);
                        CreateOrderResult result = orderService.createOrder(command);

                        successResults.add(result);
                    } catch (BusinessException e) {
                        businessErrors.add(e.getErrorCode());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        unexpectedErrors.add(e);
                    } catch (Throwable e) {
                        unexpectedErrors.add(e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            boolean ready = readyLatch.await(5, TimeUnit.SECONDS);

            assertThat(ready)
                    .as("동시 실행할 worker thread가 준비되어야 한다.")
                    .isTrue();

            startLatch.countDown();

            boolean completed = doneLatch.await(30, TimeUnit.SECONDS);

            assertThat(completed)
                    .as("모든 멱등 요청이 제한 시간 안에 완료되어야 한다.")
                    .isTrue();

            return new ConcurrentOrderExecution(
                    successResults,
                    businessErrors,
                    unexpectedErrors
            );
        } finally {
            executorService.shutdownNow();
        }
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

    private void assertNoUnexpectedError(ConcurrentOrderExecution execution) {
        assertThat(execution.unexpectedErrors())
                .as("BusinessException으로 분류되지 않은 예상 외 예외가 없어야 한다.")
                .isEmpty();
    }

    private void assertOrdersAndPayments(
            int expectedSize,
            OrderStatus expectedOrderStatus,
            PaymentStatus expectedPaymentStatus
    ) {
        List<Order> orders = orderRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();

        assertThat(orders)
                .as("멱등 요청은 주문 row를 중복 생성하면 안 된다.")
                .hasSize(expectedSize);

        assertThat(payments)
                .as("멱등 요청은 결제 row를 중복 생성하면 안 된다.")
                .hasSize(expectedSize);

        assertThat(orders)
                .extracting(Order::getStatus)
                .as("최종 주문 상태가 기대 상태와 일치해야 한다.")
                .containsOnly(expectedOrderStatus);

        assertThat(payments)
                .extracting(Payment::getStatus)
                .as("최종 결제 상태가 기대 상태와 일치해야 한다.")
                .containsOnly(expectedPaymentStatus);
    }

    private void assertStock(
            Long productId,
            int expectedReservedStock,
            int expectedSoldStock,
            int expectedAvailableStock
    ) {
        ProductStock stock = productStockRepository.findByProductId(productId)
                .orElseThrow();

        assertThat(stock.getReservedStock())
                .as("멱등 요청은 예약 재고를 중복 점유하면 안 된다.")
                .isEqualTo(expectedReservedStock);

        assertThat(stock.getSoldStock())
                .as("멱등 요청은 판매 재고를 중복 증가시키면 안 된다.")
                .isEqualTo(expectedSoldStock);

        assertThat(stock.availableStock())
                .as("가용 재고는 멱등 처리 이후에도 정합성을 유지해야 한다.")
                .isEqualTo(expectedAvailableStock);
    }

    private record ConcurrentOrderExecution(
            Queue<CreateOrderResult> successResults,
            Queue<ErrorCode> businessErrors,
            Queue<Throwable> unexpectedErrors
    ) {
    }
}
