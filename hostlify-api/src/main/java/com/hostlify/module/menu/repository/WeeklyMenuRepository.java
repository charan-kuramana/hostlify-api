package com.hostlify.module.menu.repository;

import com.hostlify.module.menu.entity.WeeklyMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WeeklyMenuRepository extends JpaRepository<WeeklyMenu, Long> {
    Optional<WeeklyMenu> findByHostelIdAndWeekStart(Long hostelId, LocalDate weekStart);
    boolean existsByHostelIdAndWeekStart(Long hostelId, LocalDate weekStart);
    List<WeeklyMenu> findByHostelIdOrderByWeekStartDesc(Long hostelId);
}
