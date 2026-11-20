package com.hostlify.module.compliance.aadhaar;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Result of an Aadhaar Secure QR check. Deliberately minimal (Aadhaar rules): no Aadhaar number (only the
 * last 4 digits UIDAI itself prints in the QR reference), no photo, no address, no copy of the QR.
 */
@Getter
@Setter
@Entity
@Table(name = "identity_verifications")
public class IdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "hostel_id")
    private Long hostelId;

    /** AADHAAR_SECURE_QR */
    @Column(nullable = false, length = 30)
    private String method = "AADHAAR_SECURE_QR";

    /** VERIFIED | FAILED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "aadhaar_last4", length = 4)
    private String aadhaarLast4;

    @Column(name = "name_on_aadhaar", length = 150)
    private String nameOnAadhaar;

    @Column(name = "dob_on_aadhaar", length = 12)
    private String dobOnAadhaar;

    @Column(name = "gender_on_aadhaar", length = 10)
    private String genderOnAadhaar;

    @Column(name = "state_on_aadhaar", length = 60)
    private String stateOnAadhaar;

    /** When UIDAI generated the QR (from its reference id). */
    @Column(name = "qr_generated_at")
    private LocalDateTime qrGeneratedAt;

    @Column(name = "name_matches")
    private Boolean nameMatches;

    @Column(name = "dob_matches")
    private Boolean dobMatches;

    /** Staff confirmed the photo in the QR matches the person. */
    @Column(name = "photo_confirmed_by")
    private Long photoConfirmedBy;

    @Column(name = "photo_confirmed_at")
    private LocalDateTime photoConfirmedAt;

    @Column(name = "consent_at", nullable = false)
    private LocalDateTime consentAt;

    @Column(name = "performed_by", nullable = false)
    private Long performedBy;

    @Column(name = "performed_by_role", nullable = false, length = 20)
    private String performedByRole;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
