package com.hostlify.module.expense.controller;

import com.hostlify.module.expense.entity.ExpenseEmployee;
import com.hostlify.module.expense.entity.ExpenseRecord;
import com.hostlify.module.expense.entity.ExpenseSalaryRecord;
import com.hostlify.module.expense.service.ExpenseService;
import com.hostlify.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Expense & Employee")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    // ─── Expenses ─────────────────────────────────────────────────

    @PostMapping("/api/v1/expense")
    @Operation(summary = "Staff: add an expense record")
    public ApiResponse<ExpenseRecord> addExpense(@RequestBody ExpenseRecord expense) {
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
    public ApiResponse<ExpenseEmployee> addEmployee(@RequestBody ExpenseEmployee employee) {
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
        BigDecimal gross = new BigDecimal(body.get("grossSalary").toString());
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
}
