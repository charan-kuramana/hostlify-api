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

    public record Deduction(String label, BigDecimal amount) {}

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

    @Transient
    private List<Deduction> deductions;

    /** Open dues included in unpaidDues, for display: [{id, label, outstanding}] */
    @Transient
    private List<java.util.Map<String, Object>> openDues;

    /** Move-out checklist items newly damaged/missing: suggestions for deductions. */
    @Transient
    private List<String> damageSuggestions;
}
