package com.hostlify.module.hostel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "hostel_booking_settings")
public class HostelBookingSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false, unique = true)
    private Long hostelId;

    @Column(name = "self_booking_enabled", nullable = false)
    private boolean selfBookingEnabled = true;

    @Column(name = "requires_admin_approval", nullable = false)
    private boolean requiresAdminApproval = true;

    @Column(name = "auto_expire_days", nullable = false)
    private int autoExpireDays = 3;

    @Column(name = "max_pending_per_bed", nullable = false)
    private int maxPendingPerBed = 1;

    @Column(name = "geolocation_radius_km", nullable = false, precision = 5, scale = 2)
    private BigDecimal geolocationRadiusKm = new BigDecimal("10.00");

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
