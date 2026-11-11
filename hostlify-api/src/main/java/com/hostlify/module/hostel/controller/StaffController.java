package com.hostlify.module.hostel.controller;

import com.hostlify.module.auth.entity.AuthUser;
import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.hostel.entity.StaffAssignment;
import com.hostlify.module.hostel.repository.StaffAssignmentRepository;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.module.hostel.repository.HostelRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/staff")
@PreAuthorize("hasRole('HOSTEL_STAFF')")
@Tag(name = "Staff Management")
public class StaffController {

    private final AuthUserRepository userRepo;
    private final StaffAssignmentRepository assignmentRepo;
    private final PasswordEncoder passwordEncoder;
    private final HostelRepository hostelRepo;

    /** Sub-roles that can be given to a staff account. OWNER is never assignable through this API. */
    private static final List<String> ASSIGNABLE_SUB_ROLES =
            List.of("ADMIN", "MANAGER", "SUPERVISOR", "RECEPTIONIST", "SECURITY", "CLEANER", "OTHER");

    public StaffController(AuthUserRepository userRepo,
                           StaffAssignmentRepository assignmentRepo,
                           PasswordEncoder passwordEncoder,
                           HostelRepository hostelRepo) {
        this.userRepo = userRepo;
        this.assignmentRepo = assignmentRepo;
        this.passwordEncoder = passwordEncoder;
        this.hostelRepo = hostelRepo;
    }

    /**
     * Staff-account management: OWNER and ADMIN only (MANAGER/SUPERVISOR could previously create an
     * OWNER account or deactivate the owner). Only an OWNER may grant or change ADMIN access, and
     * nobody can edit an OWNER account or their own account here.
     */
    private void checkCanManage(String targetCurrentSubRole, String requestedSubRole, Long targetUserId) {
        HostelAccess.requireSubRole("OWNER", "ADMIN");
        UserPrincipal p = getPrincipal();
        if (requestedSubRole != null && !ASSIGNABLE_SUB_ROLES.contains(requestedSubRole)) {
            throw new AppException("Invalid sub-role: " + requestedSubRole, HttpStatus.BAD_REQUEST);
        }
        if ("OWNER".equals(targetCurrentSubRole)) {
            throw new AppException("The owner account cannot be modified here", HttpStatus.FORBIDDEN);
        }
        if (p != null && targetUserId != null && targetUserId.equals(p.getUserId())) {
            throw new AppException("You cannot modify your own staff account", HttpStatus.FORBIDDEN);
        }
        boolean touchesAdmin = "ADMIN".equals(targetCurrentSubRole) || "ADMIN".equals(requestedSubRole);
        if (touchesAdmin && !HostelAccess.hasSubRole("OWNER")) {
            throw new AppException("Only the owner can grant or change ADMIN access", HttpStatus.FORBIDDEN);
        }
    }

    @GetMapping("/hostel/{hostelId}")
    @Operation(summary = "Staff: list all staff members for a hostel")
    public ApiResponse<List<Map<String, Object>>> getStaff(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.denySupervisor();
        List<StaffAssignment> assignments = assignmentRepo.findByHostelIdAndIsActiveTrue(hostelId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (StaffAssignment sa : assignments) {
            userRepo.findById(sa.getStaffUserId()).ifPresent(user -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", sa.getId());
                m.put("userId", user.getId());
                m.put("email", user.getEmail());
                m.put("mobile", user.getMobile());
                m.put("subRole", user.getSubRole());
                m.put("status", user.getStatus());
                m.put("assignedAt", sa.getAssignedAt());
                result.add(m);
            });
        }
        return ApiResponse.ok(result);
    }

    @PostMapping
    @Operation(summary = "Staff: create a new staff member account")
    public ApiResponse<Map<String, Object>> createStaff(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        String email = (String) body.get("email");
        String password = (String) body.getOrDefault("password", "Hostlify@123");
        String mobile = (String) body.get("mobile");
        String subRole = (String) body.getOrDefault("subRole", "MANAGER");
        Long hostelId = p != null && !p.getHostelIds().isEmpty() ? p.getHostelIds().get(0) : null;
        if (body.get("hostelId") != null) hostelId = ((Number) body.get("hostelId")).longValue();

        if (email == null || email.isBlank()) throw new AppException("email is required", HttpStatus.BAD_REQUEST);
        email = email.trim().toLowerCase();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw new AppException("Invalid email address", HttpStatus.BAD_REQUEST);
        if (hostelId == null) throw new AppException("hostelId could not be determined", HttpStatus.BAD_REQUEST);
        HostelAccess.requireStaffHostel(hostelId);
        checkCanManage(null, subRole, null);
        if (password == null || password.length() < 8) throw new AppException("Password must be at least 8 characters", HttpStatus.BAD_REQUEST);
        if (mobile != null && !mobile.isBlank() && !mobile.trim().matches("^[6-9]\\d{9}$")) throw new AppException("Invalid mobile number", HttpStatus.BAD_REQUEST);
        if (userRepo.findByEmail(email).isPresent()) throw new AppException("Email already in use", HttpStatus.CONFLICT);
        // owner_id references hostel_owners(id): take it from the hostel, not the caller's auth-user id
        Long ownerId = hostelRepo.findById(hostelId).map(h -> h.getOwnerId())
                .orElseThrow(() -> new AppException("Hostel not found", HttpStatus.NOT_FOUND));

        AuthUser user = new AuthUser();
        user.setUuid(UUID.randomUUID().toString());
        user.setEmail(email);
        user.setMobile(mobile);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole("HOSTEL_STAFF");
        user.setSubRole(subRole);
        user.setStatus("ACTIVE");
        user = userRepo.save(user);

        StaffAssignment assignment = new StaffAssignment();
        assignment.setStaffUserId(user.getId());
        assignment.setHostelId(hostelId);
        assignment.setOwnerId(ownerId);
        assignment.setActive(true);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setAssignedBy(p != null ? p.getUserId() : user.getId());
        assignmentRepo.save(assignment);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", assignment.getId());
        result.put("userId", user.getId());
        result.put("email", user.getEmail());
        result.put("mobile", user.getMobile());
        result.put("subRole", user.getSubRole());
        result.put("status", user.getStatus());
        result.put("assignedAt", assignment.getAssignedAt());
        result.put("temporaryPassword", password);
        return ApiResponse.ok(result, "Staff member created successfully");
    }

    @PutMapping("/{assignmentId}")
    @Operation(summary = "Staff: update staff member sub-role or mobile")
    public ApiResponse<Map<String, Object>> updateStaff(@PathVariable Long assignmentId,
                                                         @RequestBody Map<String, Object> body) {
        StaffAssignment sa = assignmentRepo.findById(assignmentId)
                .orElseThrow(() -> new AppException("Staff assignment not found", HttpStatus.NOT_FOUND));
        AuthUser user = userRepo.findById(sa.getStaffUserId())
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        HostelAccess.requireStaffHostel(sa.getHostelId());
        checkCanManage(user.getSubRole(), (String) body.get("subRole"), user.getId());
        String newMobile = (String) body.get("mobile");
        if (newMobile != null && !newMobile.isBlank() && !newMobile.trim().matches("^[6-9]\\d{9}$")) {
            throw new AppException("Invalid mobile number", HttpStatus.BAD_REQUEST);
        }
        String newStatus = (String) body.get("status");
        if (newStatus != null && !List.of("ACTIVE", "INACTIVE").contains(newStatus)) {
            throw new AppException("Invalid status", HttpStatus.BAD_REQUEST);
        }

        if (newMobile != null) user.setMobile(newMobile.trim());
        if (body.get("subRole") != null) user.setSubRole((String) body.get("subRole"));
        if (newStatus != null) user.setStatus(newStatus);
        userRepo.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", sa.getId());
        result.put("userId", user.getId());
        result.put("email", user.getEmail());
        result.put("mobile", user.getMobile());
        result.put("subRole", user.getSubRole());
        result.put("status", user.getStatus());
        return ApiResponse.ok(result, "Staff member updated");
    }

    @DeleteMapping("/{assignmentId}")
    @Operation(summary = "Staff: deactivate a staff member")
    public ApiResponse<Void> deactivateStaff(@PathVariable Long assignmentId) {
        StaffAssignment sa = assignmentRepo.findById(assignmentId)
                .orElseThrow(() -> new AppException("Staff assignment not found", HttpStatus.NOT_FOUND));
        HostelAccess.requireStaffHostel(sa.getHostelId());
        AuthUser target = userRepo.findById(sa.getStaffUserId())
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        checkCanManage(target.getSubRole(), null, target.getId());
        sa.setActive(false);
        assignmentRepo.save(sa);

        // Also deactivate the user account
        userRepo.findById(sa.getStaffUserId()).ifPresent(user -> {
            user.setStatus("INACTIVE");
            userRepo.save(user);
        });
        return ApiResponse.ok(null, "Staff member deactivated");
    }

    /** Staff: change own password (My Account page). */
    @PutMapping("/me/password")
    @Operation(summary = "Staff: change own password")
    public ApiResponse<Void> changeOwnPassword(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();
        if (p == null) throw new AppException("Not authenticated", HttpStatus.UNAUTHORIZED);
        String current = (String) body.get("currentPassword");
        String next = (String) body.get("newPassword");
        if (current == null || current.isBlank()) throw new AppException("Current password is required", HttpStatus.BAD_REQUEST);
        if (next == null || next.length() < 8) throw new AppException("New password must be at least 8 characters", HttpStatus.BAD_REQUEST);
        AuthUser user = userRepo.findById(p.getUserId())
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new AppException("Current password is incorrect", HttpStatus.BAD_REQUEST);
        }
        if (passwordEncoder.matches(next, user.getPasswordHash())) {
            throw new AppException("New password must be different from the current one", HttpStatus.BAD_REQUEST);
        }
        user.setPasswordHash(passwordEncoder.encode(next));
        userRepo.save(user);
        return ApiResponse.ok(null, "Password changed");
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) return null;
        return (UserPrincipal) auth.getPrincipal();
    }
}
