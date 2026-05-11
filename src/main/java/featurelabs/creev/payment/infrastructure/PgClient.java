package featurelabs.creev.payment.infrastructure;

public interface PgClient {

    void pay(Long amount);
}
