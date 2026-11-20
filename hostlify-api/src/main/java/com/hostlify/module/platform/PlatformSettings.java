package com.hostlify.module.platform;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Single-row platform pricing (id = 1). */
@Getter
@Setter
@Entity
@Table(name = "platform_settings")
public class PlatformSettings {

    @Id
    private Integer id = 1;

    @Column(name = "free_bed_limit", nullable = false)
    private int freeBedLimit = 30;

    @Column(name = "price_per_bed", nullable = false, precision = 8, scale = 2)
    private BigDecimal pricePerBed = new BigDecimal("20.00");

    @Column(name = "grace_days", nullable = false)
    private int graceDays = 7;

    @Column(name = "premium_badge", nullable = false)
    private boolean premiumBadge = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
