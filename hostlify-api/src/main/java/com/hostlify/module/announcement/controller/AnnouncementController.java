package com.hostlify.module.announcement.controller;

import com.hostlify.module.announcement.entity.Announcement;
import com.hostlify.module.announcement.service.AnnouncementService;
import com.hostlify.shared.dto.ApiResponse;
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
    public ApiResponse<List<Announcement>> getVisible(@PathVariable Long hostelId) {
        return ApiResponse.ok(announcementService.getVisibleByHostel(hostelId));
    }

    @GetMapping("/hostel/{hostelId}/all")
    public ApiResponse<List<Announcement>> getAll(@PathVariable Long hostelId) {
        return ApiResponse.ok(announcementService.getAllByHostel(hostelId));
    }

    @GetMapping("/{id}")
    public ApiResponse<Announcement> getById(@PathVariable Long id) {
        return ApiResponse.ok(announcementService.getById(id));
    }

    @PostMapping
    public ApiResponse<Announcement> create(@RequestBody Announcement announcement) {
        return ApiResponse.ok(announcementService.create(announcement));
    }

    @PutMapping("/{id}")
    public ApiResponse<Announcement> update(@PathVariable Long id, @RequestBody Announcement announcement) {
        return ApiResponse.ok(announcementService.update(id, announcement));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return ApiResponse.ok(null, "Announcement deleted");
    }
}
