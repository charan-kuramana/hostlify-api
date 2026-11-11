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
    name = "referral_records",
    indexes = {
        @Index(name = "idx_rr_referrer_id", columnList = "referrer_id"),
        @Index(name = "idx_rr_referred_id", columnList = "referred_id")
    }
)
@SQLRestriction("is_deleted = false")
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

    /** Display name of the referred hosteller (populated for the referrer's list). */
    @Transient
    private String referredName;
}
