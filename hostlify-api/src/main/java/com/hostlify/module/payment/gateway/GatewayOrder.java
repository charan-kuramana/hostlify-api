package com.hostlify.module.payment.gateway;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** One checkout attempt through an owner's gateway (Razorpay order, Stripe Checkout session or PayPal order). */
@Getter
@Setter
@Entity
@Table(name = "payment_gateway_orders")
public class GatewayOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gateway_id", nullable = false)
    private Long gatewayId;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "due_id", nullable = false)
    private Long dueId;

    @Column(name = "amount_paise", nullable = false)
    private long amountPaise;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "provider_order_id", length = 120)
    private String providerOrderId;

    @Column(name = "provider_payment_id", length = 120)
    private String providerPaymentId;

    /** CREATED | PAID | FAILED | EXPIRED */
    @Column(nullable = false, length = 20)
    private String status = "CREATED";

    @Column(name = "payment_record_id")
    private Long paymentRecordId;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
