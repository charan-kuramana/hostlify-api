package com.hostlify.module.hosteller.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "hosteller_emergency_contacts")
@SQLRestriction("is_deleted=0")
public class HostellerEmergencyContact extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hosteller_id", nullable = false)
    private Long hostellerId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String relationship;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(name = "alt_mobile", length = 15)
    private String altMobile;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getHostellerId() { return hostellerId; }
    public void setHostellerId(Long hostellerId) { this.hostellerId = hostellerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getAltMobile() { return altMobile; }
    public void setAltMobile(String altMobile) { this.altMobile = altMobile; }
}
