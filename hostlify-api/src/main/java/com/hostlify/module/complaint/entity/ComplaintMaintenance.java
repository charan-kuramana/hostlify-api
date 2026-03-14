package com.hostlify.module.complaint.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "complaint_maintenance",
    indexes = {
        @Index(name = "idx_cm_hostel_id", columnList = "hostel_id"),
        @Index(name = "idx_cm_status",    columnList = "status")
    }
)
@SQLRestriction("is_deleted = 0")
public class ComplaintMaintenance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "room_id")
    private Long roomId;

    @Column(name = "reported_by", nullable = false)
    private Long reportedBy;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "estimated_cost", precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "actual_cost", precision = 12, scale = 2)
    private BigDecimal actualCost;

    @Column(name = "vendor_name", length = 200)
    private String vendorName;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
