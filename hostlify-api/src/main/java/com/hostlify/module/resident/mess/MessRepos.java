package com.hostlify.module.resident.mess;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Spring Data repositories for the mess feature (nested public interfaces are picked up by component scan). */
public final class MessRepos {

    private MessRepos() {}

    public interface Settings extends JpaRepository<MessSettings, Long> {
        Optional<MessSettings> findByHostelId(Long hostelId);
    }

    public interface OptOuts extends JpaRepository<MessOptOut, Long> {
        List<MessOptOut> findByHostellerIdAndMealDateBetween(Long hostellerId, LocalDate from, LocalDate to);
        List<MessOptOut> findByHostelIdAndMealDateBetween(Long hostelId, LocalDate from, LocalDate to);
        Optional<MessOptOut> findByHostellerIdAndMealDateAndMealType(Long hostellerId, LocalDate date, String mealType);
    }

    public interface Bills extends JpaRepository<MessBill, Long> {
        boolean existsByHostelIdAndPeriodYearAndPeriodMonth(Long hostelId, int year, int month);
        List<MessBill> findByHostelIdOrderByPeriodYearDescPeriodMonthDesc(Long hostelId);
    }

    public interface Ratings extends JpaRepository<MealRating, Long> {
        Optional<MealRating> findByHostellerIdAndMealDateAndMealType(Long hostellerId, LocalDate date, String mealType);
        List<MealRating> findByHostellerIdAndMealDateBetween(Long hostellerId, LocalDate from, LocalDate to);
        List<MealRating> findByHostelIdAndMealDateBetweenOrderByMealDateDesc(Long hostelId, LocalDate from, LocalDate to);
    }

    public interface Suggestions extends JpaRepository<DishSuggestion, Long> {
        List<DishSuggestion> findByHostelIdAndStatusOrderByCreatedAtDesc(Long hostelId, String status);
        long countByHostellerIdAndStatus(Long hostellerId, String status);
    }
}
