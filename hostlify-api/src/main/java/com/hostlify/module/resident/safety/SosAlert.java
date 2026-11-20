package com.hostlify.module.resident.safety;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/** A resident pressed SOS: staff, supervisors and the owner are alerted with the resident's location. */
@Getter
@Setter
@Entity
@Table(name = "sos_alerts")
public class SosAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy_m")
    private Integer accuracyM;

    @Column(length = 300)
    private String message;

    /** OPEN | ACKNOWLEDGED | RESOLVED | CANCELLED */
    @Column(nullable = false, length = 12)
    private String status = "OPEN";

    @Column(name = "acknowledged_by")
    private Long acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution_note", length = 500)
    private String resolutionNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Resident details staff need right away (name, mobile, room, blood group, emergency contact, map link). */
    @Transient
    private Map<String, Object> resident;

    @Transient
    private String mapsUrl;
}
