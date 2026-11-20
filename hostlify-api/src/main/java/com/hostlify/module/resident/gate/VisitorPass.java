package com.hostlify.module.resident.gate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** EXPECTED (pre-approved by the resident) → INSIDE → LEFT, or CANCELLED. Walk-ins start INSIDE. */
@Getter
@Setter
@Entity
@Table(name = "visitor_passes")
public class VisitorPass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "hosteller_id")
    private Long hostellerId;

    @Column(name = "visitor_name", nullable = false, length = 100)
    private String visitorName;

    @Column(name = "visitor_mobile", length = 15)
    private String visitorMobile;

    @Column(length = 40)
    private String relation;

    @Column(length = 150)
    private String purpose;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Column(name = "pass_code", nullable = false, length = 6)
    private String passCode;

    @Column(nullable = false, length = 12)
    private String status = "EXPECTED";

    @Column(name = "entry_at")
    private LocalDateTime entryAt;

    @Column(name = "exit_at")
    private LocalDateTime exitAt;

    @Column(name = "entry_by")
    private Long entryBy;

    @Column(name = "exit_by")
    private Long exitBy;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_by_role", nullable = false, length = 20)
    private String createdByRole;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient private String residentName;
    @Transient private String roomNumber;
}
