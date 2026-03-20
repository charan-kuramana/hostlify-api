package com.hostlify.module.expense.repository;

import com.hostlify.module.expense.entity.ExpenseSalaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseSalaryRecordRepository extends JpaRepository<ExpenseSalaryRecord, Long> {
    List<ExpenseSalaryRecord> findByHostelIdAndPeriodYearAndPeriodMonth(Long hostelId, int year, int month);
    Optional<ExpenseSalaryRecord> findByEmployeeIdAndPeriodYearAndPeriodMonth(Long employeeId, int year, int month);
    Optional<ExpenseSalaryRecord> findFirstByEmployeeIdAndStatusOrderByPeriodYearDescPeriodMonthDesc(Long employeeId, String status);
}
