package featurelabs.creev.payment.infrastructure;

public interface PgClient {

    boolean pay(Long amount);
}
