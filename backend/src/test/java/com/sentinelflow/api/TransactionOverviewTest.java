package com.sentinelflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sentinelflow.analytics.exception.AnalyticsNotFoundException;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.MerchantRepository;
import com.sentinelflow.transaction.MerchantStatus;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import com.sentinelflow.transaction.TransactionStatus;
import com.sentinelflow.transaction.dto.TransactionOverview;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Transactional
class TransactionOverviewTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired TransactionRepository transactions;
    @Autowired UserRepository users;
    @Autowired MerchantRepository merchants;
    @Autowired TransactionOverviewController controller;

    private String reference;

    @BeforeEach
    void setUp() {
        User user = users.save(new User("USR-OV-" + System.nanoTime(), "Overview User", null, UserStatus.ACTIVE));
        Merchant merchant = merchants.save(new Merchant("MRC-OV-" + System.nanoTime(), "Overview Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        Transaction txn = transactions.save(new Transaction(
                "TXN-OV-" + System.nanoTime(), user, merchant, null, null,
                new BigDecimal("12000.00"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-14T10:00:00Z"), TransactionStatus.RECEIVED));
        reference = txn.getTransactionReference();
    }

    @Test
    void overviewReturnsTransactionWithoutDecisionFlag() {
        TransactionOverview overview = controller.overview(reference).getBody();
        assertThat(overview).isNotNull();
        assertThat(overview.transactionReference()).isEqualTo(reference);
        assertThat(overview.amount()).isEqualByComparingTo("12000.00");
        assertThat(overview.currency()).isEqualTo("INR");
        assertThat(overview.status()).isEqualTo("RECEIVED");
        assertThat(overview.decided()).isFalse();
    }

    @Test
    void unknownTransactionIsNotFound() {
        assertThatThrownBy(() -> controller.overview("TXN-MISSING-OV"))
                .isInstanceOf(AnalyticsNotFoundException.class);
    }
}