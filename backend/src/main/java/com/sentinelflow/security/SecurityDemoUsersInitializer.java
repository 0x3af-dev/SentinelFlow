package com.sentinelflow.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class SecurityDemoUsersInitializer implements ApplicationRunner {

    private final SecurityUserRepository repository;
    private final PasswordEncoder encoder;

    SecurityDemoUsersInitializer(SecurityUserRepository repository,
                                  PasswordEncoder encoder,
                                  SecurityProperties props) {
        this.repository = repository;
        this.encoder = encoder;
        this.enabled = props.seedDemoUsers();
    }

    private final boolean enabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || repository.count() > 0) {
            return;
        }
        seed("analyst",        "analyst-demo",        "ANALYST",  "Analyst User");
        seed("investigator",   "investigator-demo",   "INVESTIGATOR", "Investigator User");
        seed("operator",       "operator-demo",       "OPERATOR", "Operator User");
        seed("admin",          "admin-demo",          "ADMIN",    "Admin User");
    }

    private void seed(String username, String password, String role, String displayName) {
        // Id left null on purpose: an assigned UUID makes Spring Data treat the
        // entity as detached (merge -> UPDATE) instead of new (persist -> INSERT).
        repository.save(new SecurityUser(
                null,
                username,
                encoder.encode(password),
                role,
                true,
                displayName,
                java.time.Instant.now()));
    }
}
