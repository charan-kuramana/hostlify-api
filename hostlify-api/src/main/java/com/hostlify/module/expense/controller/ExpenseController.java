package com.hostlify.module.expense.controller;

import com.hostlify.module.expense.entity.ExpenseEmployee;
import com.hostlify.module.expense.entity.ExpenseRecord;
import com.hostlify.module.expense.entity.ExpenseSalaryRecord;
import com.hostlify.module.expense.service.ExpenseService;
import com.hostlify.shared.dto.ApiResponse;
import com.hostlify.shared.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@PreAuthorize("hasRole('HOSTEL_STAFF')")
@Tag(name = "Expense & Employee")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) return null;
        return (UserPrincipal) auth.getPrincipal();
    }

    // ─── Expenses ─────────────────────────────────────────────────

    @PostMapping("/api/v1/expense")
    @Operation(summary = "Staff: add an expense record")
    public ApiResponse<ExpenseRecord> addExpense(@RequestBody ExpenseRecord expense) {
        UserPrincipal p = getPrincipal();
        if (p != null) {
            if (expense.getHostelId() == null && p.getHostelIds() != null && !p.getHostelIds().isEmpty()) {
                expense.setHostelId(p.getHostelIds().get(0));
            }
            if (expense.getRecordedBy() == null) {
                expense.setRecordedBy(p.getUserId());
            }
        }
        return ApiResponse.ok(expenseService.addExpense(expense));
    }

    @GetMapping("/api/v1/expense/hostel/{hostelId}")
    @Operation(summary = "Staff: list expenses for a hostel by month/year")
    public ApiResponse<List<ExpenseRecord>> getExpenses(@PathVariable Long hostelId,
                                                         @RequestParam int month,
                                                         @RequestParam int year) {
        return ApiResponse.ok(expenseService.getExpensesByHostel(hostelId, month, year));
    }

    @GetMapping("/api/v1/expense/hostel/{hostelId}/summary")
    @Operation(summary = "Staff: expense summary (total + by category) for a month")
    public ApiResponse<Map<String, Object>> getSummary(@PathVariable Long hostelId,
                                                         @RequestParam int month,
                                                         @RequestParam int year) {
        return ApiResponse.ok(expenseService.getExpenseSummary(hostelId, month, year));
    }

    // ─── Employees ────────────────────────────────────────────────

    @PostMapping("/api/v1/employee")
    @Operation(summary = "Staff: add a new employee")
    public ApiResponse<ExpenseEmployee> addEmployee(@RequestBody Map<String, Object> body) {
        UserPrincipal p = getPrincipal();

        ExpenseEmployee employee = new ExpenseEmployee();

        // hostelId: from body or JWT
        if (body.get("hostelId") != null) {
            employee.setHostelId(((Number) body.get("hostelId")).longValue());
        } else if (p != null && p.getHostelIds() != null && !p.getHostelIds().isEmpty()) {
            employee.setHostelId(p.getHostelIds().get(0));
        }

        // name: accept "employeeName" or "name"
        String name = body.containsKey("employeeName") ? (String) body.get("employeeName")
                : (String) body.get("name");
        employee.setName(name);

        // role: accept "designation" or "role"
        String role = body.containsKey("designation") ? (String) body.get("designation")
                : (String) body.get("role");
        employee.setRole(role);

        // baseSalary: accept "salary" or "baseSalary"
        Object salaryRaw = body.containsKey("salary") ? body.get("salary") : body.get("baseSalary");
        if (salaryRaw != null) {
            employee.setBaseSalary(new BigDecimal(salaryRaw.toString()));
        } else {
            employee.setBaseSalary(BigDecimal.ZERO);
        }

        // Optional fields
        if (body.get("mobile") != null) employee.setMobile((String) body.get("mobile"));
        if (body.get("employeeCode") != null) employee.setEmployeeCode((String) body.get("employeeCode"));

        if (employee.getHostelId() == null)
            throw new com.hostlify.shared.exception.AppException("hostelId is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        if (employee.getName() == null || employee.getName().isBlank())
            throw new com.hostlify.shared.exception.AppException("employeeName is required", org.springframework.http.HttpStatus.BAD_REQUEST);
        if (employee.getRole() == null || employee.getRole().isBlank())
            throw new com.hostlify.shared.exception.AppException("designation is required", org.springframework.http.HttpStatus.BAD_REQUEST);

        return ApiResponse.ok(expenseService.addEmployee(employee));
    }

    @GetMapping("/api/v1/employee/hostel/{hostelId}")
    @Operation(summary = "Staff: list all employees for a hostel")
    public ApiResponse<List<ExpenseEmployee>> getEmployees(@PathVariable Long hostelId) {
        return ApiResponse.ok(expenseService.getEmployees(hostelId));
    }

    @PutMapping("/api/v1/employee/{id}")
    @Operation(summary = "Staff: update employee details")
    public ApiResponse<ExpenseEmployee> updateEmployee(@PathVariable Long id,
                                                        @RequestBody ExpenseEmployee req) {
        return ApiResponse.ok(expenseService.updateEmployee(id, req));
    }

    @DeleteMapping("/api/v1/employee/{id}")
    @Operation(summary = "Staff: deactivate an employee (soft delete)")
    public ApiResponse<Void> deactivateEmployee(@PathVariable Long id) {
        expenseService.deactivateEmployee(id);
        return ApiResponse.ok(null, "Employee deactivated");
    }

    // ─── Salary ───────────────────────────────────────────────────

    @PostMapping("/api/v1/employee/{employeeId}/salary")
    @Operation(summary = "Staff: record monthly salary for an employee")
    public ApiResponse<ExpenseSalaryRecord> recordSalary(@PathVariable Long employeeId,
                                                          @RequestBody Map<String, Object> body) {
        int month = ((Number) body.get("month")).intValue();
        int year = ((Number) body.get("year")).intValue();
        // Accept "amount" (frontend) or "grossSalary" (direct API) as the gross salary
        Object rawGross = body.getOrDefault("grossSalary", body.get("amount"));
        BigDecimal gross = rawGross != null ? new BigDecimal(rawGross.toString()) : BigDecimal.ZERO;
        BigDecimal deductions = body.get("deductions") != null ?
                new BigDecimal(body.get("deductions").toString()) : BigDecimal.ZERO;
        return ApiResponse.ok(expenseService.recordSalary(employeeId, month, year, gross, deductions));
    }

    @GetMapping("/api/v1/employee/salary/hostel/{hostelId}")
    @Operation(summary = "Staff: list salary records for all employees in a hostel for a month")
    public ApiResponse<List<ExpenseSalaryRecord>> getMonthlySalaries(@PathVariable Long hostelId,
                                                                       @RequestParam int month,
                                                                       @RequestParam int year) {
        return ApiResponse.ok(expenseService.getMonthlySalariesForHostel(hostelId, month, year));
    }

    @GetMapping("/api/v1/employee/payroll/hostel/{hostelId}")
    @Operation(summary = "Staff: payroll summary (payable/paid/pending per employee) for a month")
    public ApiResponse<Map<String, Object>> getPayroll(@PathVariable Long hostelId,
                                                        @RequestParam int month,
                                                        @RequestParam int year) {
        return ApiResponse.ok(expenseService.getPayrollSummary(hostelId, month, year));
    }
}
