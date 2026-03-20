package com.hostlify.module.complaint.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "complaint_records",
    indexes = {
        @Index(name = "idx_cr_hosteller_id", columnList = "hosteller_id"),
        @Index(name = "idx_cr_hostel_id",    columnList = "hostel_id"),
        @Index(name = "idx_cr_status",       columnList = "status")
    }
)
@SQLRestriction("is_deleted = 0")
public class ComplaintRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM";

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "assigned_to")
    private Long assignedTo;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Transient private String hostellerName;
    @Transient private String roomNumber;
}
