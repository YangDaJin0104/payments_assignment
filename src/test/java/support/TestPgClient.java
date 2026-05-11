package support;

import featurelabs.creev.payment.infrastructure.PaymentFailureException;
import featurelabs.creev.payment.infrastructure.PgClient;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TestPgClient implements PgClient {

    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.SUCCESS);
    private final AtomicInteger callCount = new AtomicInteger();

    @Override
    public boolean pay(Long amount) {
        callCount.incrementAndGet();

        if (mode.get() == Mode.FAILURE) {
            throw new PaymentFailureException("테스트 PG 결제 실패");
        }

        return true;
    }

    public void success() {
        mode.set(Mode.SUCCESS);
    }

    public void fail() {
        mode.set(Mode.FAILURE);
    }

    public void reset() {
        mode.set(Mode.SUCCESS);
        callCount.set(0);
    }

    public int getCallCount() {
        return callCount.get();
    }

    private enum Mode {
        SUCCESS,
        FAILURE
    }
}
