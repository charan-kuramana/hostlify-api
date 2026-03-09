package com.hostlify.module.payment.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "payment_dues",
    indexes = {
        @Index(name = "idx_pd_hosteller_id", columnList = "hosteller_id"),
        @Index(name = "idx_pd_booking_id",   columnList = "booking_id"),
        @Index(name = "idx_pd_hostel_id",    columnList = "hostel_id"),
        @Index(name = "idx_pd_status",       columnList = "status")
    }
)
@SQLRestriction("is_deleted = 0")
public class PaymentDue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "rent_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal rentAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "UNPAID";

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHostellerId() { return hostellerId; }
    public void setHostellerId(Long hostellerId) { this.hostellerId = hostellerId; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public Long getHostelId() { return hostelId; }
    public void setHostelId(Long hostelId) { this.hostelId = hostelId; }
    public int getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(int periodMonth) { this.periodMonth = periodMonth; }
    public int getPeriodYear() { return periodYear; }
    public void setPeriodYear(int periodYear) { this.periodYear = periodYear; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public BigDecimal getRentAmount() { return rentAmount; }
    public void setRentAmount(BigDecimal rentAmount) { this.rentAmount = rentAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
