package com.hostlify.module.resident.checkout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** A resident asks to check out; staff inspect the room (bed, blanket, keys …) and approve or send it back. */
@Getter
@Setter
@Entity
@Table(name = "checkout_requests")
public class CheckoutRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "requested_date", nullable = false)
    private LocalDate requestedDate;

    @Column(length = 500)
    private String reason;

    /** REQUESTED | APPROVED | REJECTED | CANCELLED */
    @Column(nullable = false, length = 12)
    private String status = "REQUESTED";

    @JsonIgnore
    @Column(name = "items_json", columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "staff_note", length = 500)
    private String staffNote;

    /** Notice estimate shown to the resident when they asked (JSON) */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "estimate_json", columnDefinition = "TEXT")
    private String estimateJson;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    // view-only
    @Transient private List<Map<String, Object>> items;
    @Transient private String residentName;
    @Transient private String residentMobile;
    @Transient private String roomName;
    @Transient private String bedName;
    @Transient private LocalDate checkinDate;
    @Transient private Map<String, Object> estimate;
}
