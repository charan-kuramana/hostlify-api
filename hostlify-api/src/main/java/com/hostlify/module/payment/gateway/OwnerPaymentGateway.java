package com.hostlify.module.payment.gateway;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One payment gateway account connected by a hostel owner. Residents' online payments go straight to it. */
@Getter
@Setter
@Entity
@Table(name = "owner_payment_gateways")
public class OwnerPaymentGateway {

    public static final String RAZORPAY = "RAZORPAY";
    public static final String STRIPE = "STRIPE";
    public static final String PAYPAL = "PAYPAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Null = all of the owner's hostels. */
    @Column(name = "hostel_id")
    private Long hostelId;

    @Column(nullable = false, length = 20)
    private String provider;

    /** TEST | LIVE */
    @Column(nullable = false, length = 10)
    private String mode = "TEST";

    @Column(length = 80)
    private String label;

    @Column(name = "public_key", length = 255)
    private String publicKey;

    @JsonIgnore
    @Column(name = "secret_enc", nullable = false, columnDefinition = "TEXT")
    private String secretEnc;

    @JsonIgnore
    @Column(name = "webhook_secret_enc", columnDefinition = "TEXT")
    private String webhookSecretEnc;

    @Column(name = "webhook_token", nullable = false, length = 40, unique = true)
    private String webhookToken;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_tested_at")
    private LocalDateTime lastTestedAt;

    @Column(name = "last_test_ok")
    private Boolean lastTestOk;

    @Column(name = "last_test_message", length = 300)
    private String lastTestMessage;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isLive() {
        return "LIVE".equals(mode);
    }
}
