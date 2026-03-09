package com.hostlify.module.hostel.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getStaffUserId() { return staffUserId; }
    public void setStaffUserId(Long staffUserId) { this.staffUserId = staffUserId; }
    public Long getHostelId() { return hostelId; }
    public void setHostelId(Long hostelId) { this.hostelId = hostelId; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public boolean isIsActive() { return isActive; }
    public void setIsActive(boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
    public Long getAssignedBy() { return assignedBy; }
    public void setAssignedBy(Long assignedBy) { this.assignedBy = assignedBy; }
}
