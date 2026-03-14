package com.hostlify.module.hostel.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "hostel_rooms")
@SQLRestriction("is_deleted=0")
public class Room extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "room_name", nullable = false, length = 50)
    private String roomName;

    @Column(name = "room_type", nullable = false, length = 30)
    private String roomType;

    @Column(nullable = false)
    private int capacity = 1;

    @Column(name = "current_occupancy", nullable = false)
    private int currentOccupancy = 0;

    @Column(name = "rent_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentAmount = BigDecimal.ZERO;
}
