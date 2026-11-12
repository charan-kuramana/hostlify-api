package com.hostlify.module.listing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** A lead from the public hostel page: an enquiry, a visit request, or a waitlist entry. */
@Getter
@Setter
@Entity
@Table(name = "hostel_enquiries")
public class HostelEnquiry {

    public static final Set<String> STATUSES = Set.of("NEW", "CONTACTED", "VISIT_SCHEDULED", "WAITLIST", "JOINED", "LOST");
    public static final Set<String> SLOTS = Set.of("MORNING", "AFTERNOON", "EVENING");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(length = 150)
    private String email;

    @Column(length = 10)
    private String gender;

    @Column(name = "room_type", length = 30)
    private String roomType;

    @Column(name = "move_in_date")
    private LocalDate moveInDate;

    @Column(length = 1000)
    private String message;

    @Column(name = "visit_date")
    private LocalDate visitDate;

    @Column(name = "visit_slot", length = 20)
    private String visitSlot;

    @Column(nullable = false, length = 20)
    private String status = "NEW";

    @Column(name = "staff_notes", length = 2000)
    private String staffNotes;

    @Column(nullable = false, length = 20)
    private String source = "PUBLIC_PAGE";

    @Column(name = "last_contacted_at")
    private LocalDateTime lastContactedAt;

    @Column(name = "waitlist_nudged_on")
    private LocalDate waitlistNudgedOn;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;
}
