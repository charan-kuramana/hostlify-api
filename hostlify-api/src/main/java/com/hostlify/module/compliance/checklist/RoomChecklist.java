package com.hostlify.module.compliance.checklist;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/** Room condition at move-in / move-out, with photos, confirmed (or disputed) by the resident. */
@Getter
@Setter
@Entity
@Table(name = "room_checklists")
public class RoomChecklist {

    public record Item(String key, String label, String condition, String note) {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "booking_id")
    private Long bookingId;

    /** MOVE_IN | MOVE_OUT */
    @Column(nullable = false, length = 10)
    private String kind;

    /** DRAFT | SUBMITTED | ACKNOWLEDGED | DISPUTED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @JsonIgnore
    @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
    private String itemsJson = "[]";

    @Column(name = "keys_handed")
    private Integer keysHanded;

    @Column(name = "meter_reading", length = 30)
    private String meterReading;

    @Column(name = "staff_notes", length = 2000)
    private String staffNotes;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "resident_response", length = 20)
    private String residentResponse;

    @Column(name = "resident_comment", length = 1000)
    private String residentComment;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @JsonIgnore
    @Column(name = "responded_ip", length = 64)
    private String respondedIp;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Filled by the service from itemsJson. */
    @Transient
    private List<Item> items;

    /** Filled by the service: [{id, url, caption}] */
    @Transient
    private List<java.util.Map<String, Object>> photos;
}
