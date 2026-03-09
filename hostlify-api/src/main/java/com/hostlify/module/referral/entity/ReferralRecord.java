package com.hostlify.module.referral.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(
    name = "referral_records",
    indexes = {
        @Index(name = "idx_rr_referrer_id", columnList = "referrer_id"),
        @Index(name = "idx_rr_referred_id", columnList = "referred_id")
    }
)
@SQLRestriction("is_deleted = 0")
public class ReferralRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referrer_id", nullable = false)
    private Long referrerId;

    @Column(name = "referred_id", nullable = false)
    private Long referredId;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReferrerId() { return referrerId; }
    public void setReferrerId(Long referrerId) { this.referrerId = referrerId; }
    public Long getReferredId() { return referredId; }
    public void setReferredId(Long referredId) { this.referredId = referredId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
}
