package com.hostlify.module.booking.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "booking_checkins",
    indexes = {
        @Index(name = "idx_booking_checkins_hosteller", columnList = "hosteller_id"),
        @Index(name = "idx_booking_checkins_hostel", columnList = "hostel_id"),
        @Index(name = "idx_booking_checkins_bed", columnList = "bed_id"),
        @Index(name = "idx_booking_checkins_status", columnList = "status")
    }
)
@SQLRestriction("is_deleted = 0")
public class BookingCheckin extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "floor_id", nullable = false)
    private Long floorId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "bed_id", nullable = false)
    private Long bedId;

    @Column(name = "checkin_date", nullable = false)
    private LocalDate checkinDate;

    @Column(name = "checkout_date")
    private LocalDate checkoutDate;

    @Column(name = "rent_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentAmount;

    @Column(name = "rent_frequency", nullable = false, length = 20)
    private String rentFrequency = "MONTHLY";

    @Column(name = "custom_frequency_days")
    private Integer customFrequencyDays;

    @Column(name = "advance_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal advanceAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "checkout_reason", columnDefinition = "TEXT")
    private String checkoutReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHostellerId() { return hostellerId; }
    public void setHostellerId(Long hostellerId) { this.hostellerId = hostellerId; }
    public Long getHostelId() { return hostelId; }
    public void setHostelId(Long hostelId) { this.hostelId = hostelId; }
    public Long getFloorId() { return floorId; }
    public void setFloorId(Long floorId) { this.floorId = floorId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getBedId() { return bedId; }
    public void setBedId(Long bedId) { this.bedId = bedId; }
    public LocalDate getCheckinDate() { return checkinDate; }
    public void setCheckinDate(LocalDate checkinDate) { this.checkinDate = checkinDate; }
    public LocalDate getCheckoutDate() { return checkoutDate; }
    public void setCheckoutDate(LocalDate checkoutDate) { this.checkoutDate = checkoutDate; }
    public BigDecimal getRentAmount() { return rentAmount; }
    public void setRentAmount(BigDecimal rentAmount) { this.rentAmount = rentAmount; }
    public String getRentFrequency() { return rentFrequency; }
    public void setRentFrequency(String rentFrequency) { this.rentFrequency = rentFrequency; }
    public Integer getCustomFrequencyDays() { return customFrequencyDays; }
    public void setCustomFrequencyDays(Integer customFrequencyDays) { this.customFrequencyDays = customFrequencyDays; }
    public BigDecimal getAdvanceAmount() { return advanceAmount; }
    public void setAdvanceAmount(BigDecimal advanceAmount) { this.advanceAmount = advanceAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCheckoutReason() { return checkoutReason; }
    public void setCheckoutReason(String checkoutReason) { this.checkoutReason = checkoutReason; }
}
