package com.hostlify.module.resident.checkout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.booking.service.BookingService;
import com.hostlify.module.compliance.ResidentNoticeEvent;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.event.StaffNoticeEvent;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Resident-initiated checkout: the resident asks, staff inspect what the resident has to hand back
 * (bed, blanket, locker key …) and approve — which checks the resident out — or send it back with a note.
 */
@Service
public class CheckoutService {

    public interface Repo extends JpaRepository<CheckoutRequest, Long> {
        List<CheckoutRequest> findByHostellerIdOrderByIdDesc(Long hostellerId);
        List<CheckoutRequest> findByHostelIdOrderByIdDesc(Long hostelId);
        List<CheckoutRequest> findByHostelIdAndStatusOrderByRequestedDateAsc(Long hostelId, String status);
    }

    /** Default things to check; staff can add their own on the approval screen. */
    public static final List<String> DEFAULT_ITEMS = List.of(
            "Bed & mattress", "Pillow", "Blanket / bedsheet", "Cupboard / locker", "Locker key", "Room key",
            "Fan, lights & switches", "Bathroom fittings", "ID / access card");
    private static final Set<String> ITEM_STATUSES = Set.of("OK", "DAMAGED", "MISSING", "NA");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final Repo repo;
    private final BookingCheckinRepository bookingRepo;
    private final BookingService bookingService;
    private final HostellerProfileRepository profileRepo;
    private final RoomRepository roomRepo;
    private final BedRepository bedRepo;
    private final ApplicationEventPublisher events;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public CheckoutService(Repo repo, BookingCheckinRepository bookingRepo, BookingService bookingService,
                           HostellerProfileRepository profileRepo, RoomRepository roomRepo, BedRepository bedRepo,
                           ApplicationEventPublisher events) {
        this.repo = repo;
        this.bookingRepo = bookingRepo;
        this.bookingService = bookingService;
        this.profileRepo = profileRepo;
        this.roomRepo = roomRepo;
        this.bedRepo = bedRepo;
        this.events = events;
    }

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.hostlify.module.compliance.settlement.SettlementService settlements;
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.hostlify.module.hostel.policy.PolicyService policies;
    @org.springframework.beans.factory.annotation.Autowired
    private com.hostlify.module.payment.repository.PaymentDueRepository dueRepo;

    // ═══ Resident ══════════════════════════════════════════════════════════

    /**
     * "Give notice" calculator: last day, short-notice and early-exit amounts (from the terms the resident booked with),
     * rent still to be billed up to the last day, open dues and the deposit — so they know roughly what they'll get back.
     * An estimate: the hostel's deposit settlement is the final word.
     */
    public Map<String, Object> estimate(Long hostellerId, LocalDate lastDay) {
        BookingCheckin b = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .orElseThrow(() -> new AppException("You don't have an active stay", HttpStatus.BAD_REQUEST));
        LocalDate today = DateUtils.todayKolkata();
        Map<String, Object> terms = policies.parseTerms(b.getTermsJson());
        boolean lockedTerms = terms != null;
        if (terms == null) terms = policies.publicView(b.getHostelId());
        boolean shortStay = "SHORT".equals(b.getRentFrequency());
        int noticeDays = shortStay ? 0 : num(terms.get("noticeDays")).intValue();
        int lockInMonths = shortStay ? 0 : num(terms.get("lockInMonths")).intValue();
        java.math.BigDecimal exitFee = shortStay ? java.math.BigDecimal.ZERO : num(terms.get("earlyExitFee"));
        int refundDays = terms.get("depositRefundDays") == null ? policies.get(b.getHostelId()).getDepositRefundDays() : num(terms.get("depositRefundDays")).intValue();
        if (lastDay == null) lastDay = shortStay && b.getExpectedCheckoutDate() != null ? b.getExpectedCheckoutDate() : today.plusDays(noticeDays);
        if (lastDay.isBefore(today)) lastDay = today;

        java.math.BigDecimal rent = b.getRentAmount() == null ? java.math.BigDecimal.ZERO : b.getRentAmount();
        java.math.BigDecimal perDay = rent.divide(java.math.BigDecimal.valueOf(30), 2, java.math.RoundingMode.HALF_UP);
        LocalDate fullNotice = today.plusDays(noticeDays);
        int shortDays = lastDay.isBefore(fullNotice) ? (int) java.time.temporal.ChronoUnit.DAYS.between(lastDay, fullNotice) : 0;
        java.math.BigDecimal shortCharge = shortStay ? java.math.BigDecimal.ZERO : perDay.multiply(java.math.BigDecimal.valueOf(shortDays));
        LocalDate lockInEnds = lockInMonths > 0 ? b.getCheckinDate().plusMonths(lockInMonths) : null;
        boolean inLockIn = lockInEnds != null && lastDay.isBefore(lockInEnds);
        java.math.BigDecimal exit = inLockIn ? exitFee : java.math.BigDecimal.ZERO;
        java.math.BigDecimal unbilled = shortStay ? java.math.BigDecimal.ZERO : unbilledRent(b, rent, lastDay);
        java.math.BigDecimal open = settlements.openDuesTotal(b);
        java.math.BigDecimal deposit = settlements.depositHeld(b);
        java.math.BigDecimal net = deposit.subtract(open).subtract(unbilled).subtract(shortCharge).subtract(exit);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lastDay", lastDay);
        m.put("noticeDays", noticeDays);
        m.put("fullNoticeDate", fullNotice);
        m.put("shortNoticeDays", shortDays);
        m.put("shortNoticeCharge", shortCharge);
        m.put("lockInMonths", lockInMonths);
        m.put("lockInEnds", lockInEnds);
        m.put("inLockIn", inLockIn);
        m.put("earlyExitFee", exit);
        m.put("monthlyRent", rent);
        m.put("rentToLastDay", unbilled);
        m.put("openDues", open);
        m.put("depositHeld", deposit);
        m.put("estimatedRefund", net.max(java.math.BigDecimal.ZERO));
        m.put("estimatedBalance", net.signum() < 0 ? net.negate() : java.math.BigDecimal.ZERO);
        m.put("refundDays", refundDays);
        m.put("refundBy", lastDay.plusDays(refundDays));
        m.put("termsLocked", lockedTerms);
        m.put("shortStay", shortStay);
        return m;
    }

    /** Rent for the days from the first month not billed yet up to the last day (pro-rata in the last month). */
    private java.math.BigDecimal unbilledRent(BookingCheckin b, java.math.BigDecimal rent, LocalDate lastDay) {
        LocalDate cursor = b.getCheckinDate();
        for (var d : dueRepo.findByHostellerIdOrderByDueDateDesc(b.getHostellerId())) {
            if (!"RENT".equals(d.getDueType()) || !Objects.equals(d.getBookingId(), b.getId())) continue;
            LocalDate after = LocalDate.of(d.getPeriodYear(), d.getPeriodMonth(), 1).plusMonths(1);
            if (after.isAfter(cursor)) cursor = after;
        }
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        while (!cursor.isAfter(lastDay)) {
            LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            LocalDate end = lastDay.isBefore(monthEnd) ? lastDay : monthEnd;
            long days = java.time.temporal.ChronoUnit.DAYS.between(cursor, end) + 1;
            total = total.add(rent.multiply(java.math.BigDecimal.valueOf(days))
                    .divide(java.math.BigDecimal.valueOf(cursor.lengthOfMonth()), 2, java.math.RoundingMode.HALF_UP));
            cursor = monthEnd.plusDays(1);
        }
        return total;
    }

    private static java.math.BigDecimal num(Object o) {
        if (o == null) return java.math.BigDecimal.ZERO;
        try { return new java.math.BigDecimal(String.valueOf(o)); } catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }

    public Map<String, Object> mine(Long hostellerId) {
        Map<String, Object> m = new LinkedHashMap<>();
        BookingCheckin b = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE").orElse(null);
        m.put("activeStay", b != null);
        if (b != null) {
            m.put("bookingId", b.getId());
            m.put("checkinDate", b.getCheckinDate());
            m.put("expectedCheckoutDate", b.getExpectedCheckoutDate());
        }
        m.put("requests", repo.findByHostellerIdOrderByIdDesc(hostellerId).stream().limit(10).map(this::enrich).toList());
        return m;
    }

    @Transactional
    public CheckoutRequest request(Long hostellerId, LocalDate date, String reason) {
        BookingCheckin b = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .orElseThrow(() -> new AppException("You don't have an active stay to check out from", HttpStatus.BAD_REQUEST));
        LocalDate today = DateUtils.todayKolkata();
        if (date == null) date = today;
        if (date.isBefore(today) || date.isAfter(today.plusDays(90))) {
            throw new AppException("Choose a check-out date between today and the next 90 days", HttpStatus.BAD_REQUEST);
        }
        boolean open = repo.findByHostellerIdOrderByIdDesc(hostellerId).stream()
                .anyMatch(r -> "REQUESTED".equals(r.getStatus()) && Objects.equals(r.getBookingId(), b.getId()));
        if (open) throw new AppException("You already have a check-out request waiting. Cancel it to change the date.", HttpStatus.CONFLICT);

        CheckoutRequest r = new CheckoutRequest();
        r.setHostelId(b.getHostelId());
        r.setHostellerId(hostellerId);
        r.setBookingId(b.getId());
        r.setRequestedDate(date);
        r.setReason(reason == null || reason.isBlank() ? null : reason.trim().substring(0, Math.min(500, reason.trim().length())));
        try {
            r.setEstimateJson(mapper.writeValueAsString(estimate(hostellerId, date)));
        } catch (Exception e) {
            // the estimate is a convenience; the request still goes through
        }
        CheckoutRequest saved = repo.save(r);

        String who = name(profileRepo.findById(hostellerId).orElse(null));
        events.publishEvent(new StaffNoticeEvent(this, b.getHostelId(), "CHECKOUT_REQUESTED",
                "Check-out request: " + who,
                who + " wants to check out on " + date.format(D) + (saved.getReason() != null ? ". Reason: " + saved.getReason() : "")
                        + ". Inspect the room and approve under Residents → Check-outs.",
                "/admin/checkouts", true, false));
        return enrich(saved);
    }

    @Transactional
    public CheckoutRequest cancel(Long hostellerId, Long id) {
        CheckoutRequest r = get(id);
        if (!Objects.equals(r.getHostellerId(), hostellerId)) throw new AppException("Request not found", HttpStatus.NOT_FOUND);
        if (!"REQUESTED".equals(r.getStatus())) throw new AppException("This request was already " + r.getStatus().toLowerCase(), HttpStatus.CONFLICT);
        r.setStatus("CANCELLED");
        return enrich(repo.save(r));
    }

    // ═══ Staff ═════════════════════════════════════════════════════════════

    public List<CheckoutRequest> forHostel(Long hostelId, String status) {
        List<CheckoutRequest> list = status == null || status.isBlank()
                ? repo.findByHostelIdOrderByIdDesc(hostelId).stream().limit(200).toList()
                : repo.findByHostelIdAndStatusOrderByRequestedDateAsc(hostelId, status.toUpperCase());
        return list.stream().map(this::enrich).toList();
    }

    public CheckoutRequest get(Long id) {
        return repo.findById(id).orElseThrow(() -> new AppException("Check-out request not found", HttpStatus.NOT_FOUND));
    }

    /** Saves the inspection and checks the resident out. */
    @Transactional
    public CheckoutRequest approve(Long id, Long staffUserId, List<Map<String, Object>> items, String note) {
        CheckoutRequest r = get(id);
        if (!"REQUESTED".equals(r.getStatus())) throw new AppException("This request was already " + r.getStatus().toLowerCase(), HttpStatus.CONFLICT);
        List<Map<String, Object>> clean = cleanItems(items);
        long problems = clean.stream().filter(i -> !"OK".equals(i.get("status")) && !"NA".equals(i.get("status"))).count();

        BookingCheckin b = bookingRepo.findById(r.getBookingId()).orElse(null);
        if (b != null && "ACTIVE".equals(b.getStatus())) {
            bookingService.checkout(b.getId(), "Resident request" + (r.getReason() != null ? ": " + r.getReason() : ""));
        }
        r.setItemsJson(write(clean));
        r.setStaffNote(trim(note));
        r.setStatus("APPROVED");
        r.setDecidedBy(staffUserId);
        r.setDecidedAt(LocalDateTime.now());
        CheckoutRequest saved = repo.save(r);

        String body = "Your check-out has been approved. Thank you for staying with us!"
                + (problems > 0 ? "\n" + problems + " item(s) were noted during the room check; the hostel will settle any deduction from your deposit." : "")
                + (saved.getStaffNote() != null ? "\nNote: " + saved.getStaffNote() : "")
                + "\nPlease take a minute to rate your stay — it helps the next resident.";
        events.publishEvent(new ResidentNoticeEvent(this, r.getHostellerId(), "CHECKOUT_APPROVED",
                "Check-out approved", body, "/app/checkout"));
        return enrich(saved);
    }

    @Transactional
    public CheckoutRequest reject(Long id, Long staffUserId, String note) {
        CheckoutRequest r = get(id);
        if (!"REQUESTED".equals(r.getStatus())) throw new AppException("This request was already " + r.getStatus().toLowerCase(), HttpStatus.CONFLICT);
        if (note == null || note.isBlank()) throw new AppException("Tell the resident what is pending (e.g. return the locker key)", HttpStatus.BAD_REQUEST);
        r.setStatus("REJECTED");
        r.setStaffNote(trim(note));
        r.setDecidedBy(staffUserId);
        r.setDecidedAt(LocalDateTime.now());
        CheckoutRequest saved = repo.save(r);
        events.publishEvent(new ResidentNoticeEvent(this, r.getHostellerId(), "CHECKOUT_SENT_BACK",
                "Check-out: action needed", "Your check-out request needs attention: " + saved.getStaffNote()
                + "\nSort it out with the hostel, then request check-out again.", "/app/checkout"));
        return enrich(saved);
    }

    // ═══ helpers ═══════════════════════════════════════════════════════════

    private CheckoutRequest enrich(CheckoutRequest r) {
        r.setItems(read(r.getItemsJson()));
        r.setEstimate(policies.parseTerms(r.getEstimateJson()));
        profileRepo.findById(r.getHostellerId()).ifPresent(p -> {
            r.setResidentName(name(p));
            r.setResidentMobile(p.getMobile());
        });
        bookingRepo.findById(r.getBookingId()).ifPresent(b -> {
            r.setCheckinDate(b.getCheckinDate());
            if (b.getRoomId() != null) roomRepo.findById(b.getRoomId()).ifPresent(rm -> r.setRoomName(rm.getRoomName()));
            if (b.getBedId() != null) bedRepo.findById(b.getBedId()).ifPresent(bd -> r.setBedName(bd.getBedName()));
        });
        return r;
    }

    private static List<Map<String, Object>> cleanItems(List<Map<String, Object>> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (items == null) return out;
        for (Map<String, Object> i : items) {
            String label = trimTo(i.get("label"), 80);
            if (label == null) continue;
            String st = String.valueOf(i.getOrDefault("status", "OK")).toUpperCase();
            if (!ITEM_STATUSES.contains(st)) st = "OK";
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", label);
            m.put("status", st);
            String note = trimTo(i.get("note"), 200);
            if (note != null) m.put("note", note);
            out.add(m);
            if (out.size() >= 40) break;
        }
        return out;
    }

    private List<Map<String, Object>> read(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return mapper.readValue(json, new TypeReference<>() {}); } catch (Exception e) { return List.of(); }
    }

    private String write(List<Map<String, Object>> items) {
        try { return mapper.writeValueAsString(items); } catch (Exception e) { return "[]"; }
    }

    private static String name(HostellerProfile p) {
        return p == null ? "A resident" : (p.getFirstName() + " " + Objects.toString(p.getLastName(), "")).trim();
    }

    private static String trim(String s) {
        return trimTo(s, 500);
    }

    private static String trimTo(Object o, int n) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        return s.length() > n ? s.substring(0, n) : s;
    }
}
