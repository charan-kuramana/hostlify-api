package com.hostlify.module.admin.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(
    name = "admin_feature_flags",
    indexes = {
        @Index(name = "idx_aff_owner_id",  columnList = "owner_id"),
        @Index(name = "idx_aff_flag_name", columnList = "flag_name")
    }
)
@SQLRestriction("is_deleted = 0")
public class FeatureFlag extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "flag_name", nullable = false, length = 80)
    private String flagName;

    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @Column(name = "set_by", nullable = false)
    private Long setBy;

    @Column(length = 300)
    private String reason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getFlagName() { return flagName; }
    public void setFlagName(String flagName) { this.flagName = flagName; }
    public boolean isIsEnabled() { return isEnabled; }
    public void setIsEnabled(boolean isEnabled) { this.isEnabled = isEnabled; }
    public Long getSetBy() { return setBy; }
    public void setSetBy(Long setBy) { this.setBy = setBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
