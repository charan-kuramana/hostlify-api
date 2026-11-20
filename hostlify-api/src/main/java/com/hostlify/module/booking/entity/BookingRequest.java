package com.hostlify.module.booking.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "booking_requests",
    indexes = {
        @Index(name = "idx_br_hosteller", columnList = "hosteller_user_id"),
        @Index(name = "idx_br_hostel", columnList = "hostel_id"),
        @Index(name = "idx_br_status", columnList = "status")
    }
)
@SQLRestriction("is_deleted = false")
public class BookingRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_user_id", nullable = false)
    private Long hostellerUserId;

    @Column(name = "hosteller_profile_id", nullable = false)
    private Long hostellerProfileId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "bed_id", nullable = false)
    private Long bedId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "preference_notes", columnDefinition = "TEXT")
    private String preferenceNotes;

    /** MONTHLY | SHORT */
    @Column(name = "stay_type", nullable = false, length = 10)
    private String stayType = "MONTHLY";

    /** Planned check-out for a short stay. */
    @Column(name = "check_out_date")
    private java.time.LocalDate checkOutDate;

    /** Short stay total shown to the resident when they booked. */
    @Column(name = "quoted_amount", precision = 12, scale = 2)
    private java.math.BigDecimal quotedAmount;

    /** Cost of stay + rules the resident accepted when requesting (JSON) */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "terms_json", columnDefinition = "TEXT")
    private String termsJson;

    @Column(name = "terms_accepted_at")
    private LocalDateTime termsAcceptedAt;

    @Column(nullable = false, length = 30)
    private String status = "PENDING_APPROVAL";

    @Column(name = "reviewed_by_staff_id")
    private Long reviewedByStaffId;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "reassigned_bed_id")
    private Long reassignedBedId;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "booking_checkin_id")
    private Long bookingCheckinId;
}
