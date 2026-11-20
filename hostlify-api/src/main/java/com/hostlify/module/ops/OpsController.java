package com.hostlify.module.ops;

import com.hostlify.module.expense.entity.ExpenseEmployee;
import com.hostlify.module.expense.entity.ExpenseSalaryRecord;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.security.HostelAccess;
import com.hostlify.shared.util.DateUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/** Staff attendance, salary slips, meter billing and owner insights. */
@RestController
@RequestMapping("/api/v1/ops")
@Tag(name = "Owner operations")
@PreAuthorize("hasRole('HOSTEL_STAFF')")
public class OpsController {

    private static final String[] MANAGERS = {"OWNER", "ADMIN", "MANAGER"};

    private final AttendanceService attendance;
    private final MeterService meters;
    private final InsightsService insights;
    private final SalarySlipPdfService slips;

    public OpsController(AttendanceService attendance, MeterService meters, InsightsService insights, SalarySlipPdfService slips) {
        this.attendance = attendance;
        this.meters = meters;
        this.insights = insights;
        this.slips = slips;
    }

    // ═══ Attendance (managers mark; supervisors can't — it drives payroll) ═══

    @GetMapping("/attendance/hostel/{hostelId}")
    @Operation(summary = "Employees with their attendance mark for a date (default today)")
    public ApiResponse<List<Map<String, Object>>> day(@PathVariable Long hostelId, @RequestParam(required = false) String date) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.denySupervisor();
        return ApiResponse.ok(attendance.day(hostelId, date == null ? DateUtils.todayKolkata() : date(date)));
    }

    @PutMapping("/attendance/hostel/{hostelId}")
    @Operation(summary = "Mark attendance: {date, entries:[{employeeId, status, inTime?, outTime?, note?}]}")
    public ApiResponse<Map<String, Object>> mark(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole(MANAGERS);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) body.get("entries");
        if (entries == null || entries.isEmpty()) throw new AppException("Nothing to save", HttpStatus.BAD_REQUEST);
        int n = attendance.mark(hostelId, date(body.get("date")), entries, HostelAccess.principal().getUserId());
        return ApiResponse.ok(Map.of("saved", n), "Attendance saved");
    }

    @GetMapping("/attendance/hostel/{hostelId}/month")
    @Operation(summary = "Month summary per employee with paid days and suggested salary deduction")
    public ApiResponse<Map<String, Object>> month(@PathVariable Long hostelId, @RequestParam int year, @RequestParam int month) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.denySupervisor();
        period(year, month);
        return ApiResponse.ok(attendance.month(hostelId, year, month));
    }

    @GetMapping("/salary-slip/{salaryRecordId}")
    @Operation(summary = "Salary slip PDF for a recorded salary")
    public ResponseEntity<byte[]> slip(@PathVariable Long salaryRecordId) {
        ExpenseSalaryRecord rec = attendance.salaryRecord(salaryRecordId);
        HostelAccess.requireStaffHostel(rec.getHostelId());
        HostelAccess.denySupervisor();
        ExpenseEmployee e = attendance.employee(rec.getEmployeeId());
        byte[] pdf = slips.slip(rec, e, attendance.counts(e.getId(), YearMonth.of(rec.getPeriodYear(), rec.getPeriodMonth())));
        String file = "salary-slip-" + e.getName().replaceAll("[^A-Za-z0-9]+", "-") + "-" + rec.getPeriodYear() + "-" + rec.getPeriodMonth() + ".pdf";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"").body(pdf);
    }

    // ═══ Meters ═════════════════════════════════════════════════════════════

    @GetMapping("/meters/hostel/{hostelId}")
    @Operation(summary = "Rooms with this month's meter reading and last month's closing reading")
    public ApiResponse<Map<String, Object>> meterMonth(@PathVariable Long hostelId, @RequestParam String type,
                                                       @RequestParam int year, @RequestParam int month) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.denySupervisor();
        period(year, month);
        return ApiResponse.ok(meters.month(hostelId, type, year, month));
    }

    @PutMapping("/meters/hostel/{hostelId}")
    @Operation(summary = "Save a room's reading {roomId, meterType, year, month, previousReading, currentReading, ratePerUnit, fixedCharge?}")
    public ApiResponse<MeterReading> saveReading(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.denySupervisor();
        return ApiResponse.ok(meters.save(hostelId, body, HostelAccess.principal().getUserId()), "Reading saved");
    }

    @DeleteMapping("/meters/{id}")
    public ApiResponse<Void> deleteReading(@PathVariable Long id) {
        HostelAccess.requireStaffHostel(meters.reading(id).getHostelId());
        HostelAccess.denySupervisor();
        meters.delete(id);
        return ApiResponse.ok(null, "Reading deleted");
    }

    @PostMapping("/meters/hostel/{hostelId}/bill")
    @Operation(summary = "Split the month's unbilled readings across each room's residents as charges")
    public ApiResponse<Map<String, Object>> bill(@PathVariable Long hostelId, @RequestBody Map<String, Object> body) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.requireSubRole(MANAGERS);
        int year = ((Number) body.get("year")).intValue(), month = ((Number) body.get("month")).intValue();
        period(year, month);
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("readingIds");
        return ApiResponse.ok(meters.bill(hostelId, String.valueOf(body.get("type")), year, month,
                ids == null ? null : ids.stream().map(Number::longValue).toList()), "Charges added to residents' dues");
    }

    // ═══ Insights ══════════════════════════════════════════════════════════

    @GetMapping("/insights/hostel/{hostelId}")
    @Operation(summary = "Collection rate, overdue ageing and occupancy trend (last 6 months)")
    public ApiResponse<Map<String, Object>> insights(@PathVariable Long hostelId) {
        HostelAccess.requireStaffHostel(hostelId);
        HostelAccess.denySupervisor();
        return ApiResponse.ok(insights.forHostel(hostelId));
    }

    private static LocalDate date(Object v) {
        try {
            return LocalDate.parse(String.valueOf(v));
        } catch (Exception e) {
            throw new AppException("Date must look like 2026-11-20", HttpStatus.BAD_REQUEST);
        }
    }

    private static void period(int year, int month) {
        if (month < 1 || month > 12 || year < 2020 || year > 2100) throw new AppException("Invalid month", HttpStatus.BAD_REQUEST);
    }
}
