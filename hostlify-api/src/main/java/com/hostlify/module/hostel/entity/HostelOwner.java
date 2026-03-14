package com.hostlify.module.hostel.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Getter
@Setter
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
}
