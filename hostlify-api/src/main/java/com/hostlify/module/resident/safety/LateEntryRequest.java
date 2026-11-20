package com.hostlify.module.resident.safety;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/** Coming back after curfew: asked in advance, approved by staff, marked at the gate. */
@Getter
@Setter
@Entity
@Table(name = "late_entry_requests")
public class LateEntryRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /** HH:mm */
    @Column(name = "expected_time", nullable = false, length = 5)
    private String expectedTime;

    @Column(nullable = false, length = 300)
    private String reason;

    /** PENDING | APPROVED | REJECTED | CANCELLED */
    @Column(nullable = false, length = 10)
    private String status = "PENDING";

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "staff_note", length = 300)
    private String staffNote;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private Map<String, Object> resident;
}
