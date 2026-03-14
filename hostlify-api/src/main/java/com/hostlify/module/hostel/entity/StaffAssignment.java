package com.hostlify.module.hostel.entity;

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
    name = "hostel_staff_assignments",
    indexes = {
        @Index(name = "idx_staff_assignments_staff_user", columnList = "staff_user_id"),
        @Index(name = "idx_staff_assignments_hostel", columnList = "hostel_id"),
        @Index(name = "idx_staff_assignments_owner", columnList = "owner_id")
    }
)
@SQLRestriction("is_deleted = 0")
public class StaffAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_user_id", nullable = false)
    private Long staffUserId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "assigned_by", nullable = false)
    private Long assignedBy;
}
