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

@SpringBootTest
@ActiveProfiles("test")
@Import(PgClientTestConfig.class)
@Execution(ExecutionMode.SAME_THREAD)
class OrderServiceConcurrencyTest {

    private static final int TOTAL_STOCK = 100;
    private static final int REQUEST_COUNT = 1_000;
    private static final int THREAD_COUNT = 100;
    private static final long USER_ID_OFFSET = 1L;

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
        testPgClient.success();

        Product product = productRepository.save(
                Product.create(
                        "남성 아우터 게릴라 기획전",
                        59000L,
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
    void 서로_다른_사용자_1000명이_동시에_주문해도_재고_100개만_판매된다() throws InterruptedException {
        // given
        IntFunction<CreateOrderCommand> commandFactory = index -> createCommand(
                USER_ID_OFFSET + index,
                productId,
                1,
                "concurrency-order-" + index
        );

        // when
        ConcurrentOrderExecution execution = executeConcurrently(
                REQUEST_COUNT,
                THREAD_COUNT,
                commandFactory
        );

        // then
        assertNoUnexpectedError(execution);

        assertThat(execution.successResults())
                .as("한정 수량만큼만 주문 성공해야 한다.")
                .hasSize(TOTAL_STOCK);

        assertThat(execution.businessErrors())
                .as("재고 소진 이후 요청은 모두 품절이어야 한다.")
                .hasSize(REQUEST_COUNT - TOTAL_STOCK)
                .containsOnly(ErrorCode.PRODUCT_OUT_OF_STOCK);

        assertThat(execution.successResults())
                .extracting(CreateOrderResult::status)
                .as("성공한 주문은 모두 결제 완료 상태여야 한다.")
                .containsOnly(OrderStatus.PAID);

        assertStock(
                productId,
                TOTAL_STOCK,
                0,
                TOTAL_STOCK,
                0
        );

        assertOrdersAndPayments(
                TOTAL_STOCK,
                OrderStatus.PAID,
                PaymentStatus.SUCCESS
        );

        assertThat(testPgClient.getCallCount())
                .as("재고 예약에 성공한 요청만 PG를 호출해야 한다.")
                .isEqualTo(TOTAL_STOCK);
    }

    @Test
    void 같은_사용자가_서로_다른_멱등키로_동시에_같은_상품을_주문해도_하나만_성공한다() throws InterruptedException {
        // given
        IntFunction<CreateOrderCommand> commandFactory = index -> createCommand(
                1L,
                productId,
                1,
                "duplicate-click-" + index
        );

        // when
        ConcurrentOrderExecution execution = executeConcurrently(
                2,
                2,
                commandFactory
        );

        // then
        assertNoUnexpectedError(execution);

        assertThat(execution.successResults())
                .as("같은 사용자의 같은 상품 주문은 하나만 성공해야 한다.")
                .hasSize(1);

        assertThat(execution.businessErrors())
                .as("나머지 요청은 중복 주문으로 실패해야 한다.")
                .hasSize(1)
                .containsOnly(ErrorCode.DUPLICATE_ORDER);

        assertStock(
                productId,
                TOTAL_STOCK,
                0,
                1,
                TOTAL_STOCK - 1
        );

        assertOrdersAndPayments(
                1,
                OrderStatus.PAID,
                PaymentStatus.SUCCESS
        );

        assertThat(testPgClient.getCallCount())
                .as("중복 구매로 차단된 요청은 PG를 호출하면 안 된다.")
                .isEqualTo(1);
    }

    @Test
    void 같은_멱등키의_동일_요청이_동시에_들어와도_주문과_PG호출은_한_번만_발생한다() throws InterruptedException {
        // given
        IntFunction<CreateOrderCommand> commandFactory = index -> createCommand(
                1L,
                productId,
                1,
                "same-idempotency-key"
        );

        // when
        ConcurrentOrderExecution execution = executeConcurrently(
                2,
                2,
                commandFactory
        );

        // then
        assertNoUnexpectedError(execution);

        assertThat(execution.businessErrors())
                .as("동일 멱등 요청은 에러가 아니라 기존 주문 반환이어야 한다.")
                .isEmpty();

        assertThat(execution.successResults())
                .as("동시에 들어온 두 요청 모두 정상 응답을 받아야 한다.")
                .hasSize(2);

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
                .as("중복 멱등 요청은 기존 주문으로 반환되어야 한다.")
                .isEqualTo(1);

        Set<Long> responseOrderIds = execution.successResults()
                .stream()
                .map(CreateOrderResult::orderId)
                .collect(Collectors.toSet());

        assertThat(responseOrderIds)
                .as("동일 멱등 요청의 응답은 같은 주문을 가리켜야 한다.")
                .hasSize(1);

        assertStock(
                productId,
                TOTAL_STOCK,
                0,
                1,
                TOTAL_STOCK - 1
        );

        assertOrdersAndPayments(
                1,
                OrderStatus.PAID,
                PaymentStatus.SUCCESS
        );

        assertThat(testPgClient.getCallCount())
                .as("동일 멱등 요청은 PG를 한 번만 호출해야 한다.")
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

            boolean completed = doneLatch.await(60, TimeUnit.SECONDS);

            assertThat(completed)
                    .as("모든 주문 요청이 제한 시간 안에 완료되어야 한다.")
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

    private void assertStock(
            Long productId,
            int expectedTotalStock,
            int expectedReservedStock,
            int expectedSoldStock,
            int expectedAvailableStock
    ) {
        ProductStock stock = productStockRepository.findByProductId(productId)
                .orElseThrow();

        assertThat(stock.getTotalStock())
                .as("전체 한정 수량은 변경되면 안 된다.")
                .isEqualTo(expectedTotalStock);

        assertThat(stock.getReservedStock())
                .as("결제 확정 또는 실패 복구 후 남아 있는 예약 재고가 없어야 한다.")
                .isEqualTo(expectedReservedStock);

        assertThat(stock.getSoldStock())
                .as("판매 확정 재고는 성공 주문 수와 같아야 한다.")
                .isEqualTo(expectedSoldStock);

        assertThat(stock.availableStock())
                .as("가용 재고는 total - reserved - sold 계산 결과와 일치해야 한다.")
                .isEqualTo(expectedAvailableStock);
    }

    private void assertOrdersAndPayments(
            int expectedSize,
            OrderStatus expectedOrderStatus,
            PaymentStatus expectedPaymentStatus
    ) {
        List<Order> orders = orderRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();

        assertThat(orders)
                .as("실패 요청은 주문 row를 만들면 안 된다.")
                .hasSize(expectedSize);

        assertThat(payments)
                .as("실패 요청은 결제 row를 만들면 안 된다.")
                .hasSize(expectedSize);

        assertThat(orders)
                .extracting(Order::getStatus)
                .as("저장된 주문 상태가 기대 상태와 일치해야 한다.")
                .containsOnly(expectedOrderStatus);

        assertThat(payments)
                .extracting(Payment::getStatus)
                .as("저장된 결제 상태가 기대 상태와 일치해야 한다.")
                .containsOnly(expectedPaymentStatus);
    }

    private record ConcurrentOrderExecution(
            Queue<CreateOrderResult> successResults,
            Queue<ErrorCode> businessErrors,
            Queue<Throwable> unexpectedErrors
    ) {
    }
}
