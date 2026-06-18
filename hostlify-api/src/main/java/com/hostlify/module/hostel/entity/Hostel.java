package com.hostlify.module.hostel.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "hostel_hostels")
@SQLRestriction("is_deleted = false")
public class Hostel extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "hostel_type", nullable = false, length = 20)
    private String hostelType;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 10)
    private String pincode;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "google_place_id", length = 100)
    private String googlePlaceId;

    @Column(length = 255)
    private String landmark;

    @Column(columnDefinition = "TEXT")
    private String about;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "total_floors", nullable = false)
    private int totalFloors = 0;

    @Column(name = "total_rooms", nullable = false)
    private int totalRooms = 0;

    @Column(name = "total_beds", nullable = false)
    private int totalBeds = 0;

    @Column(name = "default_rent", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultRent = BigDecimal.ZERO;

    @Column(name = "online_payment_enabled", nullable = false)
    private boolean onlinePaymentEnabled = true;
}
