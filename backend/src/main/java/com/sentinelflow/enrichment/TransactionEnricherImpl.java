package com.sentinelflow.enrichment;

import com.sentinelflow.identity.Device;
import com.sentinelflow.identity.DeviceRepository;
import com.sentinelflow.identity.Location;
import com.sentinelflow.identity.LocationRepository;
import com.sentinelflow.identity.User;
import com.sentinelflow.identity.UserRepository;
import com.sentinelflow.shared.dto.EnrichmentContext;
import com.sentinelflow.transaction.Merchant;
import com.sentinelflow.transaction.MerchantRepository;
import com.sentinelflow.transaction.Transaction;
import com.sentinelflow.transaction.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionEnricherImpl implements TransactionEnricher {

    private static final Logger log = LoggerFactory.getLogger(TransactionEnricherImpl.class);

    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final LocationRepository locationRepository;
    private final MerchantRepository merchantRepository;
    private final TransactionRepository transactionRepository;

    public TransactionEnricherImpl(UserRepository userRepository,
                                   DeviceRepository deviceRepository,
                                   LocationRepository locationRepository,
                                   MerchantRepository merchantRepository,
                                   TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.deviceRepository = deviceRepository;
        this.locationRepository = locationRepository;
        this.merchantRepository = merchantRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public EnrichmentContext enrich(Transaction transaction) {
        log.debug("Enriching transaction: {}", transaction.getTransactionReference());

        User user = userRepository.findById(transaction.getUser().getId())
                .orElseThrow(() -> new EnrichmentException("User not found: " + transaction.getUser().getId()));

        Device device = transaction.getDevice() != null
                ? deviceRepository.findById(transaction.getDevice().getId()).orElse(null)
                : null;

        Location location = transaction.getLocation() != null
                ? locationRepository.findById(transaction.getLocation().getId()).orElse(null)
                : null;

        Merchant merchant = merchantRepository.findById(transaction.getMerchant().getId())
                .orElseThrow(() -> new EnrichmentException("Merchant not found: " + transaction.getMerchant().getId()));

        EnrichmentContext.UserBehaviorProfile userBehavior = buildUserBehaviorProfile(user, transaction);
        EnrichmentContext.DeviceProfile deviceProfile = buildDeviceProfile(device, user, transaction);
        EnrichmentContext.LocationProfile locationProfile = buildLocationProfile(location, user, transaction);
        EnrichmentContext.MerchantProfile merchantProfile = buildMerchantProfile(merchant);

        return new EnrichmentContext(
                transaction, user, device, location, merchant,
                userBehavior, deviceProfile, locationProfile, merchantProfile
        );
    }

    private EnrichmentContext.UserBehaviorProfile buildUserBehaviorProfile(User user, Transaction transaction) {
        Instant txnTime = transaction.getTransactionTimestamp();
        Instant dayAgo = txnTime.minusSeconds(24 * 3600);
        Instant weekAgo = txnTime.minusSeconds(7 * 24 * 3600);
        Instant monthAgo = txnTime.minusSeconds(30 * 24 * 3600);

        List<Transaction> recent24hAll = transactionRepository.findByUser_IdAndTransactionTimestampAfter(user.getId(), dayAgo);
        List<Transaction> recent7dAll = transactionRepository.findByUser_IdAndTransactionTimestampAfter(user.getId(), weekAgo);
        List<Transaction> recent30dAll = transactionRepository.findByUser_IdAndTransactionTimestampAfter(user.getId(), monthAgo);

        // Exclude current transaction from historical counts
        List<Transaction> recent24h = recent24hAll.stream().filter(t -> !t.getId().equals(transaction.getId())).toList();
        List<Transaction> recent7d = recent7dAll.stream().filter(t -> !t.getId().equals(transaction.getId())).toList();
        List<Transaction> recent30d = recent30dAll.stream().filter(t -> !t.getId().equals(transaction.getId())).toList();

        BigDecimal total24h = recent24h.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total7d = recent7d.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total30d = recent30d.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgAmount = recent30d.isEmpty() ? BigDecimal.ZERO
                : total30d.divide(BigDecimal.valueOf(recent30d.size()), 4, BigDecimal.ROUND_HALF_UP);

        Instant firstTxn = transactionRepository.findFirstByUser_IdOrderByTransactionTimestampAsc(user.getId())
                .map(Transaction::getTransactionTimestamp).orElse(txnTime);
        Instant lastTxn = transactionRepository.findFirstByUser_IdOrderByTransactionTimestampDesc(user.getId())
                .map(Transaction::getTransactionTimestamp).orElse(txnTime);
        // if firstTxn is current txn, adjust
        if (firstTxn.equals(txnTime) && recent30d.isEmpty()) {
            // no prior history
        }

        long knownDevices = deviceRepository.countByUser_Id(user.getId());
        long knownLocations = locationRepository.countByUser_Id(user.getId());

        return new EnrichmentContext.UserBehaviorProfile(
                recent24h.size(),
                recent7d.size(),
                recent30d.size(),
                total24h,
                total7d,
                total30d,
                avgAmount,
                firstTxn,
                lastTxn,
                (int) knownDevices,
                (int) knownLocations
        );
    }

    private EnrichmentContext.DeviceProfile buildDeviceProfile(Device device, User user, Transaction transaction) {
        if (device == null) {
            return new EnrichmentContext.DeviceProfile(true, 0, 0, null, null);
        }

        boolean isNewDevice = !device.getUser().getId().equals(user.getId());
        if (!isNewDevice) {
            // Check if this device has been used by this user before in transactions, excluding current
            List<Transaction> userDeviceTxns = transactionRepository.findByUser_IdAndDevice_Id(user.getId(), device.getId())
                    .stream().filter(t -> !t.getId().equals(transaction.getId())).toList();
            isNewDevice = userDeviceTxns.isEmpty();
        }

        long deviceTxnCount = transactionRepository.countByDevice_Id(device.getId()) - 1; // exclude current
        if (deviceTxnCount < 0) deviceTxnCount = 0;
        long deviceUserCount = transactionRepository.findDistinctUserIdsByDeviceId(device.getId()).size();

        return new EnrichmentContext.DeviceProfile(
                isNewDevice,
                deviceTxnCount,
                deviceUserCount,
                device.getFirstSeenAt(),
                device.getLastSeenAt()
        );
    }

    private EnrichmentContext.LocationProfile buildLocationProfile(Location location, User user, Transaction transaction) {
        if (location == null) {
            return new EnrichmentContext.LocationProfile(true, 0, null, null, null);
        }

        boolean isNewLocation = !location.getUser().getId().equals(user.getId());
        if (!isNewLocation) {
            List<Transaction> userLocationTxns = transactionRepository.findByUser_IdAndLocation_Id(user.getId(), location.getId())
                    .stream().filter(t -> !t.getId().equals(transaction.getId())).toList();
            isNewLocation = userLocationTxns.isEmpty();
        }

        long locationTxnCount = transactionRepository.countByLocation_Id(location.getId()) - 1;
        if (locationTxnCount < 0) locationTxnCount = 0;

        return new EnrichmentContext.LocationProfile(
                isNewLocation,
                locationTxnCount,
                location.getCountry(),
                location.getRegion(),
                location.getCity()
        );
    }

    private EnrichmentContext.MerchantProfile buildMerchantProfile(Merchant merchant) {
        // Note: In a real system, this would query transaction counts per merchant
        // For now, using a placeholder
        return new EnrichmentContext.MerchantProfile(
                0L,
                merchant.getCategory(),
                merchant.getCountry()
        );
    }

    public static class EnrichmentException extends RuntimeException {
        public EnrichmentException(String message) {
            super(message);
        }
    }
}