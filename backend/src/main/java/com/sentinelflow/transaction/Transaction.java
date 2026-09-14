package com.sentinelflow.transaction;

import com.sentinelflow.identity.Device;
import com.sentinelflow.identity.Location;
import com.sentinelflow.identity.User;
import com.sentinelflow.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction extends BaseEntity {

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 64)
    private String transactionReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "transaction_type", length = 32)
    private String transactionType;

    @Column(name = "channel", length = 32)
    private String channel;

    @Column(name = "transaction_timestamp", nullable = false)
    private Instant transactionTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private TransactionStatus status = TransactionStatus.RECEIVED;

    protected Transaction() {
    }

    public Transaction(String transactionReference, User user, Merchant merchant,
                       Device device, Location location, BigDecimal amount, String currency,
                       String transactionType, String channel, Instant transactionTimestamp,
                       TransactionStatus status) {
        this.transactionReference = transactionReference;
        this.user = user;
        this.merchant = merchant;
        this.device = device;
        this.location = location;
        this.amount = amount;
        this.currency = currency;
        this.transactionType = transactionType;
        this.channel = channel;
        this.transactionTimestamp = transactionTimestamp;
        this.status = status == null ? TransactionStatus.RECEIVED : status;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public User getUser() {
        return user;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public Device getDevice() {
        return device;
    }

    public Location getLocation() {
        return location;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public String getChannel() {
        return channel;
    }

    public Instant getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
}
