package com.hostlify.module.expense.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "expense_salary_records",
    indexes = {
        @Index(name = "idx_esr_employee_id", columnList = "employee_id"),
        @Index(name = "idx_esr_hostel_id",   columnList = "hostel_id"),
        @Index(name = "idx_esr_period",      columnList = "period_year,period_month"),
        @Index(name = "idx_esr_status",      columnList = "status")
    }
)
@SQLRestriction("is_deleted = false")
public class ExpenseSalaryRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "gross_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossSalary = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductions = BigDecimal.ZERO;

    @Column(name = "net_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
