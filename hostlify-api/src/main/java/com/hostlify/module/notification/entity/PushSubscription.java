package com.hostlify.module.notification.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One browser/device that agreed to receive push notifications for a user. */
@Getter
@Setter
@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_user_id", nullable = false)
    private Long authUserId;

    @Column(nullable = false, columnDefinition = "TEXT", unique = true)
    private String endpoint;

    @Column(nullable = false, length = 200)
    private String p256dh;

    @Column(nullable = false, length = 100)
    private String auth;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
