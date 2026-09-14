package com.sentinelflow.identity;

import com.sentinelflow.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "external_reference", nullable = false, unique = true, length = 64)
    private String externalReference;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "contact_reference")
    private String contactReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    protected User() {
    }

    public User(String externalReference, String displayName, String contactReference, UserStatus status) {
        this.externalReference = externalReference;
        this.displayName = displayName;
        this.contactReference = contactReference;
        this.status = status == null ? UserStatus.ACTIVE : status;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public void setExternalReference(String externalReference) {
        this.externalReference = externalReference;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getContactReference() {
        return contactReference;
    }

    public void setContactReference(String contactReference) {
        this.contactReference = contactReference;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }
}
