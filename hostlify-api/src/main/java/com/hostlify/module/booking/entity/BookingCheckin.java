package com.hostlify.module.booking.entity;

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
    name = "booking_checkins",
    indexes = {
        @Index(name = "idx_booking_checkins_hosteller", columnList = "hosteller_id"),
        @Index(name = "idx_booking_checkins_hostel", columnList = "hostel_id"),
        @Index(name = "idx_booking_checkins_bed", columnList = "bed_id"),
        @Index(name = "idx_booking_checkins_status", columnList = "status")
    }
)
@SQLRestriction("is_deleted = false")
public class BookingCheckin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "bed_id", nullable = false)
    private Long bedId;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "checkout_date")
    private LocalDate checkoutDate;

    @Column(name = "rent_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentAmount;

    @Column(name = "rent_frequency", nullable = false, length = 20)
    private String rentFrequency = "MONTHLY";

    @Column(name = "custom_frequency_days")
    private Integer customFrequencyDays;

    @Column(name = "advance_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal advanceAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "checkout_reason", columnDefinition = "TEXT")
    private String checkoutReason;
}
