package featurelabs.creev.payment.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MockPgClient implements PgClient {

    private static final Logger log = LoggerFactory.getLogger(MockPgClient.class);

    @Override
    public void pay(Long amount) {
        validateAmount(amount);

        log.info("Mock PG payment succeeded. amount={}", amount);

        // 과제용 Mock PG
        // 기본 정책: 정상 결제 성공
        // 실패 케이스는 테스트에서 PgClient를 mock 처리해서 검증
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new PaymentFailureException("유효하지 않은 결제 금액입니다.");
        }
    }
}
