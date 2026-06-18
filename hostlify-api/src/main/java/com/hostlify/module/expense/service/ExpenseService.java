package com.hostlify.module.expense.service;

import com.hostlify.module.expense.entity.ExpenseEmployee;
import com.hostlify.module.expense.entity.ExpenseRecord;
import com.hostlify.module.expense.entity.ExpenseSalaryRecord;
import com.hostlify.module.expense.repository.ExpenseEmployeeRepository;
import com.hostlify.module.expense.repository.ExpenseRecordRepository;
import com.hostlify.module.expense.repository.ExpenseSalaryRecordRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRecordRepository expenseRepo;
    private final ExpenseEmployeeRepository employeeRepo;
    private final ExpenseSalaryRecordRepository salaryRepo;

    public ExpenseService(ExpenseRecordRepository expenseRepo,
                          ExpenseEmployeeRepository employeeRepo,
                          ExpenseSalaryRecordRepository salaryRepo) {
        this.expenseRepo = expenseRepo;
        this.employeeRepo = employeeRepo;
        this.salaryRepo = salaryRepo;
    }

    // ─── Expense Records ───────────────────────────────────────────

    @Transactional
    public ExpenseRecord addExpense(ExpenseRecord expense) {
        // Auto-populate period from expenseDate if not set
        if (expense.getExpenseDate() != null) {
            if (expense.getPeriodMonth() == 0) {
                expense.setPeriodMonth(expense.getExpenseDate().getMonthValue());
            }
            if (expense.getPeriodYear() == 0) {
                expense.setPeriodYear(expense.getExpenseDate().getYear());
            }
        }
        return expenseRepo.save(expense);
    }

    public List<ExpenseRecord> getExpensesByHostel(Long hostelId, int month, int year) {
        return expenseRepo.findByHostelIdAndPeriodYearAndPeriodMonth(hostelId, year, month);
    }

    public List<ExpenseRecord> getExpensesByCategory(Long hostelId, String category) {
        return expenseRepo.findByHostelIdAndCategory(hostelId, category);
    }

    public Map<String, Object> getExpenseSummary(Long hostelId, int month, int year) {
        List<ExpenseRecord> records = expenseRepo.findByHostelIdAndPeriodYearAndPeriodMonth(hostelId, year, month);
        BigDecimal total = records.stream()
                .map(ExpenseRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> byCategory = new HashMap<>();
        for (ExpenseRecord r : records) {
            byCategory.merge(r.getCategory(), r.getAmount(), BigDecimal::add);
        }

        List<Map<String, Object>> byCategoryList = byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", e.getKey());
                    m.put("amount", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        // Include payroll for the period as an expense line (recorded salaries, net)
        BigDecimal payroll = salaryRepo.findByHostelIdAndPeriodYearAndPeriodMonth(hostelId, year, month)
                .stream().map(ExpenseSalaryRecord::getNetSalary)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (payroll.compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("category", "Salary/Payroll");
            pm.put("amount", payroll);
            byCategoryList.add(pm);
            byCategoryList.sort((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")));
            total = total.add(payroll);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("total", total);
        summary.put("byCategory", byCategoryList);
        summary.put("count", records.size());
        summary.put("payroll", payroll);
        summary.put("month", month);
        summary.put("year", year);
        return summary;
    }

    /** Per-employee payroll status for a period: payable (base+hra), paid amount, and status. */
    public Map<String, Object> getPayrollSummary(Long hostelId, int month, int year) {
        List<ExpenseEmployee> employees = employeeRepo.findByHostelId(hostelId);
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalPayable = BigDecimal.ZERO, totalPaid = BigDecimal.ZERO, totalPending = BigDecimal.ZERO;
        for (ExpenseEmployee e : employees) {
            BigDecimal payable = e.getBaseSalary().add(e.getHra() != null ? e.getHra() : BigDecimal.ZERO);
            var recOpt = salaryRepo.findByEmployeeIdAndPeriodYearAndPeriodMonth(e.getId(), year, month);
            String status = recOpt.map(ExpenseSalaryRecord::getStatus).orElse("NOT_RECORDED");
            BigDecimal net = recOpt.map(ExpenseSalaryRecord::getNetSalary).orElse(payable);
            boolean paid = "PAID".equals(status);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", e.getId());
            m.put("name", e.getName());
            m.put("role", e.getRole());
            m.put("payable", net);
            m.put("status", status);                 // PAID | PENDING | NOT_RECORDED
            m.put("salaryRecordId", recOpt.map(ExpenseSalaryRecord::getId).orElse(null));
            rows.add(m);
            totalPayable = totalPayable.add(net);
            if (paid) totalPaid = totalPaid.add(net); else totalPending = totalPending.add(net);
        }
        Map<String, Object> out = new HashMap<>();
        out.put("employees", rows);
        out.put("totalPayable", totalPayable);
        out.put("totalPaid", totalPaid);
        out.put("totalPending", totalPending);
        out.put("month", month);
        out.put("year", year);
        return out;
    }

    // ─── Employees ────────────────────────────────────────────────

    @Transactional
    public ExpenseEmployee addEmployee(ExpenseEmployee employee) {
        return employeeRepo.save(employee);
    }

    public List<ExpenseEmployee> getEmployees(Long hostelId) {
        List<ExpenseEmployee> list = employeeRepo.findByHostelId(hostelId);
        list.forEach(emp -> {
            salaryRepo.findFirstByEmployeeIdAndStatusOrderByPeriodYearDescPeriodMonthDesc(emp.getId(), "PAID")
                .ifPresent(sr -> {
                    if (sr.getPaidAt() != null) {
                        emp.setLastPaidDate(sr.getPaidAt().toLocalDate());
                    } else {
                        emp.setLastPaidDate(java.time.LocalDate.of(sr.getPeriodYear(), sr.getPeriodMonth(), 1));
                    }
                });
        });
        return list;
    }

    public ExpenseEmployee getEmployee(Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> new AppException("Employee not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ExpenseEmployee updateEmployee(Long id, ExpenseEmployee req) {
        ExpenseEmployee emp = getEmployee(id);
        emp.setName(req.getName());
        emp.setRole(req.getRole());
        emp.setMobile(req.getMobile());
        emp.setBaseSalary(req.getBaseSalary());
        return employeeRepo.save(emp);
    }

    @Transactional
    public void deactivateEmployee(Long id) {
        // Mark as deleted (soft delete via BaseEntity)
        ExpenseEmployee emp = getEmployee(id);
        emp.setDeleted(true);
        employeeRepo.save(emp);
    }

    // ─── Salary Records ───────────────────────────────────────────

    @Transactional
    public ExpenseSalaryRecord recordSalary(Long employeeId, int month, int year,
                                            BigDecimal grossSalary, BigDecimal deductions) {
        if (salaryRepo.findByEmployeeIdAndPeriodYearAndPeriodMonth(employeeId, year, month).isPresent()) {
            throw new AppException("Salary already recorded for this employee for " + month + "/" + year,
                    HttpStatus.CONFLICT);
        }
        ExpenseEmployee emp = getEmployee(employeeId);
        BigDecimal net = grossSalary.subtract(deductions != null ? deductions : BigDecimal.ZERO);

        ExpenseSalaryRecord record = new ExpenseSalaryRecord();
        record.setEmployeeId(employeeId);
        record.setHostelId(emp.getHostelId());
        record.setPeriodMonth(month);
        record.setPeriodYear(year);
        record.setGrossSalary(grossSalary);
        record.setDeductions(deductions != null ? deductions : BigDecimal.ZERO);
        record.setNetSalary(net);
        record.setStatus("PAID");
        return salaryRepo.save(record);
    }

    public List<ExpenseSalaryRecord> getMonthlySalariesForHostel(Long hostelId, int month, int year) {
        return salaryRepo.findByHostelIdAndPeriodYearAndPeriodMonth(hostelId, year, month);
    }

}
