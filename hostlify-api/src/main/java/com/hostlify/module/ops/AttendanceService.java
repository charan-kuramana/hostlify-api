package com.hostlify.module.ops;

import com.hostlify.module.expense.entity.ExpenseEmployee;
import com.hostlify.module.expense.entity.ExpenseSalaryRecord;
import com.hostlify.module.expense.repository.ExpenseEmployeeRepository;
import com.hostlify.module.expense.repository.ExpenseSalaryRecordRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Daily staff attendance and the monthly summary used for salaries.
 * Paid days = present + leave + week off + ½ × half days; unmarked days count as paid (benefit of the doubt),
 * absent days are deducted at (salary ÷ days in month) per day.
 */
@Service
public class AttendanceService {

    private final OpsRepos.Attendance attendanceRepo;
    private final ExpenseEmployeeRepository employeeRepo;
    private final ExpenseSalaryRecordRepository salaryRepo;

    public AttendanceService(OpsRepos.Attendance attendanceRepo, ExpenseEmployeeRepository employeeRepo,
                             ExpenseSalaryRecordRepository salaryRepo) {
        this.attendanceRepo = attendanceRepo;
        this.employeeRepo = employeeRepo;
        this.salaryRepo = salaryRepo;
    }

    /** Every employee with their mark for the day (null when not yet marked). */
    public List<Map<String, Object>> day(Long hostelId, LocalDate date) {
        Map<Long, StaffAttendance> marks = new HashMap<>();
        attendanceRepo.findByHostelIdAndAttDate(hostelId, date).forEach(a -> marks.put(a.getEmployeeId(), a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ExpenseEmployee e : employees(hostelId)) {
            StaffAttendance a = marks.get(e.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", e.getId());
            m.put("name", e.getName());
            m.put("role", e.getRole());
            m.put("status", a == null ? null : a.getStatus());
            m.put("inTime", a == null || a.getInTime() == null ? null : a.getInTime().toString());
            m.put("outTime", a == null || a.getOutTime() == null ? null : a.getOutTime().toString());
            m.put("note", a == null ? null : a.getNote());
            out.add(m);
        }
        return out;
    }

    /** Mark one or more employees for a day: [{employeeId, status, inTime?, outTime?, note?}]. */
    @Transactional
    public int mark(Long hostelId, LocalDate date, List<Map<String, Object>> entries, Long markedBy) {
        LocalDate today = DateUtils.todayKolkata();
        if (date.isAfter(today)) throw bad("You can't mark attendance for a future date");
        if (date.isBefore(today.minusDays(45))) throw bad("Attendance older than 45 days can't be changed");
        int n = 0;
        for (Map<String, Object> en : entries) {
            if (!(en.get("employeeId") instanceof Number id)) throw bad("employeeId is required");
            ExpenseEmployee e = employeeRepo.findById(id.longValue()).orElseThrow(() -> bad("Employee not found"));
            if (!Objects.equals(e.getHostelId(), hostelId)) throw bad("This employee is not in this hostel");
            String status = en.get("status") == null ? null : String.valueOf(en.get("status")).toUpperCase();
            Optional<StaffAttendance> existing = attendanceRepo.findByEmployeeIdAndAttDate(e.getId(), date);
            if (status == null || status.isBlank()) { // clear the mark
                existing.ifPresent(attendanceRepo::delete);
                n++;
                continue;
            }
            if (!StaffAttendance.STATUSES.contains(status)) throw bad("Status must be one of " + StaffAttendance.STATUSES);
            StaffAttendance a = existing.orElseGet(StaffAttendance::new);
            a.setHostelId(hostelId);
            a.setEmployeeId(e.getId());
            a.setAttDate(date);
            a.setStatus(status);
            a.setInTime(time(en.get("inTime")));
            a.setOutTime(time(en.get("outTime")));
            if (a.getInTime() != null && a.getOutTime() != null && a.getOutTime().isBefore(a.getInTime())) {
                throw bad("Out time must be after in time for " + e.getName());
            }
            String note = en.get("note") == null ? null : String.valueOf(en.get("note")).trim();
            a.setNote(note == null || note.isEmpty() ? null : note.substring(0, Math.min(150, note.length())));
            a.setMarkedBy(markedBy);
            a.setUpdatedAt(LocalDateTime.now());
            attendanceRepo.save(a);
            n++;
        }
        return n;
    }

    /** Month view: per employee counts, paid days and the suggested salary deduction. */
    public Map<String, Object> month(Long hostelId, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        Map<Long, List<StaffAttendance>> byEmp = new HashMap<>();
        attendanceRepo.findByHostelIdAndAttDateBetween(hostelId, ym.atDay(1), ym.atEndOfMonth())
                .forEach(a -> byEmp.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>()).add(a));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ExpenseEmployee e : employees(hostelId)) {
            List<StaffAttendance> marks = byEmp.getOrDefault(e.getId(), List.of());
            Map<String, Object> s = summarize(e, ym, marks);
            Map<String, String> days = new TreeMap<>();
            marks.forEach(a -> days.put(a.getAttDate().toString(), a.getStatus()));
            s.put("days", days);
            rows.add(s);
        }
        return Map.of("year", year, "month", month, "daysInMonth", ym.lengthOfMonth(), "employees", rows);
    }

    public Map<String, Object> summarize(ExpenseEmployee e, YearMonth ym, List<StaffAttendance> marks) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        StaffAttendance.STATUSES.forEach(s -> counts.put(s, 0));
        marks.forEach(a -> counts.merge(a.getStatus(), 1, Integer::sum));
        int daysInMonth = ym.lengthOfMonth();
        int marked = marks.size();
        BigDecimal absent = BigDecimal.valueOf(counts.get("ABSENT")).add(BigDecimal.valueOf(counts.get("HALF_DAY")).multiply(new BigDecimal("0.5")));
        BigDecimal paidDays = BigDecimal.valueOf(daysInMonth).subtract(absent);
        BigDecimal monthly = e.getBaseSalary().add(e.getHra() == null ? BigDecimal.ZERO : e.getHra());
        BigDecimal perDay = monthly.divide(BigDecimal.valueOf(daysInMonth), 4, RoundingMode.HALF_UP);
        BigDecimal deduction = perDay.multiply(absent).setScale(0, RoundingMode.HALF_UP);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", e.getId());
        m.put("name", e.getName());
        m.put("role", e.getRole());
        m.put("counts", counts);
        m.put("unmarked", daysInMonth - marked);
        m.put("absentDays", absent);
        m.put("paidDays", paidDays);
        m.put("monthlySalary", monthly);
        m.put("suggestedDeduction", deduction);
        m.put("suggestedNet", monthly.subtract(deduction));
        return m;
    }

    /** After a salary is recorded: remember the attendance it was based on (for the slip). */
    @Transactional
    public ExpenseSalaryRecord stampSalary(ExpenseSalaryRecord rec, String note) {
        ExpenseEmployee e = employeeRepo.findById(rec.getEmployeeId()).orElse(null);
        if (e == null) return rec;
        YearMonth ym = YearMonth.of(rec.getPeriodYear(), rec.getPeriodMonth());
        Map<String, Object> s = summarize(e, ym, attendanceRepo.findByEmployeeIdAndAttDateBetween(e.getId(), ym.atDay(1), ym.atEndOfMonth()));
        rec.setDaysInMonth(ym.lengthOfMonth());
        rec.setPaidDays((BigDecimal) s.get("paidDays"));
        rec.setAbsentDays((BigDecimal) s.get("absentDays"));
        String n = note == null ? null : note.trim();
        rec.setDeductionNote(n == null || n.isEmpty() ? null : n.substring(0, Math.min(200, n.length())));
        return salaryRepo.save(rec);
    }

    public ExpenseSalaryRecord salaryRecord(Long id) {
        return salaryRepo.findById(id).orElseThrow(() -> new AppException("Salary record not found", HttpStatus.NOT_FOUND));
    }

    public ExpenseEmployee employee(Long id) {
        return employeeRepo.findById(id).orElseThrow(() -> new AppException("Employee not found", HttpStatus.NOT_FOUND));
    }

    public Map<String, Integer> counts(Long employeeId, YearMonth ym) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        StaffAttendance.STATUSES.forEach(s -> counts.put(s, 0));
        attendanceRepo.findByEmployeeIdAndAttDateBetween(employeeId, ym.atDay(1), ym.atEndOfMonth())
                .forEach(a -> counts.merge(a.getStatus(), 1, Integer::sum));
        return counts;
    }

    private List<ExpenseEmployee> employees(Long hostelId) {
        return employeeRepo.findByHostelId(hostelId).stream().sorted(Comparator.comparing(ExpenseEmployee::getName)).toList();
    }

    private static LocalTime time(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return null;
        try {
            return LocalTime.parse(String.valueOf(v).length() == 5 ? v + ":00" : String.valueOf(v));
        } catch (Exception e) {
            throw bad("Times must look like 09:30");
        }
    }

    private static AppException bad(String msg) {
        return new AppException(msg, HttpStatus.BAD_REQUEST);
    }
}
