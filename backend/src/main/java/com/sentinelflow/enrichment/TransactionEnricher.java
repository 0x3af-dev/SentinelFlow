package com.sentinelflow.enrichment;

import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.shared.dto.EnrichmentContext.UserBehaviorProfile;
import com.sentinelflow.shared.dto.EnrichmentContext.DeviceProfile;
import com.sentinelflow.shared.dto.EnrichmentContext.LocationProfile;
import com.sentinelflow.shared.dto.EnrichmentContext.MerchantProfile;
import com.sentinelflow.transaction.Transaction;

public interface TransactionEnricher {

    EnrichmentContext enrich(Transaction transaction);
}