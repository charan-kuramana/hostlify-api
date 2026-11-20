package com.hostlify.module.resident.feedback;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.complaint.entity.ComplaintRecord;
import com.hostlify.module.complaint.repository.ComplaintRecordRepository;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.notification.service.PushService;
import com.hostlify.module.resident.mess.MessRepos;
import com.hostlify.module.resident.mess.MessService;
import com.hostlify.module.resident.mess.MessSettings;
import com.hostlify.module.review.repository.HostelReviewRepository;
import com.hostlify.shared.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * "Tell us how it was": what a resident hasn't rated yet — today's / yesterday's meals once served,
 * complaints fixed recently, and stays they checked out of — plus an optional push after each meal.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    private static final Map<String, String> MEAL_NAME = Map.of("BREAKFAST", "breakfast", "LUNCH", "lunch", "DINNER", "dinner");

    private final MessService messService;
    private final MessRepos.Settings messSettings;
    private final ComplaintRecordRepository complaintRepo;
    private final BookingCheckinRepository bookingRepo;
    private final HostelReviewRepository reviewRepo;
    private final HostelRepository hostelRepo;
    private final HostellerProfileRepository profileRepo;
    private final PushService push;
    private final JdbcTemplate jdbc;

    public FeedbackService(MessService messService, MessRepos.Settings messSettings, ComplaintRecordRepository complaintRepo,
                           BookingCheckinRepository bookingRepo, HostelReviewRepository reviewRepo, HostelRepository hostelRepo,
                           HostellerProfileRepository profileRepo, PushService push, JdbcTemplate jdbc) {
        this.messService = messService;
        this.messSettings = messSettings;
        this.complaintRepo = complaintRepo;
        this.bookingRepo = bookingRepo;
        this.reviewRepo = reviewRepo;
        this.hostelRepo = hostelRepo;
        this.profileRepo = profileRepo;
        this.push = push;
        this.jdbc = jdbc;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> pending(Long hostellerId) {
        Map<String, Object> out = new LinkedHashMap<>();
        LocalDate today = DateUtils.todayKolkata();

        // Meals served today / yesterday that are not rated yet (newest first, max 3)
        List<Map<String, Object>> meals = new ArrayList<>();
        if (bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE").isPresent()) {
            try {
                List<Map<String, Object>> rateable = (List<Map<String, Object>>) messService.myMeals(hostellerId).get("rateable");
                for (Map<String, Object> m : rateable) {
                    if (m.get("rating") != null) continue;
                    if (LocalDate.parse(String.valueOf(m.get("date"))).isBefore(today.minusDays(1))) continue;
                    meals.add(m);
                    if (meals.size() >= 3) break;
                }
            } catch (Exception e) {
                log.debug("No meals to rate for {}: {}", hostellerId, e.getMessage());
            }
        }
        out.put("meals", meals);

        // Complaints fixed in the last 14 days that the resident hasn't rated
        LocalDateTime since = LocalDateTime.now().minusDays(14);
        List<Map<String, Object>> complaints = new ArrayList<>();
        for (ComplaintRecord c : complaintRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId)) {
            if (!("RESOLVED".equals(c.getStatus()) || "CLOSED".equals(c.getStatus()))) continue;
            if (c.getResidentRating() != null || c.getResolvedAt() == null || c.getResolvedAt().isBefore(since)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("title", c.getTitle());
            m.put("category", c.getCategory());
            m.put("resolvedAt", c.getResolvedAt());
            complaints.add(m);
        }
        out.put("complaints", complaints);

        // Stays checked out of in the last 60 days without a review
        List<Map<String, Object>> stays = new ArrayList<>();
        for (BookingCheckin b : bookingRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId)) {
            if (!"CHECKED_OUT".equals(b.getStatus()) || b.getCheckoutDate() == null || b.getCheckoutDate().isBefore(today.minusDays(60))) continue;
            if (reviewRepo.findFirstByHostellerIdAndBookingIdOrderByCreatedAtDesc(hostellerId, b.getId()).isPresent()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bookingId", b.getId());
            m.put("hostelId", b.getHostelId());
            m.put("hostelName", hostelRepo.findById(b.getHostelId()).map(Hostel::getName).orElse("your hostel"));
            m.put("checkoutDate", b.getCheckoutDate());
            stays.add(m);
        }
        out.put("stays", stays);
        return out;
    }

    /**
     * Every 15 minutes: for hostels that switched it on, push "How was lunch?" once per meal,
     * about 45 minutes after it starts, to residents staying there (not to those who skipped it).
     */
    @Scheduled(cron = "0 */15 * * * *")
    public void mealNudges() {
        LocalDateTime now = DateUtils.nowKolkata();
        LocalDate today = now.toLocalDate();
        for (MessSettings s : messSettings.findAll()) {
            if (!s.isAskMealFeedback()) continue;
            for (String meal : List.of("BREAKFAST", "LUNCH", "DINNER")) {
                LocalDateTime start = today.atTime(s.timeOf(meal));
                if (now.isBefore(start.plusMinutes(45)) || now.isAfter(start.plusMinutes(120))) continue;
                int inserted = jdbc.update("INSERT INTO meal_feedback_nudges (hostel_id, meal_date, meal_type) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                        s.getHostelId(), today, meal);
                if (inserted == 0) continue; // already nudged
                int sent = 0;
                for (BookingCheckin b : bookingRepo.findByHostelIdAndStatus(s.getHostelId(), "ACTIVE")) {
                    if (messService.isSkipping(b.getHostellerId(), today, meal)) continue;
                    Long authUserId = profileRepo.findById(b.getHostellerId()).map(p -> p.getAuthUserId()).orElse(null);
                    if (authUserId == null) continue;
                    try {
                        sent += push.sendToUser(authUserId, "How was " + MEAL_NAME.get(meal) + "?",
                                "Tap to rate today's " + MEAL_NAME.get(meal) + " — it takes 2 seconds and helps the kitchen.", "/app/dashboard");
                    } catch (Exception e) {
                        log.debug("Meal nudge push failed: {}", e.getMessage());
                    }
                }
                log.info("[FEEDBACK] Meal nudge {} {} hostel {}: {} push(es)", today, meal, s.getHostelId(), sent);
            }
        }
    }
}
