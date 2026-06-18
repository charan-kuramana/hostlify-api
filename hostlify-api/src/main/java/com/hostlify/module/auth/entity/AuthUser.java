package com.hostlify.module.auth.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "auth_users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_users_uuid", columnNames = "uuid"),
        @UniqueConstraint(name = "uk_auth_users_email", columnNames = "email")
    }
)
@SQLRestriction("is_deleted = false")
public class AuthUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 15)
    private String mobile;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "sub_role", length = 20)
    private String subRole;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "totp_secret", length = 100)
    private String totpSecret;
}
