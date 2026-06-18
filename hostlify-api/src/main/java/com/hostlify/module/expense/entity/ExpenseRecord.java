package com.hostlify.module.expense.entity;

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
    name = "expense_records",
    indexes = {
        @Index(name = "idx_er_hostel_id", columnList = "hostel_id"),
        @Index(name = "idx_er_period",    columnList = "period_year,period_month"),
        @Index(name = "idx_er_category",  columnList = "category")
    }
)
@SQLRestriction("is_deleted = false")
public class ExpenseRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false)
    private Long hostelId;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String description;

    @Column(name = "vendor_name", length = 200)
    private String vendorName;

    @Column(name = "invoice_number", length = 100)
    private String invoiceNumber;

    @Column(name = "invoice_url", length = 500)
    private String invoiceUrl;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;
}
