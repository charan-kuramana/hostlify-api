package com.hostlify.module.hostel.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "hostel_beds")
@SQLRestriction("is_deleted=0")
public class Bed extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "bed_name", nullable = false, length = 30)
    private String bedName;

    @Column(nullable = false, length = 20)
    private String status = "AVAILABLE";

    @Column(name = "reserved_for_hosteller_id")
    private Long reservedForHostellerId;

    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    @Column(name = "occupied_by_booking_id")
    private Long occupiedByBookingId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getHostelId() { return hostelId; }
    public void setHostelId(Long hostelId) { this.hostelId = hostelId; }
    public String getBedName() { return bedName; }
    public void setBedName(String bedName) { this.bedName = bedName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getReservedForHostellerId() { return reservedForHostellerId; }
    public void setReservedForHostellerId(Long reservedForHostellerId) { this.reservedForHostellerId = reservedForHostellerId; }
    public LocalDateTime getReservedAt() { return reservedAt; }
    public void setReservedAt(LocalDateTime reservedAt) { this.reservedAt = reservedAt; }
    public Long getOccupiedByBookingId() { return occupiedByBookingId; }
    public void setOccupiedByBookingId(Long occupiedByBookingId) { this.occupiedByBookingId = occupiedByBookingId; }
}
