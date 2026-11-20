package com.hostlify.module.resident.safety;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.compliance.ResidentNoticeEvent;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.policy.HostelPolicy;
import com.hostlify.module.hostel.policy.PolicyService;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerEmergencyContactRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.event.StaffNoticeEvent;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Resident safety and wellbeing: SOS with location, "I'm not feeling well", and late-entry requests after curfew.
 * Everything here alerts staff (supervisors included) by push; SOS also emails the owner.
 * Emergency contacts get the SOS through the resident's own WhatsApp / phone (free), from the app.
 */
@Service
public class SafetyService {

    public static final List<String> NEEDS = List.of("FOOD_IN_ROOM", "MEDICINE", "DOCTOR", "CHECK_ON_ME");
    private static final Set<String> LIVE_SOS = Set.of("OPEN", "ACKNOWLEDGED");
    private static final DateTimeFormatter T = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("d MMM");

    private final SafetyRepos.Sos sosRepo;
    private final SafetyRepos.Wellness wellnessRepo;
    private final SafetyRepos.LateEntries lateRepo;
    private final BookingCheckinRepository bookingRepo;
    private final HostellerProfileRepository profileRepo;
    private final HostellerEmergencyContactRepository contactRepo;
    private final HostelRepository hostelRepo;
    private final RoomRepository roomRepo;
    private final PolicyService policies;
    private final ApplicationEventPublisher events;

    public SafetyService(SafetyRepos.Sos sosRepo, SafetyRepos.Wellness wellnessRepo, SafetyRepos.LateEntries lateRepo,
                         BookingCheckinRepository bookingRepo, HostellerProfileRepository profileRepo,
                         HostellerEmergencyContactRepository contactRepo, HostelRepository hostelRepo, RoomRepository roomRepo,
                         PolicyService policies, ApplicationEventPublisher events) {
        this.sosRepo = sosRepo;
        this.wellnessRepo = wellnessRepo;
        this.lateRepo = lateRepo;
        this.bookingRepo = bookingRepo;
        this.profileRepo = profileRepo;
        this.contactRepo = contactRepo;
        this.hostelRepo = hostelRepo;
        this.roomRepo = roomRepo;
        this.policies = policies;
        this.events = events;
    }

    // ═══ Resident hub ══════════════════════════════════════════════════════

    /** Everything the resident's Safety & help page needs. */
    public Map<String, Object> mine(Long hostellerId) {
        Map<String, Object> m = new LinkedHashMap<>();
        HostellerProfile p = profile(hostellerId);
        BookingCheckin b = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE").orElse(null);
        m.put("activeStay", b != null);
        m.put("bloodGroup", p.getBloodGroup());
        m.put("medicalNotes", p.getMedicalNotes());
        m.put("emergencyContact", contactRepo.findFirstByHostellerId(hostellerId).map(c -> {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("name", c.getName());
            e.put("relationship", c.getRelationship());
            e.put("mobile", c.getMobile());
            return e;
        }).orElse(null));
        if (b == null) return m;
        Hostel h = hostelRepo.findById(b.getHostelId()).orElseThrow();
        HostelPolicy pol = policies.get(h.getId());
        Map<String, Object> hostel = new LinkedHashMap<>();
        hostel.put("id", h.getId());
        hostel.put("name", h.getName());
        hostel.put("phone", h.getContactPhone());
        hostel.put("latitude", h.getLatitude());
        hostel.put("longitude", h.getLongitude());
        hostel.put("safetyFeatures", PolicyService.split(pol.getSafetyFeatures()));
        m.put("hostel", hostel);
        m.put("curfewTime", pol.getCurfewTime());
        m.put("lateEntryEnabled", pol.getCurfewTime() != null && pol.isLateEntryRequests());
        m.put("sos", sosRepo.findFirstByHostellerIdOrderByIdDesc(hostellerId)
                .filter(s -> LIVE_SOS.contains(s.getStatus()) || s.getCreatedAt().isAfter(LocalDateTime.now().minusHours(6)))
                .map(this::sosView).orElse(null));
        m.put("unwell", wellnessRepo.findFirstByHostellerIdAndStatusOrderByIdDesc(hostellerId, "OPEN").map(this::wellnessView).orElse(null));
        m.put("lateEntries", lateRepo.findTop20ByHostellerIdOrderByEntryDateDescIdDesc(hostellerId));
        m.put("needOptions", NEEDS);
        return m;
    }

    // ═══ SOS ═══════════════════════════════════════════════════════════════

    @Transactional
    public SosAlert sos(Long hostellerId, BigDecimal lat, BigDecimal lng, Integer accuracy, String message) {
        BookingCheckin b = activeStay(hostellerId);
        if (lat != null && (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lng == null || lng.abs().compareTo(BigDecimal.valueOf(180)) > 0)) {
            lat = null;
            lng = null;
        }
        // Pressed again while help is on the way: update the location, don't alert everyone twice within 10 minutes
        SosAlert live = sosRepo.findFirstByHostellerIdOrderByIdDesc(hostellerId)
                .filter(s -> LIVE_SOS.contains(s.getStatus())).orElse(null);
        boolean realert = live == null || live.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(10));
        SosAlert s = live != null ? live : new SosAlert();
        s.setHostelId(b.getHostelId());
        s.setHostellerId(hostellerId);
        if (lat != null) {
            s.setLatitude(lat.setScale(6, RoundingMode.HALF_UP));
            s.setLongitude(lng.setScale(6, RoundingMode.HALF_UP));
            s.setAccuracyM(accuracy == null ? null : Math.max(0, Math.min(accuracy, 100000)));
        }
        String msg = trim(message, 300);
        if (msg != null) s.setMessage(msg);
        SosAlert saved = sosRepo.save(s);

        if (realert) {
            HostellerProfile p = profile(hostellerId);
            String room = roomRepo.findById(b.getRoomId()).map(r -> r.getRoomName()).orElse("?");
            String where = saved.getLatitude() != null ? " Location: " + mapsUrl(saved) : " (location not shared)";
            events.publishEvent(new StaffNoticeEvent(this, b.getHostelId(), "SOS",
                    "SOS: " + name(p) + " (room " + room + ")",
                    name(p) + " pressed SOS at " + LocalTime.now(DateUtils.KOLKATA).format(T) + "." + where
                            + (p.getMobile() != null ? " Call: " + p.getMobile() : "") + (msg != null ? ". \"" + msg + "\"" : ""),
                    "/admin/safety", true, true));
        }
        return sosView(saved);
    }

    @Transactional
    public SosAlert cancelSos(Long hostellerId, Long id) {
        SosAlert s = sosRepo.findById(id).filter(x -> x.getHostellerId().equals(hostellerId))
                .orElseThrow(() -> new AppException("Alert not found", HttpStatus.NOT_FOUND));
        if (!LIVE_SOS.contains(s.getStatus())) return sosView(s);
        s.setStatus("CANCELLED");
        s.setResolvedAt(LocalDateTime.now());
        s.setResolutionNote("Resident marked themselves safe");
        SosAlert saved = sosRepo.save(s);
        events.publishEvent(new StaffNoticeEvent(this, s.getHostelId(), "SOS_SAFE",
                name(profile(hostellerId)) + " is safe", name(profile(hostellerId)) + " cancelled their SOS and marked themselves safe.",
                "/admin/safety", true, false));
        return sosView(saved);
    }

    public List<SosAlert> sosForHostel(Long hostelId, boolean liveOnly) {
        List<SosAlert> list = liveOnly ? sosRepo.findByHostelIdAndStatusInOrderByIdDesc(hostelId, LIVE_SOS)
                : sosRepo.findTop50ByHostelIdOrderByIdDesc(hostelId);
        return list.stream().map(this::sosView).toList();
    }

    public SosAlert getSos(Long id) {
        return sosRepo.findById(id).orElseThrow(() -> new AppException("Alert not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public SosAlert acknowledge(Long id, Long staffUserId) {
        SosAlert s = getSos(id);
        if (!"OPEN".equals(s.getStatus())) return sosView(s);
        s.setStatus("ACKNOWLEDGED");
        s.setAcknowledgedBy(staffUserId);
        s.setAcknowledgedAt(LocalDateTime.now());
        SosAlert saved = sosRepo.save(s);
        events.publishEvent(new ResidentNoticeEvent(this, s.getHostellerId(), "SOS_ACK",
                "The hostel has seen your SOS", "Hostel staff have seen your SOS and are responding. Keep your phone with you.", "/app/safety"));
        return sosView(saved);
    }

    @Transactional
    public SosAlert resolve(Long id, Long staffUserId, String note) {
        SosAlert s = getSos(id);
        if (!LIVE_SOS.contains(s.getStatus())) throw new AppException("This alert is already closed", HttpStatus.CONFLICT);
        String n = trim(note, 500);
        if (n == null) throw new AppException("Note what happened before closing the alert", HttpStatus.BAD_REQUEST);
        if (s.getAcknowledgedAt() == null) {
            s.setAcknowledgedBy(staffUserId);
            s.setAcknowledgedAt(LocalDateTime.now());
        }
        s.setStatus("RESOLVED");
        s.setResolvedBy(staffUserId);
        s.setResolvedAt(LocalDateTime.now());
        s.setResolutionNote(n);
        return sosView(sosRepo.save(s));
    }

    // ═══ Not feeling well ══════════════════════════════════════════════════

    @Transactional
    public WellnessFlag flagUnwell(Long hostellerId, String note, List<String> needs) {
        BookingCheckin b = activeStay(hostellerId);
        List<String> clean = new ArrayList<>();
        if (needs != null) for (String n : needs) {
            String u = String.valueOf(n).toUpperCase();
            if (!NEEDS.contains(u)) throw new AppException("Unknown option: " + n, HttpStatus.BAD_REQUEST);
            if (!clean.contains(u)) clean.add(u);
        }
        WellnessFlag f = wellnessRepo.findFirstByHostellerIdAndStatusOrderByIdDesc(hostellerId, "OPEN").orElse(null);
        boolean isNew = f == null || f.getCreatedAt().isBefore(LocalDateTime.now().minusDays(3));
        if (f != null && isNew) { // stale: close it and start again
            f.setStatus("CLEARED");
            f.setClearedAt(LocalDateTime.now());
            wellnessRepo.save(f);
        }
        if (isNew) {
            f = new WellnessFlag();
            f.setHostelId(b.getHostelId());
            f.setHostellerId(hostellerId);
        }
        f.setNote(trim(note, 300));
        f.setNeeds(clean.isEmpty() ? null : String.join(",", clean));
        WellnessFlag saved = wellnessRepo.save(f);
        HostellerProfile p = profile(hostellerId);
        String room = roomRepo.findById(b.getRoomId()).map(r -> r.getRoomName()).orElse("?");
        events.publishEvent(new StaffNoticeEvent(this, b.getHostelId(), "RESIDENT_UNWELL",
                name(p) + " isn't feeling well (room " + room + ")",
                name(p) + (clean.isEmpty() ? " isn't feeling well" : " needs: " + String.join(", ", clean.stream().map(SafetyService::needLabel).toList()))
                        + (saved.getNote() != null ? ". \"" + saved.getNote() + "\"" : "") + ". Please check on them.",
                "/admin/safety", true, false));
        return wellnessView(saved);
    }

    @Transactional
    public void clearUnwell(Long hostellerId) {
        wellnessRepo.findFirstByHostellerIdAndStatusOrderByIdDesc(hostellerId, "OPEN").ifPresent(f -> {
            f.setStatus("CLEARED");
            f.setClearedAt(LocalDateTime.now());
            wellnessRepo.save(f);
        });
    }

    /** Open flags from the last 3 days (older ones are treated as over). */
    public List<WellnessFlag> unwellForHostel(Long hostelId) {
        return wellnessRepo.findByHostelIdAndStatusAndCreatedAtAfterOrderByIdDesc(hostelId, "OPEN", LocalDateTime.now().minusDays(3))
                .stream().map(this::wellnessView).toList();
    }

    public WellnessFlag getFlag(Long id) {
        return wellnessRepo.findById(id).orElseThrow(() -> new AppException("Not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public WellnessFlag checkOn(Long id, Long staffUserId, String note) {
        WellnessFlag f = getFlag(id);
        f.setCheckedBy(staffUserId);
        f.setCheckedAt(LocalDateTime.now());
        f.setStaffNote(trim(note, 300));
        WellnessFlag saved = wellnessRepo.save(f);
        events.publishEvent(new ResidentNoticeEvent(this, f.getHostellerId(), "UNWELL_CHECKED",
                "The hostel is checking on you",
                "Hostel staff have seen that you're not feeling well" + (saved.getStaffNote() != null ? ": " + saved.getStaffNote() : ".")
                        + " Get well soon!", "/app/safety"));
        return wellnessView(saved);
    }

    // ═══ Late entry ════════════════════════════════════════════════════════

    @Transactional
    public LateEntryRequest requestLate(Long hostellerId, LocalDate date, String time, String reason) {
        BookingCheckin b = activeStay(hostellerId);
        HostelPolicy pol = policies.get(b.getHostelId());
        if (pol.getCurfewTime() == null) throw new AppException("Your hostel has no curfew, so no permission is needed", HttpStatus.BAD_REQUEST);
        if (!pol.isLateEntryRequests()) throw new AppException("Your hostel doesn't take late-entry requests in the app. Please speak to the warden.", HttpStatus.BAD_REQUEST);
        LocalDate today = DateUtils.todayKolkata();
        if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(14))) {
            throw new AppException("Choose a date in the next 14 days", HttpStatus.BAD_REQUEST);
        }
        LocalTime t;
        try { t = LocalTime.parse(time); } catch (Exception e) { throw new AppException("Time must be like 23:30", HttpStatus.BAD_REQUEST); }
        String r = trim(reason, 300);
        if (r == null || r.length() < 3) throw new AppException("Give a short reason", HttpStatus.BAD_REQUEST);
        boolean dup = lateRepo.findByHostellerIdAndEntryDate(hostellerId, date).stream()
                .anyMatch(x -> "PENDING".equals(x.getStatus()) || "APPROVED".equals(x.getStatus()));
        if (dup) throw new AppException("You already have a request for that night. Cancel it to change the time.", HttpStatus.CONFLICT);
        LateEntryRequest l = new LateEntryRequest();
        l.setHostelId(b.getHostelId());
        l.setHostellerId(hostellerId);
        l.setEntryDate(date);
        l.setExpectedTime(t.toString());
        l.setReason(r);
        LateEntryRequest saved = lateRepo.save(l);
        HostellerProfile p = profile(hostellerId);
        events.publishEvent(new StaffNoticeEvent(this, b.getHostelId(), "LATE_ENTRY_REQUESTED",
                "Late entry: " + name(p) + " on " + date.format(D),
                name(p) + " will return around " + t.format(T) + " on " + date.format(D) + " (curfew " + pol.getCurfewTime() + "). Reason: " + r,
                "/admin/safety", true, false));
        return saved;
    }

    @Transactional
    public LateEntryRequest cancelLate(Long hostellerId, Long id) {
        LateEntryRequest l = getLate(id);
        if (!l.getHostellerId().equals(hostellerId)) throw new AppException("Request not found", HttpStatus.NOT_FOUND);
        if (!"PENDING".equals(l.getStatus()) && !"APPROVED".equals(l.getStatus())) throw new AppException("This request is already closed", HttpStatus.CONFLICT);
        l.setStatus("CANCELLED");
        return lateRepo.save(l);
    }

    public List<LateEntryRequest> lateForHostel(Long hostelId) {
        return lateRepo.findByHostelIdAndEntryDateGreaterThanEqualOrderByEntryDateAscExpectedTimeAsc(hostelId, DateUtils.todayKolkata().minusDays(1))
                .stream().filter(l -> !"CANCELLED".equals(l.getStatus())).peek(l -> l.setResident(residentBrief(l.getHostellerId()))).toList();
    }

    public LateEntryRequest getLate(Long id) {
        return lateRepo.findById(id).orElseThrow(() -> new AppException("Request not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public LateEntryRequest reviewLate(Long id, boolean approve, String note, Long staffUserId) {
        LateEntryRequest l = getLate(id);
        if (!"PENDING".equals(l.getStatus())) throw new AppException("This request was already " + l.getStatus().toLowerCase(), HttpStatus.CONFLICT);
        String n = trim(note, 300);
        if (!approve && n == null) throw new AppException("Give the resident a reason", HttpStatus.BAD_REQUEST);
        l.setStatus(approve ? "APPROVED" : "REJECTED");
        l.setStaffNote(n);
        l.setReviewedBy(staffUserId);
        l.setReviewedAt(LocalDateTime.now());
        LateEntryRequest saved = lateRepo.save(l);
        String when = l.getEntryDate().format(D) + " at " + LocalTime.parse(l.getExpectedTime()).format(T);
        events.publishEvent(new ResidentNoticeEvent(this, l.getHostellerId(), approve ? "LATE_ENTRY_APPROVED" : "LATE_ENTRY_REJECTED",
                approve ? "Late entry approved" : "Late entry not approved",
                (approve ? "You can come in late on " + when + "." : "Your late entry for " + when + " wasn't approved.")
                        + (n != null ? " Note: " + n : ""), "/app/safety"));
        saved.setResident(residentBrief(l.getHostellerId()));
        return saved;
    }

    @Transactional
    public LateEntryRequest markArrived(Long id) {
        LateEntryRequest l = getLate(id);
        if (!"APPROVED".equals(l.getStatus())) throw new AppException("Only approved requests can be marked", HttpStatus.CONFLICT);
        l.setArrivedAt(LocalDateTime.now());
        LateEntryRequest saved = lateRepo.save(l);
        saved.setResident(residentBrief(l.getHostellerId()));
        return saved;
    }

    // ═══ Staff summary (banner + dashboard) ════════════════════════════════

    public Map<String, Object> summary(Long hostelId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sos", sosForHostel(hostelId, true));
        m.put("unwell", unwellForHostel(hostelId).size());
        LocalDate today = DateUtils.todayKolkata();
        m.put("lateTonight", lateRepo.findByHostelIdAndEntryDateGreaterThanEqualOrderByEntryDateAscExpectedTimeAsc(hostelId, today).stream()
                .filter(l -> l.getEntryDate().equals(today) && "APPROVED".equals(l.getStatus())).count());
        m.put("latePending", lateRepo.findByHostelIdAndEntryDateGreaterThanEqualOrderByEntryDateAscExpectedTimeAsc(hostelId, today).stream()
                .filter(l -> "PENDING".equals(l.getStatus())).count());
        return m;
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private BookingCheckin activeStay(Long hostellerId) {
        return bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .orElseThrow(() -> new AppException("This is for residents who are currently staying at a hostel", HttpStatus.BAD_REQUEST));
    }

    private HostellerProfile profile(Long id) {
        return profileRepo.findById(id).orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
    }

    private SosAlert sosView(SosAlert s) {
        s.setMapsUrl(s.getLatitude() == null ? null : mapsUrl(s));
        Map<String, Object> r = residentBrief(s.getHostellerId());
        contactRepo.findFirstByHostellerId(s.getHostellerId()).ifPresent(c -> r.put("emergencyContact",
                Map.of("name", nz(c.getName()), "relationship", nz(c.getRelationship()), "mobile", nz(c.getMobile()))));
        s.setResident(r);
        return s;
    }

    private WellnessFlag wellnessView(WellnessFlag f) {
        f.setNeedList(PolicyService.split(f.getNeeds()));
        f.setResident(residentBrief(f.getHostellerId()));
        return f;
    }

    private Map<String, Object> residentBrief(Long hostellerId) {
        Map<String, Object> m = new LinkedHashMap<>();
        profileRepo.findById(hostellerId).ifPresent(p -> {
            m.put("id", p.getId());
            m.put("name", name(p));
            m.put("mobile", p.getMobile());
            m.put("bloodGroup", p.getBloodGroup());
            m.put("medicalNotes", p.getMedicalNotes());
        });
        bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .flatMap(b -> roomRepo.findById(b.getRoomId())).ifPresent(r -> m.put("room", r.getRoomName()));
        return m;
    }

    private static String mapsUrl(SosAlert s) {
        return "https://www.google.com/maps?q=" + s.getLatitude().toPlainString() + "," + s.getLongitude().toPlainString();
    }

    static String needLabel(String n) {
        return switch (n) {
            case "FOOD_IN_ROOM" -> "food in room";
            case "MEDICINE" -> "medicine";
            case "DOCTOR" -> "a doctor";
            default -> "someone to check on them";
        };
    }

    private static String name(HostellerProfile p) {
        return p.getFirstName() + (p.getLastName() != null && !p.getLastName().isBlank() ? " " + p.getLastName() : "");
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String trim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }
}
