package com.hostlify.module.hostel.policy;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A hostel's fine print (lock-in, fees, refund time), house rules and safety facilities, shown before booking. */
@Getter
@Setter
@Entity
@Table(name = "hostel_policies")
public class HostelPolicy {

    @Id
    @Column(name = "hostel_id")
    private Long hostelId;

    @Column(name = "lock_in_months", nullable = false)
    private int lockInMonths = 0;

    @Column(name = "early_exit_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal earlyExitFee = BigDecimal.ZERO;

    @Column(name = "admission_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal admissionFee = BigDecimal.ZERO;

    @Column(name = "deposit_refund_days", nullable = false)
    private int depositRefundDays = 15;

    @Column(name = "other_charges", length = 1000)
    private String otherCharges;

    /** HH:mm, null = no curfew */
    @Column(name = "curfew_time", length = 5)
    private String curfewTime;

    /** NOT_ALLOWED | DAYTIME | OVERNIGHT_WITH_APPROVAL */
    @Column(name = "guest_policy", nullable = false, length = 30)
    private String guestPolicy = "DAYTIME";

    /** One rule per line */
    @Column(name = "house_rules", columnDefinition = "TEXT")
    private String houseRules;

    @Column(name = "late_entry_requests", nullable = false)
    private boolean lateEntryRequests = true;

    /** e.g. MON,THU; null = daily */
    @Column(name = "room_cleaning_days", length = 40)
    private String roomCleaningDays;

    @Column(name = "safety_features", length = 400)
    private String safetyFeatures;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
