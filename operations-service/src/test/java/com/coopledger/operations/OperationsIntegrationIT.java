package com.coopledger.operations;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coopledger.operations.transfers.ProjectionService;
import com.coopledger.shared.TransferCompleted;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.kafka.clients.admin.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "coopledger.security.secret=bG9jYWwtY29vcGxlZGdlci1kZW1vLWtleS0zMi1ieXRlcy1taW5pbXVt",
    "management.server.port=0", "logging.level.org.apache.kafka=WARN"
})
class OperationsIntegrationIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7");
    @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

    @DynamicPropertySource static void dependencies(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.producer.key-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer", () -> "org.apache.kafka.common.serialization.StringSerializer");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ProjectionService projections;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc mvc;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;

    @BeforeAll static void topic() throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TransferCompleted.TOPIC, 3, (short) 1))).all().get(20, TimeUnit.SECONDS);
        }
    }

    @BeforeEach void clean() { jdbc.execute("TRUNCATE transfer_views, processed_events CASCADE"); }

    TransferCompleted event() {
        return new TransferCompleted(UUID.randomUUID(), TransferCompleted.TYPE, 1, Instant.now(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("30.00"), "Pagamento");
    }
    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }

    @Test void duplicateKafkaDeliveryCreatesOneProjection() throws Exception {
        var counter = metrics.find("coopledger.operations.events").tag("result", "duplicate").counter();
        double previousDuplicates = counter == null ? 0 : counter.count();
        TransferCompleted event = event();
        String payload = mapper.writeValueAsString(event);
        kafka.send(TransferCompleted.TOPIC, event.transferId().toString(), payload).get(10, TimeUnit.SECONDS);
        kafka.send(TransferCompleted.TOPIC, event.transferId().toString(), payload).get(10, TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(count("processed_events")).isEqualTo(1);
            assertThat(count("transfer_views")).isEqualTo(1);
            assertThat(metrics.get("coopledger.operations.events").tag("result", "duplicate").counter().count())
                    .isGreaterThan(previousDuplicates);
            assertThat(jdbc.queryForObject("SELECT amount FROM transfer_views WHERE transfer_id = ?", BigDecimal.class, event.transferId()))
                    .isEqualByComparingTo("30.00");
        });
        assertThat(projections.apply(event)).isFalse();
    }

    @Test void concurrentDuplicateConsumersUseDatabaseUniqueness() throws Exception {
        TransferCompleted event = event();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> first = executor.submit(() -> { start.await(); return projections.apply(event); });
            Future<Boolean> second = executor.submit(() -> { start.await(); return projections.apply(event); });
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(count("processed_events")).isEqualTo(1);
        assertThat(count("transfer_views")).isEqualTo(1);
    }

    @Test void projectionFailureAlsoRollsBackDeduplicationAndCanRetry() {
        TransferCompleted event = event();
        jdbc.execute("""
                CREATE FUNCTION fail_projection_test() RETURNS TRIGGER LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION 'Simulated projection failure'; END; $$
                """);
        jdbc.execute("CREATE TRIGGER fail_projection_test BEFORE INSERT ON transfer_views FOR EACH ROW EXECUTE FUNCTION fail_projection_test()");
        try {
            assertThatThrownBy(() -> projections.apply(event)).isInstanceOf(org.springframework.dao.DataAccessException.class);
            assertThat(count("processed_events")).isZero();
            assertThat(count("transfer_views")).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER fail_projection_test ON transfer_views");
            jdbc.execute("DROP FUNCTION fail_projection_test()");
        }
        assertThat(projections.apply(event)).isTrue();
        assertThat(count("processed_events")).isEqualTo(1);
        assertThat(count("transfer_views")).isEqualTo(1);
    }

    @Test void unsupportedSchemaIsNotRecordedAsProcessed() {
        TransferCompleted value = event();
        var invalid = new TransferCompleted(value.eventId(), value.eventType(), 2, value.occurredAt(), value.transferId(),
                value.sourceAccountId(), value.destinationAccountId(), value.amount(), value.description());
        assertThatThrownBy(() -> projections.apply(invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("processed_events")).isZero();
    }

    @Test void queryRequiresOperationsRole() throws Exception {
        mvc.perform(get("/operations/transfers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/operations/transfers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/operations/transfers").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPS"))))
                .andExpect(status().isOk());
    }
}
