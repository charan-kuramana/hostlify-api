package com.hostlify.module.hostel.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "hostel_beds")
@SQLRestriction("is_deleted = false")
public class Bed extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "bed_name", nullable = false, length = 30)
    private String bedName;

    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";

    @Column(name = "reserved_for_hosteller_id")
    private Long reservedForHostellerId;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "occupied_by_booking_id")
    private Long occupiedByBookingId;
}
