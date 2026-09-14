package com.sentinelflow.feature;

import com.sentinelflow.feature.FeatureComputationService;
import com.sentinelflow.identity.Device;
import com.sentinelflow.identity.Location;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.MerchantStatus;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureComputationTest {

    private final FeatureComputationService svc = new FeatureComputationService();

    private EnrichmentContext ctx(BigDecimal amount, Instant txnTime, Device device, Location location, Instant userFirstTxn) {
        User user = new User("USR-FEAT", "Feat", null, UserStatus.ACTIVE);
        Merchant merchant = new Merchant("MRC-FEAT", "Mart", "GROCERY", "IN", MerchantStatus.ACTIVE);
        Transaction txn = new Transaction("TXN-FEAT", user, merchant, device, location, amount, "INR", "PURCHASE", "ONLINE", txnTime, TransactionStatus.RECEIVED);
        EnrichmentContext.UserBehaviorProfile ub = new EnrichmentContext.UserBehaviorProfile(2,5,10, new BigDecimal("200"), new BigDecimal("500"), new BigDecimal("1000"), new BigDecimal("100"), userFirstTxn, txnTime, 1,1);
        EnrichmentContext.DeviceProfile dp = new EnrichmentContext.DeviceProfile(device==null, 3,1, Instant.parse("2026-01-01T00:00:00Z"), txnTime);
        EnrichmentContext.LocationProfile lp = new EnrichmentContext.LocationProfile(location==null, 2, "IN","Karnataka","Bengaluru");
        EnrichmentContext.MerchantProfile mp = new EnrichmentContext.MerchantProfile(10,"GROCERY","IN");
        return new EnrichmentContext(txn, user, device, location, merchant, ub, dp, lp, mp);
    }

    @Test
    void deterministicFeatures() {
        Instant txnTime = Instant.parse("2026-09-01T10:00:00Z");
        Instant userFirst = Instant.parse("2026-01-01T00:00:00Z");
        EnrichmentContext c1 = ctx(new BigDecimal("1499.50"), txnTime, null, null, userFirst);
        EnrichmentContext c2 = ctx(new BigDecimal("1499.50"), txnTime, null, null, userFirst);
        FeatureSet f1 = svc.compute(c1);
        FeatureSet f2 = svc.compute(c2);
        assertThat(f1.features()).isEqualTo(f2.features());
        assertThat(f1.featureSchemaVersion()).isEqualTo("fs-v1");
    }

    @Test
    void amountAndTimeFeatures() {
        Instant txnTime = Instant.parse("2026-09-01T23:30:00Z");
        EnrichmentContext c = ctx(new BigDecimal("10000"), txnTime, null, null, txnTime);
        FeatureSet f = svc.compute(c);
        assertThat(f.getDouble("transaction_amount")).isEqualTo(10000.0);
        assertThat(f.getInteger("transaction_hour")).isEqualTo(23);
        // 2026-09-01 is Tuesday -> day 2 -> %7 =2
        assertThat(f.getInteger("transaction_day_of_week")).isNotNull();
    }

    @Test
    void newDeviceAndLocationFlags() {
        Instant now = Instant.now();
        EnrichmentContext c = ctx(new BigDecimal("100"), now, null, null, now);
        FeatureSet f = svc.compute(c);
        assertThat(f.getInteger("is_new_device")).isEqualTo(1);
        assertThat(f.getInteger("is_new_location")).isEqualTo(1);
    }

    @Test
    void velocityFeatures() {
        Instant now = Instant.now();
        EnrichmentContext c = ctx(new BigDecimal("100"), now, null, null, now);
        FeatureSet f = svc.compute(c);
        assertThat(f.getLong("transactions_last_24_hours")).isNotNull();
        assertThat(f.getLong("transactions_last_24_hours")).isEqualTo(2L);
    }
}
