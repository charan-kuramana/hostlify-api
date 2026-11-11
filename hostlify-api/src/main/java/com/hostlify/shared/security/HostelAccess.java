package com.hostlify.shared.security;

import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Tenant-isolation and sub-role checks shared by controllers.
 *
 * A staff JWT carries the hostel ids the user is assigned to (hostel_ids); a hosteller JWT carries
 * their hostelId / hostellerProfileId. Every endpoint that takes a hostel id, or loads a record that
 * belongs to a hostel, must call one of these before reading or writing — @PreAuthorize alone only
 * checks the role, so without this any staff user could read or modify any other hostel's data.
 */
public final class HostelAccess {

    private HostelAccess() {}

    public static UserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal p)) {
            throw new AppException("Not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return p;
    }

    /** Staff assigned to the hostel (or super admin). Hostellers are rejected. */
    public static void requireStaffHostel(Long hostelId) {
        UserPrincipal p = principal();
        if (p.isSuperAdmin()) return;
        if (!p.isStaff() || hostelId == null || p.getHostelIds() == null || !p.getHostelIds().contains(hostelId)) {
            throw denied();
        }
    }

    /** Staff assigned to the hostel, a hosteller living in it, or super admin. */
    public static void requireHostelMember(Long hostelId) {
        UserPrincipal p = principal();
        if (p.isSuperAdmin()) return;
        if (p.isStaff()) { requireStaffHostel(hostelId); return; }
        if (p.isHosteller() && hostelId != null && hostelId.equals(p.getHostelId())) return;
        throw denied();
    }

    /**
     * Access to one hosteller's record: the hosteller themself, staff of the hostel the hosteller
     * belongs to, or super admin. {@code profileHostelId} may be null for a not-yet-allocated
     * applicant; staff may then read it (they need it to allocate a bed) only when {@code allowUnassigned}.
     */
    public static void requireHostellerRecord(Long profileId, Long profileHostelId, boolean allowUnassigned) {
        UserPrincipal p = principal();
        if (p.isSuperAdmin()) return;
        if (p.isHosteller()) {
            if (profileId != null && profileId.equals(p.getHostellerProfileId())) return;
            throw denied();
        }
        if (p.isStaff()) {
            if (profileHostelId == null && allowUnassigned) return;
            requireStaffHostel(profileHostelId);
            return;
        }
        throw denied();
    }

    /** True when the current user is staff with one of the given sub-roles (super admin always true). */
    public static boolean hasSubRole(String... subRoles) {
        UserPrincipal p = principal();
        if (p.isSuperAdmin()) return true;
        return p.isStaff() && p.getSubRole() != null && Arrays.asList(subRoles).contains(p.getSubRole());
    }

    public static void requireSubRole(String... subRoles) {
        if (!hasSubRole(subRoles)) {
            throw new AppException("Your role (" + principal().getSubRole() + ") cannot perform this action",
                    HttpStatus.FORBIDDEN);
        }
    }

    /** SUPERVISOR accounts have no access to finance (payments, expenses, payroll) or staff management. */
    public static void denySupervisor() {
        UserPrincipal p = principal();
        if (p.isStaff() && "SUPERVISOR".equals(p.getSubRole())) {
            throw new AppException("Supervisors cannot access finance or staff management", HttpStatus.FORBIDDEN);
        }
    }

    /** First hostel of a staff user, used when a request omits hostelId. */
    public static Long defaultStaffHostel() {
        List<Long> ids = principal().getHostelIds();
        return ids == null || ids.isEmpty() ? null : ids.get(0);
    }

    public static boolean sameHostel(Long a, Long b) {
        return a != null && Objects.equals(a, b);
    }

    private static AppException denied() {
        return new AppException("You don't have access to this hostel's data", HttpStatus.FORBIDDEN);
    }
}
