package com.hostlify.module.ops;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hostel.entity.Room;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.payment.service.PaymentService;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Room meter readings → amount (units × rate + fixed) → split across the residents who stayed in the room
 * that month, in proportion to days stayed → an Electricity (or "Water" Other) charge per resident.
 */
@Service
public class MeterService {

    @org.springframework.beans.factory.annotation.Autowired
    private com.hostlify.module.payment.service.PaymentSettingsService paymentSettings;

    public static final List<String> TYPES = List.of("ELECTRICITY", "WATER");

    private final OpsRepos.Meters meterRepo;
    private final RoomRepository roomRepo;
    private final BookingCheckinRepository bookingRepo;
    private final HostellerProfileRepository profileRepo;
    private final PaymentService paymentService;

    public MeterService(OpsRepos.Meters meterRepo, RoomRepository roomRepo, BookingCheckinRepository bookingRepo,
                        HostellerProfileRepository profileRepo, PaymentService paymentService) {
        this.meterRepo = meterRepo;
        this.roomRepo = roomRepo;
        this.bookingRepo = bookingRepo;
        this.profileRepo = profileRepo;
        this.paymentService = paymentService;
    }

    /** Every room with this month's reading (if any), last month's closing reading and the residents it would bill. */
    public Map<String, Object> month(Long hostelId, String type, int year, int month) {
        type = type(type);
        Map<Long, MeterReading> readings = new HashMap<>();
        for (MeterReading r : meterRepo.findByHostelIdAndPeriodYearAndPeriodMonth(hostelId, year, month)) {
            if (type.equals(r.getMeterType())) readings.put(r.getRoomId(), r);
        }
        List<BookingCheckin> bookings = bookingRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
        YearMonth ym = YearMonth.of(year, month);
        List<Map<String, Object>> rooms = new ArrayList<>();
        for (Room room : roomRepo.findByHostelId(hostelId).stream().sorted(Comparator.comparing(Room::getRoomName)).toList()) {
            MeterReading r = readings.get(room.getId());
            BigDecimal previous = r != null ? r.getPreviousReading() : lastClosing(room.getId(), type, ym);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("roomId", room.getId());
            m.put("roomName", room.getRoomName());
            m.put("previousReading", previous);
            m.put("reading", r);
            m.put("residents", shares(room.getId(), bookings, ym).size());
            rooms.add(m);
        }
        BigDecimal lastRate = meterRepo.findFirstByHostelIdAndMeterTypeOrderByCreatedAtDesc(hostelId, type)
                .map(MeterReading::getRatePerUnit).orElse(type.equals("ELECTRICITY") ? new BigDecimal("8.00") : BigDecimal.ZERO);
        return Map.of("type", type, "year", year, "month", month, "lastRate", lastRate, "rooms", rooms,
                "monthEnded", ym.atEndOfMonth().isBefore(DateUtils.todayKolkata()));
    }

    @Transactional
    public MeterReading save(Long hostelId, Map<String, Object> body, Long staffUserId) {
        String type = type(String.valueOf(body.get("meterType")));
        int year = num(body.get("year")).intValue(), month = num(body.get("month")).intValue();
        YearMonth ym = YearMonth.of(year, month);
        if (ym.isAfter(YearMonth.from(DateUtils.todayKolkata()))) throw bad("You can't enter readings for a future month");
        Room room = roomRepo.findById(num(body.get("roomId")).longValue()).orElseThrow(() -> bad("Room not found"));
        if (!Objects.equals(room.getHostelId(), hostelId)) throw bad("This room is not in this hostel");
        MeterReading r = meterRepo.findByRoomIdAndMeterTypeAndPeriodYearAndPeriodMonth(room.getId(), type, year, month).orElseGet(MeterReading::new);
        if ("BILLED".equals(r.getStatus())) throw new AppException("This reading is already billed and can't be changed", HttpStatus.CONFLICT);
        BigDecimal prev = money(body.get("previousReading"), "Previous reading");
        BigDecimal curr = money(body.get("currentReading"), "Current reading");
        if (curr.compareTo(prev) < 0) throw bad("Current reading can't be lower than the previous reading");
        BigDecimal rate = money(body.get("ratePerUnit"), "Rate per unit");
        if (rate.compareTo(new BigDecimal("100")) > 0) throw bad("Rate per unit looks too high");
        BigDecimal fixed = body.get("fixedCharge") == null || String.valueOf(body.get("fixedCharge")).isBlank()
                ? BigDecimal.ZERO : money(body.get("fixedCharge"), "Fixed charge");
        BigDecimal units = curr.subtract(prev);
        r.setHostelId(hostelId);
        r.setRoomId(room.getId());
        r.setMeterType(type);
        r.setPeriodYear(year);
        r.setPeriodMonth(month);
        r.setPreviousReading(prev);
        r.setCurrentReading(curr);
        r.setUnits(units);
        r.setRatePerUnit(rate);
        r.setFixedCharge(fixed);
        r.setAmount(units.multiply(rate).add(fixed).setScale(2, RoundingMode.HALF_UP));
        r.setCreatedBy(staffUserId);
        return meterRepo.save(r);
    }

    @Transactional
    public void delete(Long id) {
        MeterReading r = reading(id);
        if ("BILLED".equals(r.getStatus())) throw new AppException("A billed reading can't be deleted", HttpStatus.CONFLICT);
        meterRepo.delete(r);
    }

    /** Bill every DRAFT reading of the month (or the given ids): one charge per resident for their share. */
    @Transactional
    public Map<String, Object> bill(Long hostelId, String type, int year, int month, List<Long> onlyIds) {
        if (paymentSettings.getOrDefault(hostelId).isUtilitiesIncluded()) {
            throw new com.hostlify.shared.exception.AppException("Electricity and water are included in rent for this hostel. To bill them, turn on "
                    + "\"Bill electricity / water separately\" in Settings → Payments first.", org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        type = type(type);
        YearMonth ym = YearMonth.of(year, month);
        if (!ym.atEndOfMonth().isBefore(DateUtils.todayKolkata())) throw bad("You can bill a month after it ends");
        List<BookingCheckin> bookings = bookingRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
        String label = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year;
        LocalDate dueDate = DateUtils.todayKolkata().plusDays(7);
        int roomsBilled = 0, charges = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<String> skipped = new ArrayList<>();
        for (MeterReading r : meterRepo.findByHostelIdAndPeriodYearAndPeriodMonth(hostelId, year, month)) {
            if (!type.equals(r.getMeterType()) || !"DRAFT".equals(r.getStatus())) continue;
            if (onlyIds != null && !onlyIds.isEmpty() && !onlyIds.contains(r.getId())) continue;
            String roomName = roomRepo.findById(r.getRoomId()).map(Room::getRoomName).orElse("Room");
            if (r.getAmount().signum() <= 0) { skipped.add(roomName + " (nothing to bill)"); continue; }
            Map<Long, Long> shares = shares(r.getRoomId(), bookings, ym);
            // Only residents still with this hostel can be charged here
            Map<Long, HostellerProfile> profiles = new HashMap<>();
            profileRepo.findAllById(shares.keySet()).forEach(p -> profiles.put(p.getId(), p));
            shares.keySet().removeIf(id -> profiles.get(id) == null || !Objects.equals(profiles.get(id).getHostelId(), hostelId));
            if (shares.isEmpty()) { skipped.add(roomName + " (no current residents stayed this month)"); continue; }
            long totalDays = shares.values().stream().mapToLong(Long::longValue).sum();
            BigDecimal remaining = r.getAmount();
            List<Map.Entry<Long, Long>> list = new ArrayList<>(shares.entrySet());
            for (int i = 0; i < list.size(); i++) {
                var en = list.get(i);
                BigDecimal share = i == list.size() - 1 ? remaining
                        : r.getAmount().multiply(BigDecimal.valueOf(en.getValue())).divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
                remaining = remaining.subtract(share);
                if (share.signum() <= 0) continue;
                String what = type.equals("ELECTRICITY") ? "Electricity" : "Water";
                String desc = what + " " + label + ", room " + roomName + ": " + r.getUnits().stripTrailingZeros().toPlainString()
                        + " units" + (list.size() > 1 ? ", your share for " + en.getValue() + " of " + totalDays + " resident-days" : "");
                paymentService.createCharges(hostelId, en.getKey(), type.equals("ELECTRICITY") ? "ELECTRICITY" : "OTHER", share, desc, dueDate);
                charges++;
            }
            r.setStatus("BILLED");
            r.setBilledAt(LocalDateTime.now());
            r.setResidentsBilled(shares.size());
            meterRepo.save(r);
            roomsBilled++;
            total = total.add(r.getAmount());
        }
        if (roomsBilled == 0) throw bad(skipped.isEmpty() ? "No unbilled readings for " + label : "Nothing billed: " + String.join("; ", skipped));
        return Map.of("rooms", roomsBilled, "charges", charges, "totalAmount", total, "skipped", skipped);
    }

    public MeterReading reading(Long id) {
        return meterRepo.findById(id).orElseThrow(() -> new AppException("Reading not found", HttpStatus.NOT_FOUND));
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    /** hostellerId → days stayed in the room during the month. */
    private Map<Long, Long> shares(Long roomId, List<BookingCheckin> bookings, YearMonth ym) {
        LocalDate from = ym.atDay(1), to = ym.atEndOfMonth();
        Map<Long, Long> days = new LinkedHashMap<>();
        for (BookingCheckin b : bookings) {
            if (!Objects.equals(b.getRoomId(), roomId) || b.getCheckinDate() == null) continue;
            LocalDate end = b.getCheckoutDate() != null ? b.getCheckoutDate().minusDays(1) : ("ACTIVE".equals(b.getStatus()) ? to : null);
            if (end == null) continue;
            LocalDate start = b.getCheckinDate().isAfter(from) ? b.getCheckinDate() : from;
            if (end.isAfter(to)) end = to;
            if (start.isAfter(end)) continue;
            days.merge(b.getHostellerId(), ChronoUnit.DAYS.between(start, end) + 1, Long::sum);
        }
        return days;
    }

    private BigDecimal lastClosing(Long roomId, String type, YearMonth before) {
        return meterRepo.findFirstByRoomIdAndMeterTypeOrderByPeriodYearDescPeriodMonthDesc(roomId, type)
                .filter(r -> YearMonth.of(r.getPeriodYear(), r.getPeriodMonth()).isBefore(before))
                .map(MeterReading::getCurrentReading).orElse(null);
    }

    private static String type(String t) {
        String s = t == null ? "" : t.toUpperCase();
        if (!TYPES.contains(s)) throw bad("Meter type must be ELECTRICITY or WATER");
        return s;
    }

    private static Number num(Object v) {
        if (v instanceof Number n) return n;
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            throw bad("A number is missing");
        }
    }

    private static BigDecimal money(Object v, String field) {
        try {
            BigDecimal b = new BigDecimal(String.valueOf(v)).setScale(2, RoundingMode.HALF_UP);
            if (b.signum() < 0 || b.compareTo(new BigDecimal("9999999999")) > 0) throw new NumberFormatException();
            return b;
        } catch (Exception e) {
            throw bad(field + " must be a number of 0 or more");
        }
    }

    private static AppException bad(String msg) {
        return new AppException(msg, HttpStatus.BAD_REQUEST);
    }
}
