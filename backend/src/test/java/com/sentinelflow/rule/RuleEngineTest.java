package com.sentinelflow.rule;

import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.FeatureSet;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserStatus;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.MerchantStatus;
import com.sentinelflow.transaction.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {

    private final HighAmountRule highAmount = new HighAmountRule();
    private final NewDeviceRule newDevice = new NewDeviceRule();
    private final HighVelocityRule highVelocity = new HighVelocityRule();
    private final NewLocationRule newLocation = new NewLocationRule();
    private final CombinedSuspicionRule combined = new CombinedSuspicionRule();

    private EnrichmentContext ctxWithAmount(BigDecimal amount) {
        User user = new User("USR-TEST", "Test", null, UserStatus.ACTIVE);
        Merchant merchant = new Merchant("MRC-TEST", "TestMart", "GROCERY", "IN", MerchantStatus.ACTIVE);
        Transaction txn = new Transaction("TXN-TEST", user, merchant, null, null, amount, "INR", "PURCHASE", "ONLINE", Instant.now(), TransactionStatus.RECEIVED);
        EnrichmentContext.UserBehaviorProfile ub = new EnrichmentContext.UserBehaviorProfile(1,1,1, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, Instant.now(), Instant.now(), 1,1);
        EnrichmentContext.DeviceProfile dp = new EnrichmentContext.DeviceProfile(false, 0,1, Instant.now(), Instant.now());
        EnrichmentContext.LocationProfile lp = new EnrichmentContext.LocationProfile(false, 0, "IN","Karnataka","Bengaluru");
        EnrichmentContext.MerchantProfile mp = new EnrichmentContext.MerchantProfile(0,"GROCERY","IN");
        return new EnrichmentContext(txn, user, null, null, merchant, ub, dp, lp, mp);
    }

    private FeatureSet features(Map<String,Object> m) {
        return FeatureSet.of("fs-v1", m);
    }

    @Test
    void highAmountRuleBoundary() {
        // threshold is 50000
        assertThat(highAmount.evaluate(ctxWithAmount(new BigDecimal("49999.99")), features(Map.of())).triggered()).isFalse();
        assertThat(highAmount.evaluate(ctxWithAmount(new BigDecimal("50000.00")), features(Map.of())).triggered()).isTrue();
        assertThat(highAmount.evaluate(ctxWithAmount(new BigDecimal("50000.01")), features(Map.of())).triggered()).isTrue();
    }

    @Test
    void newDeviceRule() {
        EnrichmentContext ctx = ctxWithAmount(new BigDecimal("100"));
        assertThat(newDevice.evaluate(ctx, features(Map.of("is_new_device", 1))).triggered()).isTrue();
        assertThat(newDevice.evaluate(ctx, features(Map.of("is_new_device", 0))).triggered()).isFalse();
        assertThat(newDevice.evaluate(ctx, features(Map.of())) .triggered()).isFalse();
    }

    @Test
    void highVelocityRuleBoundary() {
        EnrichmentContext ctx = ctxWithAmount(new BigDecimal("100"));
        assertThat(highVelocity.evaluate(ctx, features(Map.of("transactions_last_1_hour", 9))).triggered()).isFalse();
        assertThat(highVelocity.evaluate(ctx, features(Map.of("transactions_last_1_hour", 10))).triggered()).isTrue();
        assertThat(highVelocity.evaluate(ctx, features(Map.of("transactions_last_1_hour", 11))).triggered()).isTrue();
    }

    @Test
    void newLocationRule() {
        EnrichmentContext ctx = ctxWithAmount(new BigDecimal("100"));
        assertThat(newLocation.evaluate(ctx, features(Map.of("is_new_location", 1))).triggered()).isTrue();
        assertThat(newLocation.evaluate(ctx, features(Map.of("is_new_location", 0))).triggered()).isFalse();
    }

    @Test
    void combinedSuspicionRequiresThreeSignals() {
        EnrichmentContext ctx = ctxWithAmount(new BigDecimal("100"));
        // 2 signals -> not triggered
        assertThat(combined.evaluate(ctx, features(Map.of("is_new_device",1,"is_new_location",1))).triggered()).isFalse();
        // 3 signals -> triggered
        assertThat(combined.evaluate(ctx, features(Map.of("is_new_device",1,"is_new_location",1,"transactions_last_1_hour",6))).triggered()).isTrue();
        // 4 signals -> triggered
        assertThat(combined.evaluate(ctx, features(Map.of("is_new_device",1,"is_new_location",1,"transactions_last_1_hour",6,"transaction_amount",15000.0))).triggered()).isTrue();
    }

    @Test
    void ruleEngineAggregates() {
        RuleEngine engine = new RuleEngine(java.util.List.of(highAmount, newDevice, highVelocity, newLocation, combined));
        EnrichmentContext ctx = ctxWithAmount(new BigDecimal("60000"));
        FeatureSet fs = features(Map.of("is_new_device",1,"is_new_location",1,"transactions_last_1_hour",12,"transaction_amount",60000.0,"user_account_age_days",10.0));
        RuleEngine.RuleEvaluationResult res = engine.evaluate(ctx, fs);
        assertThat(res.triggeredCount()).isGreaterThanOrEqualTo(3);
        assertThat(res.hasTriggeredRules()).isTrue();
    }
}
