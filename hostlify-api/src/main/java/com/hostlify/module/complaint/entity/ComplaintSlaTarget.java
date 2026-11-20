package com.hostlify.module.complaint.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/** Target hours to fix a complaint of one category in one hostel. */
@Getter
@Setter
@Entity
@Table(name = "complaint_sla_targets")
public class ComplaintSlaTarget {

    /** Used when a hostel hasn't set its own target for a category. */
    public static final Map<String, Integer> DEFAULT_HOURS = Map.of(
            "Maintenance", 48, "Cleanliness", 24, "Food", 24, "Noise", 12, "Security", 4, "Lost & stolen", 24, "Other", 72);
    public static final int FALLBACK_HOURS = 48;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(name = "target_hours", nullable = false)
    private int targetHours;
}
