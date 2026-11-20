package com.hostlify.module.ops;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.util.DateUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Owner dashboard: collection rate, overdue ageing, occupancy trend. */
@Service
public class InsightsService {

    private static final int MONTHS = 6;

    private final PaymentService paymentService;
    private final BookingCheckinRepository bookingRepo;
    private final BedRepository bedRepo;

    public InsightsService(PaymentService paymentService, BookingCheckinRepository bookingRepo, BedRepository bedRepo) {
        this.paymentService = paymentService;
        this.bookingRepo = bookingRepo;
        this.bedRepo = bedRepo;
    }

    public Map<String, Object> forHostel(Long hostelId) {
        LocalDate today = DateUtils.todayKolkata();
        YearMonth now = YearMonth.from(today);
        List<PaymentDue> dues = paymentService.getAllDuesByHostel(hostelId);

        // 1. Collection: billed vs collected for dues falling due in each of the last 6 months
        List<Map<String, Object>> collection = new ArrayList<>();
        for (int i = MONTHS - 1; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            BigDecimal billed = BigDecimal.ZERO, collected = BigDecimal.ZERO;
            for (PaymentDue d : dues) {
                if (d.getDueDate() == null || !YearMonth.from(d.getDueDate()).equals(ym)) continue;
                billed = billed.add(d.getRentAmount());
                collected = collected.add(min(paid(d), d.getRentAmount()));
            }
            collection.add(Map.of("month", label(ym), "billed", billed, "collected", collected, "rate", pct(collected, billed)));
        }

        // 2. Overdue ageing of what's still outstanding
        String[] names = {"1–7 days", "8–30 days", "31–60 days", "60+ days"};
        BigDecimal[] amounts = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        int[] counts = new int[4];
        Set<Long> residents = new HashSet<>();
        for (PaymentDue d : dues) {
            if ("PAID".equals(d.getStatus()) || d.getDueDate() == null || !d.getDueDate().isBefore(today)) continue;
            BigDecimal outstanding = d.getRentAmount().subtract(paid(d));
            if (outstanding.signum() <= 0) continue;
            long age = ChronoUnit.DAYS.between(d.getDueDate(), today);
            int b = age <= 7 ? 0 : age <= 30 ? 1 : age <= 60 ? 2 : 3;
            amounts[b] = amounts[b].add(outstanding);
            counts[b]++;
            residents.add(d.getHostellerId());
        }
        List<Map<String, Object>> ageing = new ArrayList<>();
        BigDecimal totalOverdue = BigDecimal.ZERO;
        for (int i = 0; i < 4; i++) {
            ageing.add(Map.of("bucket", names[i], "amount", amounts[i], "dues", counts[i]));
            totalOverdue = totalOverdue.add(amounts[i]);
        }

        // 3. Occupancy at each month end (today for the current month), against today's bed count
        long beds = bedRepo.countByHostelId(hostelId);
        List<BookingCheckin> bookings = bookingRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
        List<Map<String, Object>> occupancy = new ArrayList<>();
        for (int i = MONTHS - 1; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            LocalDate at = i == 0 ? today : ym.atEndOfMonth();
            long occupied = bookings.stream().filter(b -> b.getCheckinDate() != null && !b.getCheckinDate().isAfter(at)
                    && (b.getCheckoutDate() != null ? b.getCheckoutDate().isAfter(at) : "ACTIVE".equals(b.getStatus()))).count();
            occupancy.add(Map.of("month", label(ym), "occupied", occupied, "beds", beds,
                    "rate", beds == 0 ? 0 : Math.round(occupied * 100.0 / beds)));
        }

        Map<String, Object> thisMonth = collection.get(collection.size() - 1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("collection", collection);
        out.put("collectionRateThisMonth", thisMonth.get("rate"));
        out.put("ageing", ageing);
        out.put("totalOverdue", totalOverdue);
        out.put("overdueResidents", residents.size());
        out.put("occupancy", occupancy);
        return out;
    }

    private static BigDecimal paid(PaymentDue d) {
        return d.getPaidAmount() == null ? BigDecimal.ZERO : d.getPaidAmount();
    }

    private static BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static long pct(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? 0 : part.multiply(BigDecimal.valueOf(100)).divide(whole, 0, RoundingMode.HALF_UP).longValue();
    }

    private static String label(YearMonth ym) {
        return ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + String.valueOf(ym.getYear()).substring(2);
    }
}
