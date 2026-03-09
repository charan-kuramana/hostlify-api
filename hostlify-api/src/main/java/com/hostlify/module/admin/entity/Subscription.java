package com.hostlify.module.admin.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(
    name = "admin_subscriptions",
    indexes = @Index(name = "idx_asub_owner_id", columnList = "owner_id")
)
@SQLRestriction("is_deleted = 0")
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public int getMaxHostels() { return maxHostels; }
    public void setMaxHostels(int maxHostels) { this.maxHostels = maxHostels; }
    public int getMaxBeds() { return maxBeds; }
    public void setMaxBeds(int maxBeds) { this.maxBeds = maxBeds; }
    public BigDecimal getPriceMonthly() { return priceMonthly; }
    public void setPriceMonthly(BigDecimal priceMonthly) { this.priceMonthly = priceMonthly; }
}
