package com.hostlify.module.hostel.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "hostel_owners")
@SQLRestriction("is_deleted=0")
public class HostelOwner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_user_id", nullable = false)
    private Long authUserId;

    @Column(name = "business_name", nullable = false, length = 200)
    private String businessName;

    @Column(name = "owner_name", nullable = false, length = 150)
    private String ownerName;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "pan_number", length = 20)
    private String panNumber;

    @Column(name = "gst_number", length = 20)
    private String gstNumber;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "subscription_plan", nullable = false, length = 30)
    private String subscriptionPlan = "FREE";

    @Column(name = "plan_expires_at")
    private LocalDateTime planExpiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAuthUserId() { return authUserId; }
    public void setAuthUserId(Long authUserId) { this.authUserId = authUserId; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPanNumber() { return panNumber; }
    public void setPanNumber(String panNumber) { this.panNumber = panNumber; }
    public String getGstNumber() { return gstNumber; }
    public void setGstNumber(String gstNumber) { this.gstNumber = gstNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubscriptionPlan() { return subscriptionPlan; }
    public void setSubscriptionPlan(String subscriptionPlan) { this.subscriptionPlan = subscriptionPlan; }
    public LocalDateTime getPlanExpiresAt() { return planExpiresAt; }
    public void setPlanExpiresAt(LocalDateTime planExpiresAt) { this.planExpiresAt = planExpiresAt; }
}
