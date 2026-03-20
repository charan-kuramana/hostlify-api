package com.hostlify.module.expense.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(
    name = "expense_employees",
    indexes = @Index(name = "idx_ee_hostel_id", columnList = "hostel_id")
)
@SQLRestriction("is_deleted = 0")
public class ExpenseEmployee extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(name = "employee_code", length = 30)
    private String employeeCode;

    @JsonAlias({"employeeName"})
    @Column(nullable = false, length = 150)
    private String name;

    @JsonAlias({"designation"})
    @Column(nullable = false, length = 80)
    private String role;

    @Column(length = 15)
    private String mobile;

    @Column(name = "base_salary", nullable = false, precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal hra = BigDecimal.ZERO;

    @JsonProperty("salary")
    public BigDecimal getSalaryAlias() { return baseSalary; }

    @JsonProperty("salary")
    public void setSalaryAlias(BigDecimal salary) { this.baseSalary = salary; }

    @Transient private LocalDate lastPaidDate;
}
