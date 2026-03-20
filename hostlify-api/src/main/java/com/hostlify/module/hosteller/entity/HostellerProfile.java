package com.hostlify.module.hosteller.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "hosteller_profiles")
@SQLRestriction("is_deleted=0")
public class HostellerProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_user_id", nullable = false)
    private Long authUserId;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 15)
    private String mobile;

    @Column(name = "alt_mobile", length = 15)
    private String altMobile;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "id_proof_type", length = 30)
    private String idProofType;

    @Column(name = "id_proof_number", length = 30)
    private String idProofNumber;

    @Column(name = "id_proof_url", length = 500)
    private String idProofUrl;

    @Column(name = "user_type", nullable = false, length = 20)
    private String userType = "GENERAL";

    @Column(name = "company_college", length = 200)
    private String companyCollege;

    @Column(name = "permanent_address", columnDefinition = "TEXT")
    private String permanentAddress;

    @Column(length = 100)
    private String city;

    @Column(length = 10)
    private String pincode;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "joined_date")
    private LocalDate joinedDate;

    @Column(name = "hostel_id")
    private Long hostelId;

    public String getName() {
        String full = (firstName != null ? firstName : "") + (lastName != null ? " " + lastName : "");
        return full.trim();
    }

    /** Alias for mobile — used by frontend */
    public String getPhone() { return mobile; }

    /** Alias for joinedDate — used by frontend */
    public java.time.LocalDate getJoinDate() { return joinedDate; }

    @Transient private String roomNumber;
    @Transient private String type;
    @Transient private java.math.BigDecimal advance;
    @Transient private java.math.BigDecimal monthlyRent;
    @Transient private Long bookingId;
}
