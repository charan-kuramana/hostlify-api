package com.hostlify.module.platform;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** An owner's payment to the platform for a per-bed plan. */
@Getter
@Setter
@Entity
@Table(name = "platform_payments")
public class PlatformPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private int beds;

    @Column(nullable = false)
    private int months;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(nullable = false, length = 12)
    private String method;

    @Column(nullable = false, length = 10)
    private String status = "CREATED";

    @Column(name = "razorpay_order_id", length = 60)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 60)
    private String razorpayPaymentId;

    @Column(length = 100)
    private String reference;

    @Column(name = "period_from")
    private LocalDateTime periodFrom;

    @Column(name = "period_to")
    private LocalDateTime periodTo;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Transient private String ownerName;
}
