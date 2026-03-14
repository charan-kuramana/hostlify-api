package com.hostlify.module.admin.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
    name = "admin_audit_log",
    indexes = {
        @Index(name = "idx_aal_actor_user", columnList = "actor_user_id"),
        @Index(name = "idx_aal_entity",     columnList = "entity_type,entity_id"),
        @Index(name = "idx_aal_hostel_id",  columnList = "hostel_id")
    }
)
public class AuditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "hostel_id")
    private Long hostelId;

    @Column(name = "before_state", columnDefinition = "JSON")
    private String beforeState;

    @Column(name = "after_state", columnDefinition = "JSON")
    private String afterState;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;
}
