package com.hostlify.module.payment.entity;

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
    name = "payment_razorpay_orders",
    uniqueConstraints = @UniqueConstraint(name = "uk_razorpay_order_id", columnNames = "razorpay_order_id"),
    indexes = {
        @Index(name = "idx_pro_hosteller_id", columnList = "hosteller_id"),
        @Index(name = "idx_pro_due_id",       columnList = "due_id"),
        @Index(name = "idx_pro_status",       columnList = "status")
    }
)
@SQLRestriction("is_deleted = false")
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
}
