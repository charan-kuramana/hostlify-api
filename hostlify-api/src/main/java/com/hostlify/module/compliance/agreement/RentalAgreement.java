package com.hostlify.module.compliance.agreement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One resident's agreement: the filled-in text is frozen when sent, so what was accepted can't change later. */
@Getter
@Setter
@Entity
@Table(name = "rental_agreements")
public class RentalAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "body_sha256", nullable = false, length = 64)
    private String bodySha256;

    /** SENT | ACCEPTED | VOID */
    @Column(nullable = false, length = 20)
    private String status = "SENT";

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "sent_at", insertable = false, updatable = false)
    private LocalDateTime sentAt;

    @JsonIgnore
    @Column(name = "otp_hash", length = 64)
    private String otpHash;

    @JsonIgnore
    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    @JsonIgnore
    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts = 0;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "accepted_name", length = 150)
    private String acceptedName;

    @Column(name = "accepted_ip", length = 64)
    private String acceptedIp;

    @JsonIgnore
    @Column(name = "accepted_user_agent", length = 300)
    private String acceptedUserAgent;

    @Column(name = "accepted_with_otp", nullable = false)
    private boolean acceptedWithOtp = false;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", length = 300)
    private String voidReason;
}
