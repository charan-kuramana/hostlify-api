package com.hostlify.module.booking.service;

import com.hostlify.module.hostel.entity.Bed;
import com.hostlify.module.hostel.entity.HostelBookingSetting;
import com.hostlify.module.hostel.entity.HostelPricing;
import com.hostlify.module.hostel.entity.Room;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.hostel.repository.HostelBookingSettingRepository;
import com.hostlify.module.hostel.repository.HostelPricingRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Short stays (a night, a week, 15 days …), billed by day. The owner switches them on per hostel and sets
 * per-room-type rates: per day (required), and optionally per 7 days and per 15 days. The price is the
 * cheapest combination of those blocks, so a 9-day stay = 1 week + 2 days.
 */
@Service
public class ShortStayService {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("d MMM");

    private final HostelBookingSettingRepository settingRepo;
    private final HostelPricingRepository pricingRepo;
    private final BedRepository bedRepo;
    private final RoomRepository roomRepo;

    public ShortStayService(HostelBookingSettingRepository settingRepo, HostelPricingRepository pricingRepo,
                            BedRepository bedRepo, RoomRepository roomRepo) {
        this.settingRepo = settingRepo;
        this.pricingRepo = pricingRepo;
        this.bedRepo = bedRepo;
        this.roomRepo = roomRepo;
    }

    public record Quote(int days, BigDecimal amount, String breakdown, String description, String roomType) {}

    /** Nights between the dates; a same-day stay counts as 1 day. */
    public static int days(LocalDate in, LocalDate out) {
        return (int) Math.max(1, ChronoUnit.DAYS.between(in, out));
    }

    public Quote quote(Long hostelId, Long bedId, LocalDate checkIn, LocalDate checkOut) {
        HostelBookingSetting s = settingRepo.findByHostelId(hostelId).orElse(null);
        if (s == null || !s.isShortStayEnabled()) throw new AppException("This hostel does not offer short stays", HttpStatus.BAD_REQUEST);
        if (checkIn == null || checkOut == null) throw new AppException("Choose check-in and check-out dates", HttpStatus.BAD_REQUEST);
        if (checkOut.isBefore(checkIn)) throw new AppException("Check-out must be on or after check-in", HttpStatus.BAD_REQUEST);
        int days = days(checkIn, checkOut);
        if (days < s.getShortStayMinDays() || days > s.getShortStayMaxDays()) {
            throw new AppException("Short stays here are " + s.getShortStayMinDays() + " to " + s.getShortStayMaxDays()
                    + " days. For longer, book a monthly stay.", HttpStatus.BAD_REQUEST);
        }
        Bed bed = bedRepo.findById(bedId).orElseThrow(() -> new AppException("Bed not found", HttpStatus.NOT_FOUND));
        if (!Objects.equals(bed.getHostelId(), hostelId)) throw new AppException("Bed does not belong to this hostel", HttpStatus.BAD_REQUEST);
        Room room = roomRepo.findById(bed.getRoomId()).orElseThrow();
        HostelPricing p = pricingFor(hostelId, room.getRoomType());
        if (p == null || p.getDailyRent() == null) {
            throw new AppException("Short-stay rates are not set for this room type yet. Please ask the hostel.", HttpStatus.BAD_REQUEST);
        }
        return price(days, p, checkIn, checkOut);
    }

    /** Owner-set rates for a room type (exact match first, then normalised like pricing tiers are stored). */
    public HostelPricing pricingFor(Long hostelId, String roomType) {
        if (roomType == null) return null;
        return pricingRepo.findByHostelIdAndRoomType(hostelId, roomType)
                .or(() -> pricingRepo.findByHostelIdAndRoomType(hostelId, roomType.trim().toUpperCase().replaceAll("[\\s-]+", "_")))
                .orElse(null);
    }

    static Quote price(int days, HostelPricing p, LocalDate in, LocalDate out) {
        // cheapest mix of 15-day, 7-day and 1-day blocks
        BigDecimal[] best = new BigDecimal[days + 1];
        int[] choice = new int[days + 1];
        best[0] = BigDecimal.ZERO;
        for (int d = 1; d <= days; d++) {
            best[d] = best[d - 1].add(p.getDailyRent());
            choice[d] = 1;
            if (p.getWeeklyRent() != null && d >= 7 && best[d - 7].add(p.getWeeklyRent()).compareTo(best[d]) < 0) {
                best[d] = best[d - 7].add(p.getWeeklyRent());
                choice[d] = 7;
            }
            if (p.getFortnightRent() != null && d >= 15 && best[d - 15].add(p.getFortnightRent()).compareTo(best[d]) < 0) {
                best[d] = best[d - 15].add(p.getFortnightRent());
                choice[d] = 15;
            }
        }
        Map<Integer, Integer> count = new TreeMap<>(Comparator.reverseOrder());
        for (int d = days; d > 0; d -= choice[d]) count.merge(choice[d], 1, Integer::sum);
        List<String> parts = new ArrayList<>();
        count.forEach((size, n) -> {
            BigDecimal rate = size == 15 ? p.getFortnightRent() : size == 7 ? p.getWeeklyRent() : p.getDailyRent();
            String unit = size == 15 ? "15 days" : size == 7 ? "week" : "day";
            parts.add(n + " × " + unit + " @ Rs. " + rate.stripTrailingZeros().toPlainString());
        });
        String breakdown = String.join(" + ", parts);
        String desc = "Short stay: " + days + (days == 1 ? " day" : " days") + " (" + in.format(D) + " – " + out.format(D) + ")";
        return new Quote(days, best[days], breakdown, desc, p.getRoomType());
    }

    public Map<String, Object> view(Quote q) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", q.days());
        m.put("amount", q.amount());
        m.put("breakdown", q.breakdown());
        m.put("description", q.description());
        m.put("roomType", q.roomType());
        return m;
    }

    /** What a resident needs to know before booking: whether short stays are offered and the rates. */
    public Map<String, Object> info(Long hostelId) {
        HostelBookingSetting s = settingRepo.findByHostelId(hostelId).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>();
        boolean on = s != null && s.isShortStayEnabled();
        m.put("enabled", on);
        if (!on) return m;
        m.put("minDays", s.getShortStayMinDays());
        m.put("maxDays", s.getShortStayMaxDays());
        List<Map<String, Object>> rates = new ArrayList<>();
        for (HostelPricing p : pricingRepo.findByHostelIdAndIsActiveTrue(hostelId)) {
            if (p.getDailyRent() == null) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("roomType", p.getRoomType());
            r.put("dailyRent", p.getDailyRent());
            r.put("weeklyRent", p.getWeeklyRent());
            r.put("fortnightRent", p.getFortnightRent());
            rates.add(r);
        }
        m.put("rates", rates);
        return m;
    }
}
