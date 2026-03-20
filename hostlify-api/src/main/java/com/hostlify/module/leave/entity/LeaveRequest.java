package com.hostlify.module.leave.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "leave_requests",
    indexes = {
        @Index(name = "idx_lr_hosteller_id", columnList = "hosteller_id"),
        @Index(name = "idx_lr_booking_id",   columnList = "booking_id"),
        @Index(name = "idx_lr_status",       columnList = "status")
    }
)
@SQLRestriction("is_deleted = 0")
public class LeaveRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "leave_type", nullable = false, length = 20)
    private String leaveType;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Transient private String hostellerName;
    @Transient private String roomNumber;

    public java.time.LocalDateTime getAppliedAt() { return getCreatedAt(); }
}
