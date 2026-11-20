package com.hostlify.module.resident.gate;

import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.security.UserPrincipal;
import com.hostlify.shared.util.DateUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/gate")
@Tag(name = "Visitors & parcels")
public class GateController {

    private final GateService gate;

    public GateController(GateService gate) {
        this.gate = gate;
    }

    // ═══ Resident ══════════════════════════════════════════════════════════

    @GetMapping("/visitors/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<Map<String, Object>> myVisitors() {
        return ApiResponse.ok(gate.mine(me().getHostellerProfileId()));
    }

    @PostMapping("/visitors/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    @Operation(summary = "Resident: pre-approve a visitor; returns a 6-digit pass code to share with them")
    public ApiResponse<VisitorPass> addVisitor(@RequestBody Map<String, Object> body) {
        UserPrincipal p = me();
        if (p.getHostelId() == null) throw new AppException("You need an active stay to add visitors", HttpStatus.BAD_REQUEST);
        return ApiResponse.ok(gate.createPass(p.getHostelId(), p.getHostellerProfileId(), body, p.getUserId(), "HOSTELLER", false),
                "Visitor pass created");
    }

    @PostMapping("/visitors/my/{id}/cancel")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<VisitorPass> cancelMine(@PathVariable Long id) {
        VisitorPass v = gate.pass(id);
        if (!Objects.equals(v.getHostellerId(), me().getHostellerProfileId())) throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        return ApiResponse.ok(gate.cancel(id), "Pass cancelled");
    }

    @GetMapping("/parcels/my")
    @PreAuthorize("hasRole('HOSTELLER')")
    public ApiResponse<List<Parcel>> myParcels() {
        return ApiResponse.ok(gate.myParcels(me().getHostellerProfileId()));
    }

    // ═══ Staff (supervisors included: gate and desk duty) ══════════════════

    @GetMapping("/visitors/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: visitor passes for a date (default today) plus everyone still inside")
    public ApiResponse<List<VisitorPass>> visitors(@PathVariable Long hostelId, @RequestParam(required = false) String date) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(gate.forDate(hostelId, date == null ? DateUtils.todayKolkata() : LocalDate.parse(date)));
    }

    @GetMapping("/visitors/hostel/{hostelId}/code/{code}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: look up an expected visitor by pass code")
    public ApiResponse<List<VisitorPass>> byCode(@PathVariable Long hostelId, @PathVariable String code) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(gate.findByCode(hostelId, code));
    }

    @PostMapping("/visitors/hostel/{hostelId}/walk-in")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: log a walk-in visitor (marked inside now)")
    public ApiResponse<VisitorPass> walkIn(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        Long hostellerId = body.get("hostellerId") instanceof Number n ? n.longValue() : null;
        return ApiResponse.ok(gate.createPass(hostelId, hostellerId, body, me().getUserId(), "STAFF", true), "Visitor checked in");
    }

    @PostMapping("/visitors/{id}/check-in")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<VisitorPass> checkIn(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(gate.pass(id).getHostelId());
        return ApiResponse.ok(gate.checkIn(id, me().getUserId()), "Visitor checked in");
    }

    @PostMapping("/visitors/{id}/check-out")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<VisitorPass> checkOut(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(gate.pass(id).getHostelId());
        return ApiResponse.ok(gate.checkOut(id, me().getUserId()), "Visitor checked out");
    }

    @PostMapping("/visitors/{id}/cancel")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<VisitorPass> cancel(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(gate.pass(id).getHostelId());
        return ApiResponse.ok(gate.cancel(id), "Pass cancelled");
    }

    @GetMapping("/parcels/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Map<String, Object>> parcels(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(gate.hostelParcels(hostelId));
    }

    @PostMapping("/parcels/hostel/{hostelId}")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    @Operation(summary = "Staff: log a parcel received at the desk; the resident is notified")
    public ApiResponse<Parcel> logParcel(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        return ApiResponse.ok(gate.logParcel(hostelId, body, me().getUserId()), "Parcel logged; resident notified");
    }

    @PostMapping("/parcels/{id}/collect")
    @PreAuthorize("hasRole('HOSTEL_STAFF')")
    public ApiResponse<Parcel> collect(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        HostelAccess.requireStaffHostel(gate.parcel(id).getHostelId());
        Object by = body == null ? null : body.get("collectedBy");
        return ApiResponse.ok(gate.collect(id, by == null ? null : String.valueOf(by), me().getUserId()), "Marked collected");
    }

    private static UserPrincipal me() {
        return HostelAccess.principal();
    }
}
