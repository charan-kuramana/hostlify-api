package com.hostlify.module.admin.entity;

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
    name = "admin_subscriptions",
    indexes = @Index(name = "idx_asub_owner_id", columnList = "owner_id")
)
@SQLRestriction("is_deleted = false")
public class Subscription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "plan_name", nullable = false, length = 30)
    private String planName;

    @Column(name = "max_hostels", nullable = false)
    private int maxHostels = 1;

    @Column(name = "max_beds", nullable = false)
    private int maxBeds = 50;

    @Column(name = "price_monthly", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceMonthly = BigDecimal.ZERO;
}
