package com.hostlify.module.payment.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "payment_razorpay_orders",
    uniqueConstraints = @UniqueConstraint(name = "uk_razorpay_order_id", columnNames = "razorpay_order_id"),
    indexes = {
        @Index(name = "idx_pro_hosteller_id", columnList = "hosteller_id"),
        @Index(name = "idx_pro_due_id",       columnList = "due_id"),
        @Index(name = "idx_pro_status",       columnList = "status")
    }
)
@SQLRestriction("is_deleted = 0")
public class PaymentRazorpayOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "due_id", nullable = false)
    private Long dueId;

    @Column(name = "razorpay_order_id", nullable = false, unique = true, length = 100)
    private String razorpayOrderId;

    @Column(name = "amount_paise", nullable = false)
    private Long amountPaise;

    @Column(nullable = false, length = 5)
    private String currency = "INR";

    @Column(nullable = false, length = 30)
    private String status = "CREATED";

    @Column(name = "razorpay_response_json", columnDefinition = "TEXT")
    private String razorpayResponseJson;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHostellerId() { return hostellerId; }
    public void setHostellerId(Long hostellerId) { this.hostellerId = hostellerId; }
    public Long getDueId() { return dueId; }
    public void setDueId(Long dueId) { this.dueId = dueId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public Long getAmountPaise() { return amountPaise; }
    public void setAmountPaise(Long amountPaise) { this.amountPaise = amountPaise; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRazorpayResponseJson() { return razorpayResponseJson; }
    public void setRazorpayResponseJson(String razorpayResponseJson) { this.razorpayResponseJson = razorpayResponseJson; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
