package com.hostlify.shared.security;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Holds parsed JWT claims for the currently authenticated user.
 * Used as the principal in SecurityContextHolder.
 */
@Getter
@Setter
public class UserPrincipal {

    private Long userId;
    private String uuid;
    private String role;       // HOSTELLER | HOSTEL_STAFF | SUPER_ADMIN
    private String subRole;    // null | OWNER | ADMIN | SUPERVISOR
    private String name;
    private String email;
    private String status;

    // Hosteller-specific
    private Long hostelId;
    private Long bookingId;
    private Long hostellerProfileId;

    // Staff-specific
    private Long ownerId;
    private List<Long> hostelIds;
    private List<String> permissions;

    public UserPrincipal() {}

    public boolean isHosteller() { return "HOSTELLER".equals(role); }
    public boolean isStaff() { return "HOSTEL_STAFF".equals(role); }
    public boolean isSuperAdmin() { return "SUPER_ADMIN".equals(role); }
    public boolean isOwner() { return "OWNER".equals(subRole); }
}
