package my.code.auth;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests.
 * Provides shared PostgreSQL and Kafka containers via Testcontainers.
 *
 * <p>Containers are static (singleton) — started once, reused across all test classes.
 * Spring Boot's {@code @ServiceConnection} auto-configures datasource and Kafka bootstrap servers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
@SuppressWarnings("resource")
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"))
                    .withDatabaseName("auth_test")
                    .withUsername("test")
                    .withPassword("test");

    @ServiceConnection
    protected static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    static {
        postgres.start();
        kafka.start();
    }
}
