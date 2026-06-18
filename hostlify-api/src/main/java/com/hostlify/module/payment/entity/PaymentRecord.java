package com.hostlify.module.payment.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
    name = "payment_records",
    indexes = {
        @Index(name = "idx_pr_hosteller_id", columnList = "hosteller_id"),
        @Index(name = "idx_pr_due_id",       columnList = "due_id"),
        @Index(name = "idx_pr_hostel_id",    columnList = "hostel_id")
    }
)
@SQLRestriction("is_deleted = false")
public class PaymentRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "due_id")
    private Long dueId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** RAZORPAY_UPI | RAZORPAY_CARD | RAZORPAY_NETBANKING | CASH | UPI_MANUAL | BANK_TRANSFER */
    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "razorpay_payment_id", unique = true, length = 100)
    private String razorpayPaymentId;

    @Column(name = "razorpay_order_id", length = 100)
    private String razorpayOrderId;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Column(length = 500)
    private String notes;

    @Column(name = "recorded_by")
    private Long recordedBy;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    /** SUCCESS | FAILED | PENDING | REFUNDED */
    @Column(nullable = false, length = 20)
    private String status = "SUCCESS";
}
