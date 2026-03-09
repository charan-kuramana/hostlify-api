package com.hostlify.module.expense.repository;

import com.hostlify.module.expense.entity.ExpenseEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseEmployeeRepository extends JpaRepository<ExpenseEmployee, Long> {
    List<ExpenseEmployee> findByHostelId(Long hostelId);
}
