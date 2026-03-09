package com.hostlify.module.hosteller.controller;

import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.service.HostellerService;
import com.hostlify.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/hostellers")
public class HostellerController {

    private final HostellerService hostellerService;

    public HostellerController(HostellerService hostellerService) {
        this.hostellerService = hostellerService;
    }

    @GetMapping("/hostel/{hostelId}")
    public ApiResponse<List<HostellerProfile>> getByHostel(@PathVariable Long hostelId) {
        return ApiResponse.ok(hostellerService.getByHostel(hostelId));
    }

    @GetMapping("/{id}")
    public ApiResponse<HostellerProfile> getById(@PathVariable Long id) {
        return ApiResponse.ok(hostellerService.getById(id));
    }

    @GetMapping("/auth/{authUserId}")
    public ApiResponse<HostellerProfile> getByAuthUser(@PathVariable Long authUserId) {
        return ApiResponse.ok(hostellerService.getByAuthUser(authUserId));
    }

    @PutMapping("/{id}")
    public ApiResponse<HostellerProfile> update(@PathVariable Long id, @RequestBody HostellerProfile profile) {
        return ApiResponse.ok(hostellerService.update(id, profile));
    }
}
