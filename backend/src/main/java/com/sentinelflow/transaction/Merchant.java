package com.sentinelflow.transaction;

import com.sentinelflow.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "merchants")
public class Merchant extends BaseEntity {

    @Column(name = "external_reference", nullable = false, unique = true, length = 64)
    private String externalReference;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category")
    private String category;

    @Column(name = "country", length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MerchantStatus status = MerchantStatus.ACTIVE;

    protected Merchant() {
    }

    public Merchant(String externalReference, String name, String category, String country, MerchantStatus status) {
        this.externalReference = externalReference;
        this.name = name;
        this.category = category;
        this.country = country;
        this.status = status == null ? MerchantStatus.ACTIVE : status;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getCountry() {
        return country;
    }

    public MerchantStatus getStatus() {
        return status;
    }

    public void setStatus(MerchantStatus status) {
        this.status = status;
    }
}
