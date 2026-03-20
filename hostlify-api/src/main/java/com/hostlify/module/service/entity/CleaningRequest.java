package com.hostlify.module.service.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
    name = "service_cleaning_requests",
    indexes = {
        @Index(name = "idx_scr_hosteller_id", columnList = "hosteller_id"),
        @Index(name = "idx_scr_hostel_id",    columnList = "hostel_id")
    }
)
@SQLRestriction("is_deleted = 0")
public class CleaningRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "room_number", length = 20)
    private String roomNumber;

    @Column(name = "request_date", nullable = false)
    private LocalDate requestDate;

    /** PENDING | IN_PROGRESS | COMPLETED | CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(length = 500)
    private String notes;

    @Column(name = "completed_by")
    private Long completedBy;

    @Transient private String hostellerName;

    public java.time.LocalDate getPreferredDate() { return requestDate; }
}
