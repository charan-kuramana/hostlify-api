package com.hostlify.module.auth;

import com.hostlify.module.auth.entity.AuthUser;
import com.hostlify.module.auth.repository.AuthUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {
    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AuthUserRepository authUserRepository, PasswordEncoder passwordEncoder) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (authUserRepository.count() == 0) {
            AuthUser staff = new AuthUser();
            staff.setUuid(UUID.randomUUID().toString());
            staff.setEmail("admin@test.com");
            staff.setPasswordHash(passwordEncoder.encode("Test@1234"));
            staff.setRole("HOSTEL_STAFF");
            staff.setSubRole("ADMIN");
            staff.setStatus("ACTIVE");
            authUserRepository.save(staff);
        }
    }
}
