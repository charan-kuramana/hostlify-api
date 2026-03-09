package com.hostlify.module.expense.repository;

import com.hostlify.module.expense.entity.ExpenseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseRecordRepository extends JpaRepository<ExpenseRecord, Long> {
    List<ExpenseRecord> findByHostelIdAndPeriodYearAndPeriodMonth(Long hostelId, int year, int month);
    List<ExpenseRecord> findByHostelIdAndCategory(Long hostelId, String category);
}
