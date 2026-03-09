package com.hostlify.module.leave.controller;

import com.hostlify.module.leave.entity.LeaveRequest;
import com.hostlify.module.leave.service.LeaveService;
import com.hostlify.shared.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @GetMapping("/hosteller/{hostellerId}")
    public ApiResponse<List<LeaveRequest>> getByHosteller(@PathVariable Long hostellerId) {
        return ApiResponse.ok(leaveService.getByHosteller(hostellerId));
    }

    @GetMapping("/pending")
    public ApiResponse<List<LeaveRequest>> getPending() {
        return ApiResponse.ok(leaveService.getPending());
    }

    @GetMapping("/{id}")
    public ApiResponse<LeaveRequest> getById(@PathVariable Long id) {
        return ApiResponse.ok(leaveService.getById(id));
    }

    @PostMapping
    public ApiResponse<LeaveRequest> apply(@RequestBody LeaveRequest leave) {
        return ApiResponse.ok(leaveService.apply(leave));
    }

    @PutMapping("/{id}/review")
    public ApiResponse<LeaveRequest> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = (String) body.get("status");
        Long reviewedBy = body.get("reviewedBy") != null ? Long.parseLong(body.get("reviewedBy").toString()) : null;
        return ApiResponse.ok(leaveService.review(id, status, reviewedBy));
    }
}
