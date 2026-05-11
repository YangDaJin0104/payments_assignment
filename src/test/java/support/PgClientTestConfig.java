package support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class PgClientTestConfig {

    @Bean
    public TestPgClient testPgClient() {
        return new TestPgClient();
    }
}
