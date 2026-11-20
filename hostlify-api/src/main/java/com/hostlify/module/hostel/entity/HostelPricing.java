package com.hostlify.module.hostel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "hostel_pricing")
public class HostelPricing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "room_type", nullable = false, length = 30)
    private String roomType;

    @Column(name = "base_rent", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseRent;

    /** Short-stay rates (null = not offered): per day, per 7 days, per 15 days. */
    @Column(name = "daily_rent", precision = 12, scale = 2)
    private BigDecimal dailyRent;

    @Column(name = "weekly_rent", precision = 12, scale = 2)
    private BigDecimal weeklyRent;

    @Column(name = "fortnight_rent", precision = 12, scale = 2)
    private BigDecimal fortnightRent;

    @Column(name = "deposit_months", nullable = false)
    private int depositMonths = 2;

    @Column(name = "late_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
