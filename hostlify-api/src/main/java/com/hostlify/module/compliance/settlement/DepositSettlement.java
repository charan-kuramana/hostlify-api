package com.hostlify.module.compliance.settlement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Deposit settlement for one booking: deposit held − unpaid dues − deductions = refund (or balance still owed). */
@Getter
@Setter
@Entity
@Table(name = "deposit_settlements")
public class DepositSettlement {

    /** A deduction with an optional explanation and the checklist photos that back it up. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Deduction(String label, BigDecimal amount, String note, List<Long> photoIds) {
        public Deduction(String label, BigDecimal amount) { this(label, amount, null, null); }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "booking_id", nullable = false, unique = true)
    private Long bookingId;

    @Column(name = "deposit_held", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositHeld = BigDecimal.ZERO;

    @JsonIgnore
    @Column(name = "deductions_json", nullable = false, columnDefinition = "TEXT")
    private String deductionsJson = "[]";

    @Column(name = "unpaid_dues", nullable = false, precision = 12, scale = 2)
    private BigDecimal unpaidDues = BigDecimal.ZERO;

    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "balance_due", nullable = false, precision = 12, scale = 2)
    private BigDecimal balanceDue = BigDecimal.ZERO;

    /** DRAFT | FINALIZED | REFUNDED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(length = 1000)
    private String notes;

    @Column(name = "refund_method", length = 30)
    private String refundMethod;

    @Column(name = "refund_reference", length = 100)
    private String refundReference;

    @Column(name = "finalized_by")
    private Long finalizedBy;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** OPEN | RESOLVED — the resident disagreed with the settlement */
    @Column(name = "dispute_status", length = 10)
    private String disputeStatus;

    @Column(name = "dispute_reason", length = 1000)
    private String disputeReason;

    @Column(name = "disputed_at")
    private LocalDateTime disputedAt;

    @Column(name = "dispute_response", length = 1000)
    private String disputeResponse;

    @Column(name = "dispute_resolved_at")
    private LocalDateTime disputeResolvedAt;

    /** Deductions given up while resolving a dispute (already added to refundAmount) */
    @Column(name = "waived_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal waivedAmount = BigDecimal.ZERO;

    /** Refund promised by: check-out date + the hostel's refund days */
    @Transient
    private java.time.LocalDate refundDueBy;

    @Transient
    private boolean refundOverdue;

    /** Move-in / move-out photos that deductions can point to: [{id, url, caption, kind}] */
    @Transient
    private List<java.util.Map<String, Object>> evidencePhotos;

    @Transient
    private List<Deduction> deductions;

    /** Open dues included in unpaidDues, for display: [{id, label, outstanding}] */
    @Transient
    private List<java.util.Map<String, Object>> openDues;

    /** Move-out checklist items newly damaged/missing: suggestions for deductions. */
    @Transient
    private List<String> damageSuggestions;
}
