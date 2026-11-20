package com.hostlify.module.resident.safety;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** "I'm not feeling well": staff check on the resident (food in room, doctor, …). */
@Getter
@Setter
@Entity
@Table(name = "wellness_flags")
public class WellnessFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(length = 300)
    private String note;

    /** comma list: FOOD_IN_ROOM, DOCTOR, MEDICINE, CHECK_ON_ME */
    @Column(length = 100)
    private String needs;

    /** OPEN | CLEARED */
    @Column(nullable = false, length = 10)
    private String status = "OPEN";

    @Column(name = "checked_by")
    private Long checkedBy;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(name = "staff_note", length = 300)
    private String staffNote;

    @Column(name = "cleared_at")
    private LocalDateTime clearedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private List<String> needList;

    @Transient
    private Map<String, Object> resident;
}
