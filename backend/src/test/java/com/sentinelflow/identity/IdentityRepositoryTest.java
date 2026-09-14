package com.sentinelflow.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class IdentityRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    UserRepository users;

    @Autowired
    DeviceRepository devices;

    @Autowired
    LocationRepository locations;

    @Test
    void createAndRetrieveUser() {
        User user = users.save(new User("USR-001", "Aarav Sharma", "aarav@example.com", UserStatus.ACTIVE));

        assertThat(user.getId()).isNotNull();
        assertThat(users.findByExternalReference("USR-001")).isPresent();
        assertThat(users.findById(user.getId())).isPresent();
    }

    @Test
    void duplicateExternalReferenceRejected() {
        users.save(new User("USR-DUP", "User One", null, UserStatus.ACTIVE));

        assertThatThrownBy(() -> users.saveAndFlush(new User("USR-DUP", "User Two", null, UserStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deviceRelationshipAndInvalidUserRejected() {
        User user = users.save(new User("USR-DEV", "Diya Patel", null, UserStatus.ACTIVE));
        Device device = devices.save(new Device(user, "DEV-001", "MOBILE", "ANDROID",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z")));

        assertThat(device.getId()).isNotNull();
        assertThat(devices.findByUserId(user.getId())).hasSize(1);
        assertThat(devices.findByDeviceReference("DEV-001")).isPresent();

        // Invalid user FK must fail: transient user with random id is not persisted
        User ghost = new User("GHOST", "Ghost", null, UserStatus.ACTIVE);
        Device orphan = new Device(ghost, "DEV-ORPHAN", "MOBILE", "IOS", null, null);
        assertThatThrownBy(() -> devices.saveAndFlush(orphan))
                .isInstanceOf(Exception.class);
    }

    @Test
    void duplicateDeviceReferenceRejected() {
        User user = users.save(new User("USR-DEVDUP", "Kabir Rao", null, UserStatus.ACTIVE));
        devices.save(new Device(user, "DEV-DUP", "MOBILE", "ANDROID", null, null));

        assertThatThrownBy(() -> devices.saveAndFlush(new Device(user, "DEV-DUP", "DESKTOP", "WEB", null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void locationRelationshipWorks() {
        User user = users.save(new User("USR-LOC", "Meera Nair", null, UserStatus.ACTIVE));
        Location location = locations.save(new Location(user, "IN", "Karnataka", "Bengaluru",
                12.9716, 77.5946, Instant.parse("2026-01-01T00:00:00Z"), null));

        assertThat(location.getId()).isNotNull();
        assertThat(locations.findByUserId(user.getId())).hasSize(1);
    }

    @Test
    void invalidLocationUserRejected() {
        User ghost = new User("GHOST-LOC", "Ghost", null, UserStatus.ACTIVE);
        Location orphan = new Location(ghost, "IN", "Karnataka", "Bengaluru",
                12.9, 77.5, null, null);
        assertThatThrownBy(() -> locations.saveAndFlush(orphan))
                .isInstanceOf(Exception.class);
    }
}
