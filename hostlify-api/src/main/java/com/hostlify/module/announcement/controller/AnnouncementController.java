package com.hostlify.module.announcement.controller;

import com.hostlify.module.announcement.entity.Announcement;
import com.hostlify.module.announcement.service.AnnouncementService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping("/hostel/{hostelId}")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ApiResponse<List<Announcement>> getVisible(@PathVariable Long hostelId) {
        com.hostlify.shared.security.HostelAccess.requireHostelMember(hostelId);
        return ApiResponse.ok(announcementService.getVisibleByHostel(hostelId));
    }

    @GetMapping("/hostel/{hostelId}/all")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<List<Announcement>> getAll(@PathVariable Long hostelId) {
        com.hostlify.shared.security.HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(announcementService.getAllByHostel(hostelId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('HOSTELLER','HOSTEL_STAFF')")
    public ApiResponse<Announcement> getById(@PathVariable Long id) {
        Announcement a = announcementService.getById(id);
        com.hostlify.shared.security.HostelAccess.requireHostelMember(a.getHostelId());
        return ApiResponse.ok(a);
    }

    @PostMapping
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Announcement> create(@RequestBody Announcement announcement) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            if (announcement.getCreatedBy() == null) {
                announcement.setCreatedBy(principal.getUserId());
            }
            if (announcement.getHostelId() == null
                    && principal.getHostelIds() != null && !principal.getHostelIds().isEmpty()) {
                announcement.setHostelId(principal.getHostelIds().get(0));
            }
        }
        com.hostlify.shared.security.HostelAccess.requireStaffHostel(announcement.getHostelId());
        announcement.setId(null);
        if (announcement.getTitle() == null || announcement.getTitle().isBlank()) {
            throw new com.hostlify.shared.exception.AppException("Title is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        if (announcement.getValidTill() != null && announcement.getValidFrom() != null
                && announcement.getValidTill().isBefore(announcement.getValidFrom())) {
            throw new com.hostlify.shared.exception.AppException("Valid till must be after valid from", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        return ApiResponse.ok(announcementService.create(announcement));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Announcement> update(@PathVariable Long id, @RequestBody Announcement announcement) {
        com.hostlify.shared.security.HostelAccess.requireStaffHostel(announcementService.getById(id).getHostelId());
        return ApiResponse.ok(announcementService.update(id, announcement));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        com.hostlify.shared.security.HostelAccess.requireStaffHostel(announcementService.getById(id).getHostelId());
        announcementService.delete(id);
        return ApiResponse.ok(null, "Announcement deleted");
    }
}
