package com.hostlify.module.referral.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(
    name = "referral_codes",
    uniqueConstraints = @UniqueConstraint(name = "uk_referral_code", columnNames = "code"),
    indexes = @Index(name = "idx_rc_hosteller_id", columnList = "hosteller_id")
)
@SQLRestriction("is_deleted = 0")
public class ReferralCode extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "total_referrals", nullable = false)
    private int totalReferrals = 0;

    @Column(name = "total_discount_earned", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalDiscountEarned = BigDecimal.ZERO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHostellerId() { return hostellerId; }
    public void setHostellerId(Long hostellerId) { this.hostellerId = hostellerId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public int getTotalReferrals() { return totalReferrals; }
    public void setTotalReferrals(int totalReferrals) { this.totalReferrals = totalReferrals; }
    public BigDecimal getTotalDiscountEarned() { return totalDiscountEarned; }
    public void setTotalDiscountEarned(BigDecimal totalDiscountEarned) { this.totalDiscountEarned = totalDiscountEarned; }
}
