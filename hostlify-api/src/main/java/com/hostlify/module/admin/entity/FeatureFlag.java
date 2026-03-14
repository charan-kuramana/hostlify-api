package com.hostlify.module.admin.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
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
}
