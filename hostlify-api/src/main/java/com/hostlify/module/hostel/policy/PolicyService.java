package com.hostlify.module.hostel.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostlify.module.booking.service.ShortStayService;
import com.hostlify.module.compliance.agreement.AgreementService;
import com.hostlify.module.compliance.agreement.AgreementTemplate;
import com.hostlify.module.hostel.entity.Bed;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.entity.HostelPricing;
import com.hostlify.module.hostel.entity.Room;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.hostel.repository.HostelPricingRepository;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.payment.service.PaymentSettingsService;
import com.hostlify.module.resident.mess.MessRepos;
import com.hostlify.module.resident.mess.MessSettings;
import com.hostlify.shared.exception.AppException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * The hostel's fine print in one place, so residents see the full cost of a stay and the rules before booking:
 * rent + deposit + one-time fees, notice period, lock-in, early-exit fee, deposit refund time, house rules, safety.
 * A copy is stored with each booking request / booking so it can't change after the resident agreed to it.
 */
@Service
public class PolicyService {

    public interface Repo extends JpaRepository<HostelPolicy, Long> {}

    public static final List<String> GUEST_POLICIES = List.of("NOT_ALLOWED", "DAYTIME", "OVERNIGHT_WITH_APPROVAL");
    public static final List<String> SAFETY_FEATURES = List.of(
            "CCTV", "SECURITY_GUARD", "BIOMETRIC_ENTRY", "WOMEN_WARDEN", "FIRE_EXTINGUISHERS", "FIRE_NOC",
            "EMERGENCY_EXIT", "FIRST_AID", "STAFF_VERIFIED", "WELL_LIT_ENTRANCE");
    public static final List<String> WEEKDAYS = List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN");

    private final Repo repo;
    private final HostelRepository hostelRepo;
    private final RoomRepository roomRepo;
    private final BedRepository bedRepo;
    private final HostelPricingRepository pricingRepo;
    private final ShortStayService shortStays;
    private final AgreementService agreements;
    private final PaymentSettingsService paymentSettings;
    private final MessRepos.Settings messSettings;
    private final ObjectMapper mapper = new ObjectMapper();

    public PolicyService(Repo repo, HostelRepository hostelRepo, RoomRepository roomRepo, BedRepository bedRepo,
                         HostelPricingRepository pricingRepo, ShortStayService shortStays, AgreementService agreements,
                         PaymentSettingsService paymentSettings, MessRepos.Settings messSettings) {
        this.repo = repo;
        this.hostelRepo = hostelRepo;
        this.roomRepo = roomRepo;
        this.bedRepo = bedRepo;
        this.pricingRepo = pricingRepo;
        this.shortStays = shortStays;
        this.agreements = agreements;
        this.paymentSettings = paymentSettings;
        this.messSettings = messSettings;
    }

    public HostelPolicy get(Long hostelId) {
        return repo.findById(hostelId).orElseGet(() -> {
            HostelPolicy p = new HostelPolicy();
            p.setHostelId(hostelId);
            return p;
        });
    }

    public int noticeDays(Long hostelId) {
        return agreements.template(hostelId).getNoticeDays();
    }

    /** Staff view: the stored settings + notice days (kept on the agreement template) + the option lists. */
    public Map<String, Object> staffView(Long hostelId) {
        Map<String, Object> m = publicView(hostelId);
        m.put("guestPolicies", GUEST_POLICIES);
        m.put("safetyOptions", SAFETY_FEATURES);
        return m;
    }

    @Transactional
    public Map<String, Object> save(Long hostelId, Map<String, Object> b, Long userId) {
        HostelPolicy p = get(hostelId);
        if (b.containsKey("lockInMonths")) p.setLockInMonths(intIn(b.get("lockInMonths"), 0, 24, "Lock-in must be 0 to 24 months"));
        if (b.containsKey("earlyExitFee")) p.setEarlyExitFee(money(b.get("earlyExitFee"), "Early-exit fee"));
        if (b.containsKey("admissionFee")) p.setAdmissionFee(money(b.get("admissionFee"), "Admission fee"));
        if (b.containsKey("depositRefundDays")) p.setDepositRefundDays(intIn(b.get("depositRefundDays"), 1, 60, "Refund time must be 1 to 60 days"));
        if (b.containsKey("otherCharges")) p.setOtherCharges(text(b.get("otherCharges"), 1000, "Other charges"));
        if (b.containsKey("curfewTime")) {
            String c = text(b.get("curfewTime"), 5, "Curfew");
            if (c != null) {
                try { c = LocalTime.parse(c).toString(); } catch (Exception e) { throw bad("Curfew must be a time like 22:30"); }
            }
            p.setCurfewTime(c);
        }
        if (b.containsKey("guestPolicy")) {
            String g = String.valueOf(b.get("guestPolicy")).toUpperCase();
            if (!GUEST_POLICIES.contains(g)) throw bad("Unknown guest policy");
            p.setGuestPolicy(g);
        }
        if (b.containsKey("houseRules")) {
            Object r = b.get("houseRules");
            String rules = r instanceof List<?> l ? String.join("\n", l.stream().map(String::valueOf).toList()) : r == null ? null : String.valueOf(r);
            List<String> lines = lines(rules);
            if (lines.size() > 30) throw bad("Up to 30 house rules");
            if (lines.stream().anyMatch(x -> x.length() > 200)) throw bad("Keep each rule under 200 characters");
            p.setHouseRules(lines.isEmpty() ? null : String.join("\n", lines));
        }
        if (b.containsKey("lateEntryRequests")) p.setLateEntryRequests(Boolean.TRUE.equals(b.get("lateEntryRequests")));
        if (b.containsKey("roomCleaningDays")) p.setRoomCleaningDays(codes(b.get("roomCleaningDays"), WEEKDAYS, "cleaning day"));
        if (b.containsKey("safetyFeatures")) p.setSafetyFeatures(codes(b.get("safetyFeatures"), SAFETY_FEATURES, "safety feature"));
        if (b.containsKey("noticeDays")) {
            int notice = intIn(b.get("noticeDays"), 0, 90, "Notice period must be 0 to 90 days");
            AgreementTemplate t = agreements.template(hostelId);
            if (t.getNoticeDays() != notice) agreements.saveTemplate(hostelId, t.getTitle(), t.getBody(), notice, userId);
        }
        p.setUpdatedBy(userId);
        p.setUpdatedAt(LocalDateTime.now());
        repo.save(p);
        return staffView(hostelId);
    }

    /** What residents see on the hostel page and in the booking wizard. */
    public Map<String, Object> publicView(Long hostelId) {
        HostelPolicy p = get(hostelId);
        Hostel h = hostelRepo.findById(hostelId).orElseThrow(() -> new AppException("Hostel not found", HttpStatus.NOT_FOUND));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("noticeDays", noticeDays(hostelId));
        m.put("lockInMonths", p.getLockInMonths());
        m.put("earlyExitFee", p.getEarlyExitFee());
        m.put("admissionFee", p.getAdmissionFee());
        m.put("depositRefundDays", p.getDepositRefundDays());
        m.put("otherCharges", p.getOtherCharges());
        m.put("utilitiesIncluded", paymentSettings.getOrDefault(hostelId).isUtilitiesIncluded());
        MessSettings ms = messSettings.findByHostelId(hostelId).orElse(null);
        if (ms != null && ms.isEnabled()) {
            m.put("mess", Map.of("breakfastRate", ms.getBreakfastRate(), "lunchRate", ms.getLunchRate(), "dinnerRate", ms.getDinnerRate()));
        }
        m.put("curfewTime", p.getCurfewTime());
        m.put("guestPolicy", p.getGuestPolicy());
        m.put("houseRules", lines(p.getHouseRules()));
        m.put("lateEntryRequests", p.isLateEntryRequests());
        m.put("roomCleaningDays", split(p.getRoomCleaningDays()));
        m.put("safetyFeatures", split(p.getSafetyFeatures()));
        m.put("costSheets", costSheets(h));
        return m;
    }

    /** Monthly cost per room type: rent, deposit, one-time fees and what's due before moving in. */
    private List<Map<String, Object>> costSheets(Hostel h) {
        HostelPolicy p = get(h.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (HostelPricing pr : pricingRepo.findByHostelIdAndIsActiveTrue(h.getId())) {
            if (pr.getBaseRent() == null || pr.getBaseRent().signum() <= 0) continue;
            out.add(costSheet(pr.getRoomType(), pr.getBaseRent(), pr.getDepositMonths(), pr.getLateFee(), p));
        }
        if (out.isEmpty() && h.getDefaultRent() != null && h.getDefaultRent().signum() > 0) {
            out.add(costSheet(null, h.getDefaultRent(), null, null, p));
        }
        return out;
    }

    private Map<String, Object> costSheet(String roomType, BigDecimal rent, Integer depositMonths, BigDecimal lateFee, HostelPolicy p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("roomType", roomType);
        m.put("monthlyRent", rent);
        m.put("depositMonths", depositMonths);
        BigDecimal deposit = depositMonths == null ? null : rent.multiply(BigDecimal.valueOf(depositMonths));
        m.put("deposit", deposit);
        m.put("admissionFee", p.getAdmissionFee());
        m.put("lateFee", lateFee == null ? BigDecimal.ZERO : lateFee);
        m.put("moveInTotal", rent.add(deposit == null ? BigDecimal.ZERO : deposit).add(p.getAdmissionFee()));
        return m;
    }

    /** Rent the resident was shown for this room: the owner's price for the room type, else the room's, else the hostel default. */
    public BigDecimal advertisedRent(Room room, Hostel hostel) {
        HostelPricing pr = shortStays.pricingFor(hostel.getId(), room.getRoomType());
        if (pr != null && pr.isActive() && pr.getBaseRent() != null && pr.getBaseRent().signum() > 0) return pr.getBaseRent();
        if (room.getRentAmount() != null && room.getRentAmount().signum() > 0) return room.getRentAmount();
        return hostel.getDefaultRent();
    }

    /** The terms the resident sees when booking this bed, frozen as JSON. */
    public String termsSnapshot(Long hostelId, Long bedId) {
        Map<String, Object> view = publicView(hostelId);
        view.remove("costSheets");
        Bed bed = bedRepo.findById(bedId).orElse(null);
        Room room = bed == null ? null : roomRepo.findById(bed.getRoomId()).orElse(null);
        Hostel h = hostelRepo.findById(hostelId).orElseThrow();
        if (room != null) {
            HostelPricing pr = shortStays.pricingFor(hostelId, room.getRoomType());
            BigDecimal rent = advertisedRent(room, h);
            view.put("costSheet", costSheet(room.getRoomType(), rent, pr == null ? null : pr.getDepositMonths(),
                    pr == null ? null : pr.getLateFee(), get(hostelId)));
        }
        view.put("capturedAt", LocalDateTime.now().toString());
        try { return mapper.writeValueAsString(view); } catch (Exception e) { return null; }
    }

    public Map<String, Object> parseTerms(String json) {
        if (json == null || json.isBlank()) return null;
        try { return mapper.readValue(json, new TypeReference<Map<String, Object>>() {}); } catch (Exception e) { return null; }
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    static List<String> lines(String s) {
        if (s == null) return List.of();
        return Arrays.stream(s.split("\\r?\\n")).map(String::trim).map(x -> x.replaceFirst("^[-•*]\\s*", "")).filter(x -> !x.isEmpty()).toList();
    }

    public static List<String> split(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private static String codes(Object v, List<String> allowed, String what) {
        List<String> in = v instanceof List<?> l ? l.stream().map(String::valueOf).toList() : split(v == null ? null : String.valueOf(v));
        List<String> out = new ArrayList<>();
        for (String c : in) {
            String u = c.trim().toUpperCase();
            if (!allowed.contains(u)) throw bad("Unknown " + what + ": " + c);
            if (!out.contains(u)) out.add(u);
        }
        out.sort(Comparator.comparingInt(allowed::indexOf));
        return out.isEmpty() ? null : String.join(",", out);
    }

    private static int intIn(Object v, int min, int max, String msg) {
        int n;
        try { n = new BigDecimal(String.valueOf(v)).intValueExact(); } catch (Exception e) { throw bad(msg); }
        if (n < min || n > max) throw bad(msg);
        return n;
    }

    private static BigDecimal money(Object v, String what) {
        if (v == null || String.valueOf(v).isBlank()) return BigDecimal.ZERO;
        BigDecimal d;
        try { d = new BigDecimal(String.valueOf(v)).setScale(2, RoundingMode.HALF_UP); } catch (Exception e) { throw bad(what + " must be a number"); }
        if (d.signum() < 0 || d.compareTo(new BigDecimal("1000000")) > 0) throw bad(what + " must be between 0 and 10,00,000");
        return d;
    }

    private static String text(Object v, int max, String what) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        if (s.length() > max) throw bad(what + " must be " + max + " characters or less");
        return s;
    }

    private static AppException bad(String m) { return new AppException(m, HttpStatus.BAD_REQUEST); }
}
