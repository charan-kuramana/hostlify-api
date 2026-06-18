package com.hostlify.module.referral.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(
    name = "referral_codes",
    uniqueConstraints = @UniqueConstraint(name = "uk_referral_code", columnNames = "code"),
    indexes = @Index(name = "idx_rc_hosteller_id", columnList = "hosteller_id")
)
@SQLRestriction("is_deleted = false")
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
}
