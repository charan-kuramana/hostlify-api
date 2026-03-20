package com.hostlify.module.service.entity;

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
    name = "service_parking",
    indexes = {
        @Index(name = "idx_sp_hosteller_id", columnList = "hosteller_id"),
        @Index(name = "idx_sp_hostel_id",    columnList = "hostel_id")
    }
)
@SQLRestriction("is_deleted = 0")
public class ParkingRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "vehicle_number", nullable = false, length = 20)
    private String vehicleNumber;

    @Column(name = "vehicle_type", nullable = false, length = 20)
    private String vehicleType;

    @Column(name = "slot_number", length = 20)
    private String slotNumber;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Transient private String hostellerName;
    @Transient private String roomNumber;
}
