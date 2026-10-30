package com.coopledger.core;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.coopledger.core.accounts.*;
import com.coopledger.core.ledger.*;
import com.coopledger.core.outbox.*;
import com.coopledger.core.reconciliation.ReconciliationService;
import com.coopledger.core.transfers.*;
import com.coopledger.shared.*;
import com.coopledger.shared.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
    "coopledger.security.secret=bG9jYWwtY29vcGxlZGdlci1kZW1vLWtleS0zMi1ieXRlcy1taW5pbXVt",
    "coopledger.outbox.enabled=false", "management.server.port=0",
    "spring.kafka.admin.fail-fast=true", "logging.level.org.apache.kafka=WARN"
})
class CoreIntegrationIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.7");
    @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.9.1");

    @DynamicPropertySource static void dependencies(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired AccountRepository accounts;
    @Autowired LedgerRepository ledger;
    @Autowired TransferService transfers;
    @Autowired ReconciliationService reconciliation;
    @Autowired OutboxRelay relay;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtEncoder encoder;
    @MockitoSpyBean TransferPosting posting;
    final Actor alice = new Actor("alice", false);
    final Actor bob = new Actor("bob", false);
    UUID source;
    UUID destination;

    @BeforeEach void fixtures() {
        jdbc.execute("TRUNCATE reconciliation_discrepancies, reconciliation_runs, outbox_events, idempotency_keys, ledger_entries, transfers, accounts CASCADE");
        source = fundedAccount("alice", "100.00");
        destination = fundedAccount("bob", "0.00");
    }

    UUID fundedAccount(String owner, String value) {
        return transaction.execute(status -> {
            Account account = new Account(owner);
            BigDecimal amount = new BigDecimal(value);
            if (amount.signum() > 0) account.credit(amount);
            Account saved = accounts.saveAndFlush(account);
            if (amount.signum() > 0) ledger.saveAndFlush(new LedgerEntry(saved.getId(), null, LedgerEntry.Type.OPENING_CREDIT,
                    amount, "Fixture", Instant.now()));
            return saved.getId();
        });
    }

    TransferRequest request(String amount) {
        return new TransferRequest(source, destination, new BigDecimal(amount), "Pagamento de serviço");
    }
    BigDecimal balance(UUID id) { return jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, id); }
    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }

    @Test void transfersThirtyAndCreatesBalancedHistoryAndOutbox() {
        var result = transfers.transfer(request("30.00"), "valid", alice);
        assertThat(result.replayed()).isFalse();
        assertThat(balance(source)).isEqualByComparingTo("70.00");
        assertThat(balance(destination)).isEqualByComparingTo("30.00");
        assertThat(count("transfers")).isEqualTo(1);
        assertThat(count("ledger_entries")).isEqualTo(3); // opening + debit + credit
        assertThat(count("outbox_events")).isEqualTo(1);
        assertThat(count("idempotency_keys")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT sum(CASE WHEN entry_type = 'DEBIT' THEN -amount ELSE amount END) FROM ledger_entries WHERE transfer_id = ?",
                BigDecimal.class, result.transfer().id())).isEqualByComparingTo("0.00");
        assertThat(reconciliation.execute("ops").divergenceCount()).isZero();
    }

    @Test void insufficientFundsLeavesNoFinancialOrIdempotencyWrites() {
        assertThatThrownBy(() -> transfers.transfer(request("100.01"), "insufficient", alice))
                .isInstanceOf(DomainException.class).hasMessageContaining("Saldo insuficiente");
        assertThat(balance(source)).isEqualByComparingTo("100.00");
        assertThat(balance(destination)).isEqualByComparingTo("0.00");
        assertThat(count("transfers")).isZero();
        assertThat(count("outbox_events")).isZero();
        assertThat(count("idempotency_keys")).isZero();
        assertThat(count("ledger_entries")).isEqualTo(1);
    }

    @Test void failureAfterFlushedDebitRollsBackEverything() {
        doAnswer(invocation -> {
            assertThat(balance(source)).isEqualByComparingTo("70.00");
            assertThat(balance(destination)).isEqualByComparingTo("0.00");
            assertThat(count("ledger_entries")).isEqualTo(2);
            throw new IllegalStateException("Falha simulada antes do crédito");
        }).when(posting).credit(any(), any());
        assertThatThrownBy(() -> transfers.transfer(request("30.00"), "rollback", alice)).isInstanceOf(IllegalStateException.class);
        assertThat(balance(source)).isEqualByComparingTo("100.00");
        assertThat(balance(destination)).isEqualByComparingTo("0.00");
        assertThat(count("transfers")).isZero();
        assertThat(count("outbox_events")).isZero();
        assertThat(count("idempotency_keys")).isZero();
        assertThat(count("ledger_entries")).isEqualTo(1);
    }

    @Test void sameRequestReplaysAndDifferentPayloadConflicts() {
        var original = transfers.transfer(request("30.00"), "retry", alice);
        var replayed = transfers.transfer(request("30"), "retry", alice);
        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.transfer().id()).isEqualTo(original.transfer().id());
        assertThat(replayed.transfer()).isEqualTo(original.transfer());
        assertThatThrownBy(() -> transfers.transfer(request("31.00"), "retry", alice)).isInstanceOf(DomainException.class)
                .hasMessageContaining("dados diferentes");
        assertThat(count("transfers")).isEqualTo(1);
        assertThat(balance(source)).isEqualByComparingTo("70.00");
    }

    @Test void simultaneousSameKeyMovesMoneyOnce() throws Exception {
        var results = concurrently(
                () -> transfers.transfer(request("30.00"), "concurrent-key", alice),
                () -> transfers.transfer(request("30.00"), "concurrent-key", alice));
        assertThat(results.get(0).transfer().id()).isEqualTo(results.get(1).transfer().id());
        assertThat(results).filteredOn(TransferExecutor.Result::replayed).hasSize(1);
        assertThat(count("transfers")).isEqualTo(1);
        assertThat(count("outbox_events")).isEqualTo(1);
        assertThat(balance(source)).isEqualByComparingTo("70.00");
    }

    @Test void replayRechecksAuthorizationAfterOperationsRoleIsRemoved() {
        transfers.transfer(request("30.00"), "role-change", new Actor("operator", true));
        assertThatThrownBy(() -> transfers.transfer(request("30.00"), "role-change", new Actor("operator", false)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(count("transfers")).isEqualTo(1);
        assertThat(balance(source)).isEqualByComparingTo("70.00");
    }

    @Test void historyIsVisibleOnlyToParticipantsAndOperations() {
        var result = transfers.transfer(request("30.00"), "history", alice);
        assertThat(transfers.find(result.transfer().id(), alice)).isEqualTo(result.transfer());
        assertThat(transfers.find(result.transfer().id(), bob)).isEqualTo(result.transfer());
        assertThat(transfers.find(result.transfer().id(), new Actor("operator", true))).isEqualTo(result.transfer());
        assertThatThrownBy(() -> transfers.find(result.transfer().id(), new Actor("stranger", false)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test void concurrentSpendingApprovesOnlyOneEightyReaisTransfer() throws Exception {
        var results = concurrently(
                () -> attempt(request("80.00"), "spend-1", alice),
                () -> attempt(request("80.00"), "spend-2", alice));
        assertThat(results).containsExactlyInAnyOrder("COMPLETED", "INSUFFICIENT_FUNDS");
        assertThat(balance(source)).isEqualByComparingTo("20.00");
        assertThat(balance(destination)).isEqualByComparingTo("80.00");
        assertThat(count("transfers")).isEqualTo(1);
    }

    @Test void opposingTransfersLockInSameOrderAndKeysAreScopedToUser() throws Exception {
        UUID other = fundedAccount("bob", "100.00");
        var results = concurrently(
                () -> transfers.transfer(new TransferRequest(source, other, new BigDecimal("10.00"), ""), "shared-key", alice),
                () -> transfers.transfer(new TransferRequest(other, source, new BigDecimal("10.00"), ""), "shared-key", bob));
        assertThat(results).hasSize(2);
        assertThat(count("transfers")).isEqualTo(2);
        assertThat(balance(source)).isEqualByComparingTo("100.00");
        assertThat(balance(other)).isEqualByComparingTo("100.00");
    }

    @Test void blockedDestinationAndForeignSourceCannotMoveMoney() {
        transaction.executeWithoutResult(status -> accounts.findLockedById(destination).orElseThrow().changeStatus(Account.Status.BLOCKED));
        assertThatThrownBy(() -> transfers.transfer(request("30.00"), "blocked", alice)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> transfers.transfer(request("30.00"), "foreign", bob)).isInstanceOf(AccessDeniedException.class);
        assertThat(balance(source)).isEqualByComparingTo("100.00");
        assertThat(count("transfers")).isZero();
    }

    @Test void reconciliationRecordsDivergenceAndDoesNotChangeBalance() {
        jdbc.update("UPDATE accounts SET balance = 400.00 WHERE id = ?", source);
        var run = reconciliation.execute("ops");
        assertThat(run.accountsChecked()).isEqualTo(2);
        assertThat(run.divergenceCount()).isEqualTo(1);
        var divergence = reconciliation.divergences(run.id(), 0, 20).content().getFirst();
        assertThat(divergence.storedBalance()).isEqualByComparingTo("400.00");
        assertThat(divergence.ledgerBalance()).isEqualByComparingTo("100.00");
        assertThat(divergence.difference()).isEqualByComparingTo("300.00");
        assertThat(balance(source)).isEqualByComparingTo("400.00");
    }

    @Test void databaseRejectsHistoryChangesAndUnbalancedTransfer() {
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = 200 WHERE account_id = ?", source))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO transfers(id, source_account_id, destination_account_id, actor_id, amount, description, status, completed_at)
                VALUES (?, ?, ?, 'alice', 30.00, '', 'COMPLETED', now())
                """, UUID.randomUUID(), source, destination))).isInstanceOf(RuntimeException.class);
        assertThat(count("transfers")).isZero();
    }

    @Test void pausedKafkaDoesNotPreventTransferAndOutboxRecovers() throws Exception {
        var docker = DockerClientFactory.instance().client();
        docker.pauseContainerCmd(KAFKA.getContainerId()).exec();
        UUID eventId;
        try {
            transfers.transfer(request("30.00"), "kafka-outage", alice);
            assertThat(balance(source)).isEqualByComparingTo("70.00");
            assertThat(relay.publishNext()).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NULL AND attempts = 1", Long.class)).isEqualTo(1);
            eventId = jdbc.queryForObject("SELECT id FROM outbox_events", UUID.class);
        } finally {
            docker.unpauseContainerCmd(KAFKA.getContainerId()).exec();
        }
        jdbc.update("UPDATE outbox_events SET next_attempt_at = now()");
        assertThat(relay.publishNext()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL", Long.class)).isEqualTo(1);
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TransferCompleted.TOPIC));
            boolean found = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (!found && System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofMillis(500))) {
                    var event = mapper.readValue(record.value(), TransferCompleted.class);
                    if (event.eventId().equals(eventId)) {
                        assertThat(event.amount()).isEqualByComparingTo("30.00");
                        found = true;
                    }
                }
            }
            assertThat(found).as("evento preservado publicado no Kafka real").isTrue();
        }
    }

    @Test void apiValidatesBearerOwnershipAmountsAndPagination() throws Exception {
        mvc.perform(get("/accounts/{id}/balance", source)).andExpect(status().isUnauthorized());
        mvc.perform(get("/accounts/{id}/balance", source).header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/accounts/{id}/balance", source).header("Authorization", token("alice", "MEMBER", true))).andExpect(status().isUnauthorized());
        mvc.perform(get("/accounts/{id}/balance", source).header("Authorization", token("bob", "MEMBER", false))).andExpect(status().isForbidden());
        mvc.perform(get("/accounts/{id}/statement", source).header("Authorization", token("bob", "MEMBER", false))).andExpect(status().isForbidden());
        mvc.perform(post("/reconciliation-runs").header("Authorization", token("alice", "MEMBER", false))).andExpect(status().isForbidden());
        mvc.perform(post("/transfers").header("Authorization", token("alice", "MEMBER", false))
                .header("Idempotency-Key", "api").contentType("application/json").content(mapper.writeValueAsString(request("30.00"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(30.00)).andExpect(header().string("Idempotency-Replayed", "false"));
        mvc.perform(post("/transfers").header("Authorization", token("alice", "MEMBER", false))
                .header("Idempotency-Key", "api").contentType("application/json").content(mapper.writeValueAsString(request("30.00"))))
                .andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed", "true"));
        mvc.perform(post("/transfers").header("Authorization", token("alice", "MEMBER", false))
                .header("Idempotency-Key", "fractional").contentType("application/json").content(mapper.writeValueAsString(request("0.001"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/transfers").header("Authorization", token("alice", "MEMBER", false))
                .contentType("application/json").content(mapper.writeValueAsString(request("1.00")))).andExpect(status().isBadRequest());
        mvc.perform(get("/accounts/{id}/statement?size=1", source).header("Authorization", token("alice", "MEMBER", false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/accounts/{id}/statement?size=101", source).header("Authorization", token("alice", "MEMBER", false)))
                .andExpect(status().isBadRequest());
    }

    String token(String subject, String role, boolean expired) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(SecurityConfiguration.ISSUER).subject(subject)
                .audience(List.of(SecurityConfiguration.AUDIENCE)).issuedAt(now.minusSeconds(600))
                .expiresAt(expired ? now.minusSeconds(120) : now.plusSeconds(600)).claim("roles", List.of(role)).build();
        return "Bearer " + encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    String attempt(TransferRequest request, String key, Actor actor) {
        try { transfers.transfer(request, key, actor); return "COMPLETED"; }
        catch (DomainException ex) { return ex.code(); }
    }

    <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            Future<T> one = pool.submit(() -> { start.await(); return first.call(); });
            Future<T> two = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(one.get(25, TimeUnit.SECONDS), two.get(25, TimeUnit.SECONDS));
        }
    }
}
