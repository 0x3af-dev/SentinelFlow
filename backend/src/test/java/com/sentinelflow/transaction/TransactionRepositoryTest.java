package com.sentinelflow.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sentinelflow.identity.Device;
import com.sentinelflow.identity.DeviceRepository;
import com.sentinelflow.identity.Location;
import com.sentinelflow.identity.LocationRepository;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.identity.UserStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class TransactionRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    UserRepository users;

    @Autowired
    DeviceRepository devices;

    @Autowired
    LocationRepository locations;

    @Autowired
    MerchantRepository merchants;

    @Autowired
    TransactionRepository transactions;

    private User user;
    private Merchant merchant;
    private Device device;
    private Location location;

    @BeforeEach
    void setUp() {
        user = users.save(new User("USR-TXN-" + System.nanoTime(), "Test User", null, UserStatus.ACTIVE));
        merchant = merchants.save(new Merchant("MRC-" + System.nanoTime(), "Test Mart", "GROCERY", "IN", MerchantStatus.ACTIVE));
        device = devices.save(new Device(user, "DEV-" + System.nanoTime(), "MOBILE", "ANDROID", null, null));
        location = locations.save(new Location(user, "IN", "Karnataka", "Bengaluru", 12.97, 77.59, null, null));
    }

    private Transaction newTransaction(String ref) {
        return new Transaction(ref, user, merchant, device, location,
                new BigDecimal("1499.5000"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-01T10:00:00Z"), TransactionStatus.RECEIVED);
    }

    @Test
    void createAndRetrieveTransaction() {
        String ref = "TXN-" + System.nanoTime();
        Transaction saved = transactions.save(newTransaction(ref));

        assertThat(saved.getId()).isNotNull();
        assertThat(transactions.findByTransactionReference(ref)).isPresent();
        assertThat(saved.getAmount()).isEqualByComparingTo(new BigDecimal("1499.50"));
        assertThat(saved.getCurrency()).isEqualTo("INR");
    }

    @Test
    void duplicateTransactionReferenceRejected() {
        String ref = "TXN-DUP-" + System.nanoTime();
        transactions.saveAndFlush(newTransaction(ref));

        assertThatThrownBy(() -> transactions.saveAndFlush(newTransaction(ref)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidUserRejected() {
        User ghost = new User("GHOST", "Ghost", null, UserStatus.ACTIVE);
        Transaction orphan = new Transaction("TXN-GHOST-" + System.nanoTime(), ghost, merchant,
                null, null, BigDecimal.TEN, "INR", "PURCHASE", "ONLINE",
                Instant.now(), TransactionStatus.RECEIVED);

        assertThatThrownBy(() -> transactions.saveAndFlush(orphan))
                .isInstanceOf(Exception.class);
    }

    @Test
    void invalidMerchantRejected() {
        Merchant ghost = new Merchant("GHOST-M", "Ghost", null, null, MerchantStatus.ACTIVE);
        Transaction orphan = new Transaction("TXN-GHOSTM-" + System.nanoTime(), user, ghost,
                null, null, BigDecimal.TEN, "INR", "PURCHASE", "ONLINE",
                Instant.now(), TransactionStatus.RECEIVED);

        assertThatThrownBy(() -> transactions.saveAndFlush(orphan))
                .isInstanceOf(Exception.class);
    }

    @Test
    void negativeAmountRejected() {
        Transaction bad = new Transaction("TXN-NEG-" + System.nanoTime(), user, merchant,
                null, null, new BigDecimal("-5.00"), "INR", "PURCHASE", "ONLINE",
                Instant.now(), TransactionStatus.RECEIVED);

        assertThatThrownBy(() -> transactions.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateMerchantReferenceRejected() {
        String ref = "MRC-DUP-" + System.nanoTime();
        merchants.save(new Merchant(ref, "Shop A", "FOOD", "IN", MerchantStatus.ACTIVE));

        assertThatThrownBy(() -> merchants.saveAndFlush(new Merchant(ref, "Shop B", "FOOD", "IN", MerchantStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void monetaryPrecisionPreserved() {
        String ref = "TXN-PREC-" + System.nanoTime();
        Transaction saved = transactions.saveAndFlush(new Transaction(ref, user, merchant, null, null,
                new BigDecimal("123.4567"), "INR", "PURCHASE", "ONLINE",
                Instant.parse("2026-09-01T10:00:00Z"), TransactionStatus.RECEIVED));
        transactions.flush();

        Transaction reloaded = transactions.findByTransactionReference(ref).orElseThrow();
        assertThat(reloaded.getAmount()).isEqualByComparingTo(new BigDecimal("123.4567"));
    }
}
