package com.hostlify.module.payment.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
    name = "payment_dues",
    indexes = {
        @Index(name = "idx_pd_hosteller_id", columnList = "hosteller_id"),
        @Index(name = "idx_pd_booking_id",   columnList = "booking_id"),
        @Index(name = "idx_pd_hostel_id",    columnList = "hostel_id"),
        @Index(name = "idx_pd_status",       columnList = "status")
    }
)
@SQLRestriction("is_deleted = false")
public class PaymentDue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @JsonProperty("amount")
    @Column(name = "rent_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "UNPAID";

    /** RENT | DEPOSIT | MESS | ELECTRICITY | FINE | LATE_FEE | OTHER */
    @Column(name = "due_type", nullable = false, length = 20)
    private String dueType = TYPE_RENT;

    /** Free text shown to the resident, e.g. "Electricity Oct: 82 units". */
    @Column(length = 300)
    private String description;

    /** For LATE_FEE dues: the rent due it was charged on. */
    @Column(name = "parent_due_id")
    private Long parentDueId;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "last_reminder_on")
    private LocalDate lastReminderOn;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "reminder_count", nullable = false)
    private int reminderCount = 0;

    public static final String TYPE_RENT = "RENT";
    public static final String TYPE_LATE_FEE = "LATE_FEE";
    public static final java.util.Set<String> TYPES =
            java.util.Set.of("RENT", "DEPOSIT", "MESS", "ELECTRICITY", "FINE", "LATE_FEE", "OTHER");
    /** Types staff may add by hand (rent comes from generation, late fee from the scheduler). */
    public static final java.util.Set<String> MANUAL_TYPES =
            java.util.Set.of("DEPOSIT", "MESS", "ELECTRICITY", "FINE", "OTHER");

    public boolean isOpen() {
        return "UNPAID".equals(status) || "PARTIAL".equals(status) || "OVERDUE".equals(status);
    }

    // Transient enrichment fields (populated by service, not persisted)
    @Transient
    private String hostellerName;

    /** Resident mobile, for staff WhatsApp one-tap reminders. */
    @Transient
    private String hostellerMobile;

    @Transient
    private String roomNumber;

    /** Sum of successful payments recorded against this due (staff views; partial payments). */
    @Transient
    private BigDecimal paidAmount;

    /** Resident's UPI payments waiting for staff confirmation. */
    @Transient
    private BigDecimal pendingVerificationAmount;
}
