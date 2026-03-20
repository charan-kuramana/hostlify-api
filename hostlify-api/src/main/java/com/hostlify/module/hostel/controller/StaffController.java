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

    public StaffController(AuthUserRepository userRepo,
                           StaffAssignmentRepository assignmentRepo,
                           PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.assignmentRepo = assignmentRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/hostel/{hostelId}")
    @Operation(summary = "Staff: list all staff members for a hostel")
    public ApiResponse<List<Map<String, Object>>> getStaff(@PathVariable Long hostelId) {
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
        if (hostelId == null) throw new AppException("hostelId could not be determined", HttpStatus.BAD_REQUEST);
        if (userRepo.findByEmail(email).isPresent()) throw new AppException("Email already in use", HttpStatus.CONFLICT);

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
        assignment.setOwnerId(p != null ? p.getUserId() : user.getId());
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

        if (body.get("mobile") != null) user.setMobile((String) body.get("mobile"));
        if (body.get("subRole") != null) user.setSubRole((String) body.get("subRole"));
        if (body.get("status") != null) user.setStatus((String) body.get("status"));
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
        sa.setActive(false);
        assignmentRepo.save(sa);

        // Also deactivate the user account
        userRepo.findById(sa.getStaffUserId()).ifPresent(user -> {
            user.setStatus("INACTIVE");
            userRepo.save(user);
        });
        return ApiResponse.ok(null, "Staff member deactivated");
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) return null;
        return (UserPrincipal) auth.getPrincipal();
    }
}
