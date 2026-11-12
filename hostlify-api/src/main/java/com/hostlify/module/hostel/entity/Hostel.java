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

    /** Public page URL part, e.g. "bliss-pg-vijayawada-1". */
    @Column(nullable = false, length = 160, unique = true)
    private String slug;

    /** Owner's switch: show this hostel on the public listing pages. */
    @Column(name = "listing_enabled", nullable = false)
    private boolean listingEnabled = false;

    /** New hostels get a slug from name + city; the id suffix is added once the id exists. */
    @PrePersist
    void defaultSlug() {
        if (slug == null || slug.isBlank()) slug = slugify(name + "-" + city) + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);
    }

    public static String slugify(String s) {
        String out = (s == null ? "" : s).toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return out.length() > 140 ? out.substring(0, 140) : out;
    }
}
