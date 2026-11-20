package com.hostlify.module.ops;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A room's electricity or water meter reading for one month; billing splits it across the room's residents. */
@Getter
@Setter
@Entity
@Table(name = "meter_readings")
public class MeterReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "meter_type", nullable = false, length = 12)
    private String meterType;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "previous_reading", nullable = false, precision = 12, scale = 2)
    private BigDecimal previousReading;

    @Column(name = "current_reading", nullable = false, precision = 12, scale = 2)
    private BigDecimal currentReading;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal units;

    @Column(name = "rate_per_unit", nullable = false, precision = 8, scale = 2)
    private BigDecimal ratePerUnit;

    @Column(name = "fixed_charge", nullable = false, precision = 10, scale = 2)
    private BigDecimal fixedCharge = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String status = "DRAFT";

    @Column(name = "residents_billed")
    private Integer residentsBilled;

    @Column(name = "billed_at")
    private LocalDateTime billedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
