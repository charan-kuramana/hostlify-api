package com.hostlify.module.resident.mess;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.leave.entity.LeaveRequest;
import com.hostlify.module.leave.repository.LeaveRequestRepository;
import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mess: everyone eats by default; residents skip meals before a cut-off. Staff see the kitchen headcount,
 * bill a month by meals actually taken (optional per-meal rates), and see meal ratings + dish suggestions.
 */
@Service
public class MessService {

    public static final List<String> MEALS = List.of("BREAKFAST", "LUNCH", "DINNER");
    private static final int PLAN_DAYS = 7;
    private static final int RATE_WINDOW_DAYS = 3;
    private static final int MAX_OPEN_SUGGESTIONS = 3;

    private final MessRepos.Settings settingsRepo;
    private final MessRepos.OptOuts optOutRepo;
    private final MessRepos.Bills billRepo;
    private final MessRepos.Ratings ratingRepo;
    private final MessRepos.Suggestions suggestionRepo;
    private final BookingCheckinRepository bookingRepo;
    private final LeaveRequestRepository leaveRepo;
    private final HostellerProfileRepository profileRepo;
    private final PaymentService paymentService;
    private final JdbcTemplate jdbc;

    public MessService(MessRepos.Settings settingsRepo, MessRepos.OptOuts optOutRepo, MessRepos.Bills billRepo,
                       MessRepos.Ratings ratingRepo, MessRepos.Suggestions suggestionRepo,
                       BookingCheckinRepository bookingRepo, LeaveRequestRepository leaveRepo,
                       HostellerProfileRepository profileRepo, PaymentService paymentService, JdbcTemplate jdbc) {
        this.settingsRepo = settingsRepo;
        this.optOutRepo = optOutRepo;
        this.billRepo = billRepo;
        this.ratingRepo = ratingRepo;
        this.suggestionRepo = suggestionRepo;
        this.bookingRepo = bookingRepo;
        this.leaveRepo = leaveRepo;
        this.profileRepo = profileRepo;
        this.paymentService = paymentService;
        this.jdbc = jdbc;
    }

    // ═══ Settings ══════════════════════════════════════════════════════════

    public MessSettings settings(Long hostelId) {
        return settingsRepo.findByHostelId(hostelId).orElseGet(() -> {
            MessSettings s = new MessSettings();
            s.setHostelId(hostelId);
            return s;
        });
    }

    @Transactional
    public MessSettings saveSettings(Long hostelId, Map<String, Object> body) {
        MessSettings s = settings(hostelId);
        if (body.containsKey("enabled")) s.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        if (body.get("breakfastTime") != null) s.setBreakfastTime(time(body.get("breakfastTime")));
        if (body.get("lunchTime") != null) s.setLunchTime(time(body.get("lunchTime")));
        if (body.get("dinnerTime") != null) s.setDinnerTime(time(body.get("dinnerTime")));
        if (body.containsKey("askMealFeedback")) s.setAskMealFeedback(Boolean.TRUE.equals(body.get("askMealFeedback")));
        if (body.get("cutoffHours") != null) {
            int h = ((Number) body.get("cutoffHours")).intValue();
            if (h < 0 || h > 48) throw bad("Cut-off must be between 0 and 48 hours before the meal");
            s.setCutoffHours(h);
        }
        if (body.containsKey("breakfastRate")) s.setBreakfastRate(rate(body.get("breakfastRate")));
        if (body.containsKey("lunchRate")) s.setLunchRate(rate(body.get("lunchRate")));
        if (body.containsKey("dinnerRate")) s.setDinnerRate(rate(body.get("dinnerRate")));
        if (!s.getBreakfastTime().isBefore(s.getLunchTime()) || !s.getLunchTime().isBefore(s.getDinnerTime())) {
            throw bad("Meal times must be in order: breakfast, then lunch, then dinner");
        }
        s.setUpdatedAt(LocalDateTime.now());
        return settingsRepo.save(s);
    }

    // ═══ Resident: my meals ════════════════════════════════════════════════

    /** Next 7 days × 3 meals with skip/locked flags, plus recent meals the resident can still rate. */
    public Map<String, Object> myMeals(Long hostellerId) {
        BookingCheckin b = activeBooking(hostellerId);
        MessSettings s = settings(b.getHostelId());
        LocalDate today = DateUtils.todayKolkata();
        LocalDateTime now = DateUtils.nowKolkata();
        Set<String> skipped = optOutRepo.findByHostellerIdAndMealDateBetween(hostellerId, today, today.plusDays(PLAN_DAYS - 1))
                .stream().map(o -> o.getMealDate() + "|" + o.getMealType()).collect(Collectors.toSet());
        Set<String> onLeave = leaveDays(b, today, today.plusDays(PLAN_DAYS - 1));

        List<Map<String, Object>> days = new ArrayList<>();
        for (int i = 0; i < PLAN_DAYS; i++) {
            LocalDate d = today.plusDays(i);
            List<Map<String, Object>> meals = new ArrayList<>();
            for (String meal : MEALS) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("meal", meal);
                m.put("time", s.timeOf(meal).toString());
                m.put("skipped", skipped.contains(d + "|" + meal));
                m.put("locked", !now.isBefore(cutoff(s, d, meal)));
                m.put("cutoffAt", cutoff(s, d, meal).toString());
                meals.add(m);
            }
            days.add(Map.of("date", d.toString(), "day", d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                    "onLeave", onLeave.contains(d.toString()), "meals", meals));
        }

        // Meals already served in the last few days (rateable)
        Map<String, MealRating> mine = ratingRepo.findByHostellerIdAndMealDateBetween(hostellerId, today.minusDays(RATE_WINDOW_DAYS), today)
                .stream().collect(Collectors.toMap(r -> r.getMealDate() + "|" + r.getMealType(), r -> r, (a, c) -> a));
        List<Map<String, Object>> rateable = new ArrayList<>();
        for (int i = 0; i <= RATE_WINDOW_DAYS; i++) {
            LocalDate d = today.minusDays(i);
            for (String meal : MEALS) {
                if (now.isBefore(d.atTime(s.timeOf(meal)))) continue;
                if (skipped(hostellerId, d, meal)) continue;
                MealRating r = mine.get(d + "|" + meal);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", d.toString());
                m.put("meal", meal);
                m.put("rating", r == null ? null : r.getRating());
                m.put("comment", r == null ? null : r.getComment());
                rateable.add(m);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", s.isEnabled());
        out.put("cutoffHours", s.getCutoffHours());
        out.put("askMealFeedback", s.isAskMealFeedback());
        out.put("rates", rates(s));
        out.put("days", days);
        out.put("rateable", rateable);
        return out;
    }

    @Transactional
    public void setSkip(Long hostellerId, LocalDate date, String meal, boolean skip) {
        meal = meal(meal);
        BookingCheckin b = activeBooking(hostellerId);
        MessSettings s = settings(b.getHostelId());
        if (!s.isEnabled()) throw bad("Meal opt-out is not turned on for this hostel");
        LocalDate today = DateUtils.todayKolkata();
        if (date.isBefore(today) || date.isAfter(today.plusDays(PLAN_DAYS - 1))) {
            throw bad("You can change meals for the next " + PLAN_DAYS + " days only");
        }
        if (!DateUtils.nowKolkata().isBefore(cutoff(s, date, meal))) {
            throw bad("Too late to change this meal. The cut-off is " + s.getCutoffHours() + " hour(s) before it's served.");
        }
        Optional<MessOptOut> existing = optOutRepo.findByHostellerIdAndMealDateAndMealType(hostellerId, date, meal);
        if (skip && existing.isEmpty()) {
            MessOptOut o = new MessOptOut();
            o.setHostelId(b.getHostelId());
            o.setHostellerId(hostellerId);
            o.setMealDate(date);
            o.setMealType(meal);
            optOutRepo.save(o);
        } else if (!skip) {
            existing.ifPresent(optOutRepo::delete);
        }
    }

    @Transactional
    public void rate(Long hostellerId, LocalDate date, String meal, int rating, String comment) {
        meal = meal(meal);
        BookingCheckin b = activeBooking(hostellerId);
        MessSettings s = settings(b.getHostelId());
        LocalDate today = DateUtils.todayKolkata();
        if (rating < 1 || rating > 5) throw bad("Rating must be 1 to 5 stars");
        if (date.isAfter(today) || date.isBefore(today.minusDays(RATE_WINDOW_DAYS))) {
            throw bad("You can rate meals from the last " + RATE_WINDOW_DAYS + " days only");
        }
        if (DateUtils.nowKolkata().isBefore(date.atTime(s.timeOf(meal)))) throw bad("You can rate this meal after it's served");
        MealRating r = ratingRepo.findByHostellerIdAndMealDateAndMealType(hostellerId, date, meal).orElseGet(MealRating::new);
        r.setHostelId(b.getHostelId());
        r.setHostellerId(hostellerId);
        r.setMealDate(date);
        r.setMealType(meal);
        r.setRating((short) rating);
        r.setComment(comment == null || comment.isBlank() ? null : trim(comment, 300));
        ratingRepo.save(r);
    }

    // ═══ Staff: kitchen headcount ══════════════════════════════════════════

    public Map<String, Object> headcount(Long hostelId, LocalDate date) {
        List<BookingCheckin> active = bookingRepo.findByHostelIdAndStatus(hostelId, "ACTIVE").stream()
                .filter(b -> b.getCheckinDate() == null || !b.getCheckinDate().isAfter(date)).toList();
        Map<Long, String> names = names(active.stream().map(BookingCheckin::getHostellerId).toList());
        Set<Long> onLeave = new HashSet<>();
        for (BookingCheckin b : active) if (leaveDays(b, date, date).contains(date.toString())) onLeave.add(b.getHostellerId());
        Map<String, List<Long>> skipsByMeal = optOutRepo.findByHostelIdAndMealDateBetween(hostelId, date, date).stream()
                .collect(Collectors.groupingBy(MessOptOut::getMealType, Collectors.mapping(MessOptOut::getHostellerId, Collectors.toList())));
        Set<Long> residents = active.stream().map(BookingCheckin::getHostellerId).collect(Collectors.toSet());

        List<Map<String, Object>> meals = new ArrayList<>();
        for (String meal : MEALS) {
            List<Long> skip = skipsByMeal.getOrDefault(meal, List.of()).stream()
                    .filter(residents::contains).filter(id -> !onLeave.contains(id)).toList();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("meal", meal);
            m.put("residents", residents.size());
            m.put("onLeave", onLeave.size());
            m.put("skipping", skip.size());
            m.put("eating", residents.size() - onLeave.size() - skip.size());
            m.put("skippingNames", skip.stream().map(names::get).filter(Objects::nonNull).sorted().toList());
            meals.add(m);
        }
        return Map.of("date", date.toString(), "meals", meals,
                "onLeaveNames", onLeave.stream().map(names::get).filter(Objects::nonNull).sorted().toList());
    }

    // ═══ Staff: monthly mess bill ══════════════════════════════════════════

    public Map<String, Object> billPreview(Long hostelId, int year, int month) {
        MessSettings s = settings(hostelId);
        YearMonth ym = YearMonth.of(year, month);
        LocalDate from = ym.atDay(1), to = ym.atEndOfMonth();
        Map<String, Set<String>> skipsByResident = optOutRepo.findByHostelIdAndMealDateBetween(hostelId, from, to).stream()
                .collect(Collectors.groupingBy(o -> String.valueOf(o.getHostellerId()),
                        Collectors.mapping(o -> o.getMealDate() + "|" + o.getMealType(), Collectors.toSet())));

        Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
        for (BookingCheckin b : bookingRepo.findByHostelIdOrderByCreatedAtDesc(hostelId)) {
            if (b.getCheckinDate() == null) continue;
            LocalDate end = b.getCheckoutDate() != null ? b.getCheckoutDate().minusDays(1) : ("ACTIVE".equals(b.getStatus()) ? to : null);
            if (end == null) continue;
            LocalDate start = b.getCheckinDate().isAfter(from) ? b.getCheckinDate() : from;
            if (end.isAfter(to)) end = to;
            if (start.isAfter(end)) continue;
            Set<String> leave = leaveDays(b, start, end);
            Set<String> skips = skipsByResident.getOrDefault(String.valueOf(b.getHostellerId()), Set.of());
            int meals = 0;
            BigDecimal amount = BigDecimal.ZERO;
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                if (leave.contains(d.toString())) continue;
                for (String meal : MEALS) {
                    if (s.rateOf(meal).signum() <= 0 || skips.contains(d + "|" + meal)) continue;
                    meals++;
                    amount = amount.add(s.rateOf(meal));
                }
            }
            Map<String, Object> row = rows.computeIfAbsent(b.getHostellerId(), id -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("hostellerId", id);
                r.put("meals", 0);
                r.put("amount", BigDecimal.ZERO);
                return r;
            });
            row.put("meals", (int) row.get("meals") + meals);
            row.put("amount", ((BigDecimal) row.get("amount")).add(amount));
        }
        Map<Long, String> names = names(new ArrayList<>(rows.keySet()));
        Map<Long, HostellerProfile> profiles = profileRepo.findAllById(rows.keySet()).stream()
                .collect(Collectors.toMap(HostellerProfile::getId, p -> p));
        List<Map<String, Object>> list = new ArrayList<>();
        int totalMeals = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> r : rows.values()) {
            Long id = (Long) r.get("hostellerId");
            HostellerProfile p = profiles.get(id);
            boolean billable = p != null && Objects.equals(p.getHostelId(), hostelId) && ((BigDecimal) r.get("amount")).signum() > 0;
            r.put("name", names.getOrDefault(id, "Resident #" + id));
            r.put("billable", billable);
            if (!billable && ((BigDecimal) r.get("amount")).signum() > 0) r.put("note", "Moved out; add a charge manually if needed");
            if (billable) {
                totalMeals += (int) r.get("meals");
                total = total.add((BigDecimal) r.get("amount"));
            }
            list.add(r);
        }
        list.sort(Comparator.comparing(r -> String.valueOf(r.get("name"))));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", year);
        out.put("month", month);
        out.put("rates", rates(s));
        out.put("hasRates", MEALS.stream().anyMatch(m -> s.rateOf(m).signum() > 0));
        out.put("alreadyBilled", billRepo.existsByHostelIdAndPeriodYearAndPeriodMonth(hostelId, year, month));
        out.put("monthEnded", to.isBefore(DateUtils.todayKolkata()));
        out.put("rows", list);
        out.put("totalMeals", totalMeals);
        out.put("totalAmount", total);
        return out;
    }

    @Transactional
    public Map<String, Object> runBill(Long hostelId, int year, int month, Long staffUserId) {
        Map<String, Object> preview = billPreview(hostelId, year, month);
        if (Boolean.TRUE.equals(preview.get("alreadyBilled"))) throw new AppException("This month's mess bill was already added", HttpStatus.CONFLICT);
        if (!Boolean.TRUE.equals(preview.get("monthEnded"))) throw bad("You can bill a month after it ends");
        if (!Boolean.TRUE.equals(preview.get("hasRates"))) throw bad("Set a per-meal rate in mess settings first");
        String label = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year;
        LocalDate dueDate = DateUtils.todayKolkata().plusDays(5);
        int billed = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) preview.get("rows");
        for (Map<String, Object> r : rows) {
            if (!Boolean.TRUE.equals(r.get("billable"))) continue;
            BigDecimal amount = ((BigDecimal) r.get("amount")).setScale(2, RoundingMode.HALF_UP);
            paymentService.createCharges(hostelId, (Long) r.get("hostellerId"), "MESS", amount,
                    "Mess bill " + label + ": " + r.get("meals") + " meals", dueDate);
            billed++;
        }
        if (billed == 0) throw bad("Nobody has a mess amount to bill for " + label);
        MessBill bill = new MessBill();
        bill.setHostelId(hostelId);
        bill.setPeriodYear(year);
        bill.setPeriodMonth(month);
        bill.setResidents(billed);
        bill.setTotalMeals((int) preview.get("totalMeals"));
        bill.setTotalAmount((BigDecimal) preview.get("totalAmount"));
        bill.setCreatedBy(staffUserId);
        billRepo.save(bill);
        return Map.of("residents", billed, "totalAmount", bill.getTotalAmount(), "period", label);
    }

    public List<MessBill> bills(Long hostelId) {
        return billRepo.findByHostelIdOrderByPeriodYearDescPeriodMonthDesc(hostelId);
    }

    // ═══ Ratings & suggestions ═════════════════════════════════════════════

    /** Staff: 30-day averages per meal, last 14 days by day, and recent comments. */
    public Map<String, Object> ratingSummary(Long hostelId) {
        LocalDate today = DateUtils.todayKolkata();
        List<MealRating> all = ratingRepo.findByHostelIdAndMealDateBetweenOrderByMealDateDesc(hostelId, today.minusDays(29), today);
        List<Map<String, Object>> byMeal = new ArrayList<>();
        for (String meal : MEALS) {
            List<MealRating> rs = all.stream().filter(r -> meal.equals(r.getMealType())).toList();
            byMeal.add(Map.of("meal", meal, "count", rs.size(), "average", avg(rs)));
        }
        List<Map<String, Object>> daily = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d.toString());
            for (String meal : MEALS) {
                List<MealRating> rs = all.stream().filter(r -> d.equals(r.getMealDate()) && meal.equals(r.getMealType())).toList();
                row.put(meal.toLowerCase(), rs.isEmpty() ? null : avg(rs));
            }
            daily.add(row);
        }
        Map<Long, String> names = names(all.stream().map(MealRating::getHostellerId).distinct().toList());
        List<Map<String, Object>> comments = all.stream().filter(r -> r.getComment() != null).limit(20).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", r.getMealDate().toString());
            m.put("meal", r.getMealType());
            m.put("rating", r.getRating());
            m.put("comment", r.getComment());
            m.put("name", names.get(r.getHostellerId()));
            return m;
        }).toList();
        return Map.of("byMeal", byMeal, "daily", daily, "comments", comments, "totalRatings", all.size());
    }

    public List<Map<String, Object>> suggestions(Long hostelId, Long viewerHostellerId) {
        List<DishSuggestion> list = suggestionRepo.findByHostelIdAndStatusOrderByCreatedAtDesc(hostelId, "OPEN");
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(DishSuggestion::getId).toList();
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        Map<Long, Integer> votes = new HashMap<>();
        Set<Long> mine = new HashSet<>();
        jdbc.query("SELECT suggestion_id, hosteller_id FROM dish_suggestion_votes WHERE suggestion_id IN (" + in + ")", rs -> {
            long sid = rs.getLong(1);
            votes.merge(sid, 1, Integer::sum);
            if (viewerHostellerId != null && viewerHostellerId == rs.getLong(2)) mine.add(sid);
        });
        Map<Long, String> names = names(list.stream().map(DishSuggestion::getHostellerId).distinct().toList());
        return list.stream().map(sg -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", sg.getId());
            m.put("dish", sg.getDish());
            m.put("votes", votes.getOrDefault(sg.getId(), 0));
            m.put("myVote", mine.contains(sg.getId()));
            m.put("mine", Objects.equals(sg.getHostellerId(), viewerHostellerId));
            m.put("suggestedBy", viewerHostellerId == null ? names.get(sg.getHostellerId()) : null);
            m.put("createdAt", sg.getCreatedAt().toString());
            return m;
        }).sorted(Comparator.comparing((Map<String, Object> m) -> -(int) m.get("votes"))).toList();
    }

    @Transactional
    public DishSuggestion suggest(Long hostellerId, String dish) {
        BookingCheckin b = activeBooking(hostellerId);
        String name = dish == null ? "" : dish.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 100) throw bad("Dish name must be 2 to 100 characters");
        if (suggestionRepo.countByHostellerIdAndStatus(hostellerId, "OPEN") >= MAX_OPEN_SUGGESTIONS) {
            throw bad("You can have up to " + MAX_OPEN_SUGGESTIONS + " open suggestions at a time");
        }
        boolean dup = suggestionRepo.findByHostelIdAndStatusOrderByCreatedAtDesc(b.getHostelId(), "OPEN").stream()
                .anyMatch(sg -> sg.getDish().equalsIgnoreCase(name));
        if (dup) throw new AppException("Someone already suggested this. Vote for it instead.", HttpStatus.CONFLICT);
        DishSuggestion sg = new DishSuggestion();
        sg.setHostelId(b.getHostelId());
        sg.setHostellerId(hostellerId);
        sg.setDish(name);
        sg = suggestionRepo.save(sg);
        jdbc.update("INSERT INTO dish_suggestion_votes (suggestion_id, hosteller_id) VALUES (?, ?)", sg.getId(), hostellerId);
        return sg;
    }

    @Transactional
    public void vote(Long hostellerId, Long suggestionId, boolean up) {
        BookingCheckin b = activeBooking(hostellerId);
        DishSuggestion sg = suggestion(suggestionId);
        if (!Objects.equals(sg.getHostelId(), b.getHostelId())) throw new AppException("Not found", HttpStatus.NOT_FOUND);
        if (!"OPEN".equals(sg.getStatus())) throw bad("Voting has closed for this dish");
        if (up) {
            jdbc.update("INSERT INTO dish_suggestion_votes (suggestion_id, hosteller_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    suggestionId, hostellerId);
        } else {
            jdbc.update("DELETE FROM dish_suggestion_votes WHERE suggestion_id = ? AND hosteller_id = ?", suggestionId, hostellerId);
        }
    }

    @Transactional
    public DishSuggestion closeSuggestion(Long suggestionId, String status) {
        if (!List.of("ADDED", "DISMISSED").contains(status)) throw bad("Status must be ADDED or DISMISSED");
        DishSuggestion sg = suggestion(suggestionId);
        sg.setStatus(status);
        return suggestionRepo.save(sg);
    }

    public DishSuggestion suggestion(Long id) {
        return suggestionRepo.findById(id).orElseThrow(() -> new AppException("Suggestion not found", HttpStatus.NOT_FOUND));
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private BookingCheckin activeBooking(Long hostellerId) {
        return bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .orElseThrow(() -> bad("You need an active stay to use the mess"));
    }

    /** True when the resident opted out of this meal. */
    public boolean isSkipping(Long hostellerId, LocalDate d, String meal) {
        return skipped(hostellerId, d, meal);
    }

    private boolean skipped(Long hostellerId, LocalDate d, String meal) {
        return optOutRepo.findByHostellerIdAndMealDateAndMealType(hostellerId, d, meal).isPresent();
    }

    private static LocalDateTime cutoff(MessSettings s, LocalDate d, String meal) {
        return d.atTime(s.timeOf(meal)).minusHours(s.getCutoffHours());
    }

    /** Dates (yyyy-MM-dd) within [from, to] the resident is on approved leave for this booking. */
    private Set<String> leaveDays(BookingCheckin b, LocalDate from, LocalDate to) {
        Set<String> days = new HashSet<>();
        if (b.getId() == null) return days;
        for (LeaveRequest l : leaveRepo.findByBookingIdAndStatus(b.getId(), "APPROVED")) {
            LocalDate s = l.getFromDate().isAfter(from) ? l.getFromDate() : from;
            LocalDate e = l.getToDate().isBefore(to) ? l.getToDate() : to;
            for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) days.add(d.toString());
        }
        return days;
    }

    private Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> m = new HashMap<>();
        profileRepo.findAllById(new HashSet<>(ids)).forEach(p ->
                m.put(p.getId(), (p.getFirstName() + " " + (p.getLastName() == null ? "" : p.getLastName())).trim()));
        return m;
    }

    private static Map<String, BigDecimal> rates(MessSettings s) {
        return Map.of("BREAKFAST", s.getBreakfastRate(), "LUNCH", s.getLunchRate(), "DINNER", s.getDinnerRate());
    }

    private static double avg(List<MealRating> rs) {
        return rs.isEmpty() ? 0 : Math.round(rs.stream().mapToInt(MealRating::getRating).average().orElse(0) * 10) / 10.0;
    }

    static String meal(String meal) {
        String m = meal == null ? "" : meal.toUpperCase();
        if (!MEALS.contains(m)) throw bad("Meal must be BREAKFAST, LUNCH or DINNER");
        return m;
    }

    private static LocalTime time(Object v) {
        try {
            return LocalTime.parse(String.valueOf(v), DateTimeFormatter.ofPattern("H:mm"));
        } catch (Exception e) {
            throw bad("Times must look like 08:00");
        }
    }

    private static BigDecimal rate(Object v) {
        BigDecimal r = v == null || String.valueOf(v).isBlank() ? BigDecimal.ZERO : new BigDecimal(String.valueOf(v));
        if (r.signum() < 0 || r.compareTo(new BigDecimal("5000")) > 0) throw bad("Meal rate must be between 0 and 5000");
        return r.setScale(2, RoundingMode.HALF_UP);
    }

    private static String trim(String s, int max) {
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static AppException bad(String msg) {
        return new AppException(msg, HttpStatus.BAD_REQUEST);
    }
}
