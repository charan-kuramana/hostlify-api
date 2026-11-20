package com.hostlify.module.resident.gate;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A parcel received at the desk for a resident: WAITING → COLLECTED. */
@Getter
@Setter
@Entity
@Table(name = "parcels")
public class Parcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(length = 60)
    private String courier;

    @Column(length = 200)
    private String description;

    @Column(length = 30)
    private String shelf;

    @Column(nullable = false, length = 12)
    private String status = "WAITING";

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Column(name = "handed_by")
    private Long handedBy;

    @Column(name = "collected_by_name", length = 100)
    private String collectedByName;

    @Transient private String residentName;
    @Transient private String roomNumber;
}
