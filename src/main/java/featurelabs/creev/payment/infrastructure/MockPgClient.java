package featurelabs.creev.payment.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class MockPgClient implements PgClient {

    @Override
    public boolean pay(Long amount) {
        try {
            Thread.sleep((long) (Math.random() * 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentFailureException("PG사 결제 요청 중 인터럽트가 발생했습니다.");
        }

        double random = Math.random();

        if (random < 0.2) {
            throw new PaymentFailureException("PG사 결제 요청 실패 또는 타임아웃 발생");
        }

        return true;
    }
}
