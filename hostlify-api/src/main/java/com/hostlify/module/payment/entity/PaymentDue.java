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

    @Column(name = "booking_id", nullable = false)
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

    // Transient enrichment fields (populated by service, not persisted)
    @Transient
    private String hostellerName;

    @Transient
    private String roomNumber;

    /** Sum of successful payments recorded against this due (staff views; partial payments). */
    @Transient
    private BigDecimal paidAmount;
}
