package com.hostlify.module.hostel.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "hostel_floors")
@SQLRestriction("is_deleted=0")
public class Floor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "floor_name", nullable = false, length = 50)
    private String floorName;

    @Column(name = "floor_number", nullable = false)
    private int floorNumber;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHostelId() { return hostelId; }
    public void setHostelId(Long hostelId) { this.hostelId = hostelId; }
    public String getFloorName() { return floorName; }
    public void setFloorName(String floorName) { this.floorName = floorName; }
    public int getFloorNumber() { return floorNumber; }
    public void setFloorNumber(int floorNumber) { this.floorNumber = floorNumber; }
}
