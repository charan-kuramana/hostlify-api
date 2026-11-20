package com.hostlify.module.resident.mess;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "mess_settings")
public class MessSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hostel_id", nullable = false, unique = true)
    private Long hostelId;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "breakfast_time", nullable = false)
    private LocalTime breakfastTime = LocalTime.of(8, 0);

    @Column(name = "lunch_time", nullable = false)
    private LocalTime lunchTime = LocalTime.of(13, 0);

    @Column(name = "dinner_time", nullable = false)
    private LocalTime dinnerTime = LocalTime.of(20, 0);

    @Column(name = "cutoff_hours", nullable = false)
    private int cutoffHours = 3;

    /** Push "How was lunch?" to residents after each meal. */
    @Column(name = "ask_meal_feedback", nullable = false)
    private boolean askMealFeedback = false;

    @Column(name = "breakfast_rate", nullable = false, precision = 8, scale = 2)
    private BigDecimal breakfastRate = BigDecimal.ZERO;

    @Column(name = "lunch_rate", nullable = false, precision = 8, scale = 2)
    private BigDecimal lunchRate = BigDecimal.ZERO;

    @Column(name = "dinner_rate", nullable = false, precision = 8, scale = 2)
    private BigDecimal dinnerRate = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public LocalTime timeOf(String meal) {
        return switch (meal) {
            case "BREAKFAST" -> breakfastTime;
            case "LUNCH" -> lunchTime;
            default -> dinnerTime;
        };
    }

    public BigDecimal rateOf(String meal) {
        return switch (meal) {
            case "BREAKFAST" -> breakfastRate;
            case "LUNCH" -> lunchRate;
            default -> dinnerRate;
        };
    }
}
