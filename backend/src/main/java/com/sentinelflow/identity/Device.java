package com.sentinelflow.identity;

import com.sentinelflow.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "devices")
public class Device extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "device_reference", nullable = false, unique = true, length = 64)
    private String deviceReference;

    @Column(name = "device_type", length = 64)
    private String deviceType;

    @Column(name = "platform", length = 64)
    private String platform;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected Device() {
    }

    public Device(User user, String deviceReference, String deviceType, String platform,
                  Instant firstSeenAt, Instant lastSeenAt) {
        this.user = user;
        this.deviceReference = deviceReference;
        this.deviceType = deviceType;
        this.platform = platform;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
    }

    public User getUser() {
        return user;
    }

    public String getDeviceReference() {
        return deviceReference;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public String getPlatform() {
        return platform;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
