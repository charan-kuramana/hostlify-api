package com.hostlify.module.listing.service;

import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.listing.entity.HostelEnquiry;
import com.hostlify.module.listing.event.EnquiryEvent;
import com.hostlify.module.listing.repository.HostelEnquiryRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class EnquiryService {

    private static final Logger log = LoggerFactory.getLogger(EnquiryService.class);
    private static final Pattern MOBILE = Pattern.compile("^[6-9][0-9]{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,255}\\.[^\\s@]{2,}$");

    private final HostelEnquiryRepository repo;
    private final HostelRepository hostelRepo;
    private final BedRepository bedRepo;
    private final ListingService listingService;
    private final ApplicationEventPublisher events;

    public EnquiryService(HostelEnquiryRepository repo, HostelRepository hostelRepo, BedRepository bedRepo,
                          ListingService listingService, ApplicationEventPublisher events) {
        this.repo = repo;
        this.hostelRepo = hostelRepo;
        this.bedRepo = bedRepo;
        this.listingService = listingService;
        this.events = events;
    }

    /**
     * Public form submission. Returns a status message for the visitor.
     * Same mobile within 24h for the same hostel is not stored twice (no duplicate alerts to staff).
     */
    @Transactional
    public String create(String slug, Map<String, Object> body) {
        Hostel h = listingService.listedBySlug(slug);

        String name = text(body.get("name"), 120);
        if (name == null || name.length() < 2) throw bad("Please enter your name");
        String mobile = normaliseMobile(text(body.get("mobile"), 20));
        if (mobile == null) throw bad("Enter a valid 10-digit mobile number");
        String email = text(body.get("email"), 150);
        if (email != null && !EMAIL.matcher(email).matches()) throw bad("Enter a valid email address, or leave it empty");

        LocalDate today = DateUtils.todayKolkata();
        LocalDate moveIn = date(body.get("moveInDate"));
        if (moveIn != null && (moveIn.isBefore(today) || moveIn.isAfter(today.plusMonths(6)))) throw bad("Move-in date must be within the next 6 months");
        LocalDate visit = date(body.get("visitDate"));
        String slot = text(body.get("visitSlot"), 20);
        if (visit != null) {
            if (visit.isBefore(today) || visit.isAfter(today.plusDays(30))) throw bad("Pick a visit date within the next 30 days");
            if (slot == null || !HostelEnquiry.SLOTS.contains(slot.toUpperCase())) throw bad("Pick a visit time");
            slot = slot.toUpperCase();
        } else {
            slot = null;
        }

        if (repo.existsByHostelIdAndMobileAndCreatedAtAfter(h.getId(), mobile, LocalDateTime.now().minusHours(24))) {
            return "We already have your request. " + h.getName() + " will contact you soon.";
        }

        HostelEnquiry e = new HostelEnquiry();
        e.setHostelId(h.getId());
        e.setName(name);
        e.setMobile(mobile);
        e.setEmail(email);
        String gender = text(body.get("gender"), 10);
        e.setGender(gender != null && Set.of("MALE", "FEMALE", "OTHER").contains(gender.toUpperCase()) ? gender.toUpperCase() : null);
        e.setRoomType(text(body.get("roomType"), 30));
        e.setMoveInDate(moveIn);
        e.setMessage(text(body.get("message"), 1000));
        e.setVisitDate(visit);
        e.setVisitSlot(slot);
        boolean waitlist = Boolean.TRUE.equals(body.get("waitlist"));
        e.setStatus(waitlist ? "WAITLIST" : "NEW");
        HostelEnquiry saved = repo.save(e);

        events.publishEvent(new EnquiryEvent(this, EnquiryEvent.Kind.CREATED, saved, h.getName(), 0, 0));
        if (waitlist) return "You're on the waitlist. " + h.getName() + " will contact you when a bed is free.";
        return visit != null
                ? "Visit request sent. " + h.getName() + " will call you to confirm the time."
                : "Thanks! " + h.getName() + " will contact you soon.";
    }

    // ─── Staff inbox ───────────────────────────────────────────────────────

    public List<HostelEnquiry> list(Long hostelId, String status) {
        return status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)
                ? repo.findByHostelIdOrderByCreatedAtDesc(hostelId)
                : repo.findByHostelIdAndStatusOrderByCreatedAtDesc(hostelId, status.toUpperCase());
    }

    public Map<String, Long> counts(Long hostelId) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String s : List.of("NEW", "CONTACTED", "VISIT_SCHEDULED", "WAITLIST", "JOINED", "LOST")) {
            m.put(s, repo.countByHostelIdAndStatus(hostelId, s));
        }
        return m;
    }

    public HostelEnquiry get(Long id) {
        return repo.findById(id).orElseThrow(() -> new AppException("Enquiry not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public HostelEnquiry update(Long id, Map<String, Object> body) {
        HostelEnquiry e = get(id);
        String before = e.getStatus();
        LocalDate beforeVisit = e.getVisitDate();
        String beforeSlot = e.getVisitSlot();

        if (body.containsKey("status")) {
            String s = String.valueOf(body.get("status")).toUpperCase();
            if (!HostelEnquiry.STATUSES.contains(s)) throw bad("Unknown status");
            e.setStatus(s);
            if (!s.equals("NEW")) e.setLastContactedAt(LocalDateTime.now());
        }
        if (body.containsKey("staffNotes")) e.setStaffNotes(text(body.get("staffNotes"), 2000));
        if (body.containsKey("visitDate")) e.setVisitDate(date(body.get("visitDate")));
        if (body.containsKey("visitSlot")) {
            String slot = text(body.get("visitSlot"), 20);
            if (slot != null && !HostelEnquiry.SLOTS.contains(slot.toUpperCase())) throw bad("Unknown visit time");
            e.setVisitSlot(slot == null ? null : slot.toUpperCase());
        }
        if (Boolean.TRUE.equals(body.get("markContacted"))) e.setLastContactedAt(LocalDateTime.now());
        if ("VISIT_SCHEDULED".equals(e.getStatus()) && (e.getVisitDate() == null || e.getVisitSlot() == null)) {
            throw bad("Set the visit date and time to schedule a visit");
        }
        e.setUpdatedAt(LocalDateTime.now());
        HostelEnquiry saved = repo.save(e);

        boolean visitChanged = !Objects.equals(beforeVisit, saved.getVisitDate()) || !Objects.equals(beforeSlot, saved.getVisitSlot());
        if ("VISIT_SCHEDULED".equals(saved.getStatus()) && (!"VISIT_SCHEDULED".equals(before) || visitChanged)) {
            String hostelName = hostelRepo.findById(saved.getHostelId()).map(Hostel::getName).orElse("the hostel");
            events.publishEvent(new EnquiryEvent(this, EnquiryEvent.Kind.VISIT_CONFIRMED, saved, hostelName, 0, 0));
        }
        return saved;
    }

    // ─── Waitlist nudge (daily, 10 AM IST) ─────────────────────────────────

    /** When a hostel has free beds and people on its waitlist, remind staff (at most weekly per person). */
    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void nudgeWaitlists() {
        LocalDate today = DateUtils.todayKolkata();
        Map<Long, List<HostelEnquiry>> byHostel = new HashMap<>();
        for (HostelEnquiry e : repo.findByStatus("WAITLIST")) {
            if (e.getWaitlistNudgedOn() != null && e.getWaitlistNudgedOn().isAfter(today.minusDays(7))) continue;
            byHostel.computeIfAbsent(e.getHostelId(), k -> new ArrayList<>()).add(e);
        }
        for (var entry : byHostel.entrySet()) {
            try {
                long free = bedRepo.countByHostelIdAndStatus(entry.getKey(), "AVAILABLE");
                if (free <= 0) continue;
                String hostelName = hostelRepo.findById(entry.getKey()).map(Hostel::getName).orElse("your hostel");
                events.publishEvent(new EnquiryEvent(this, EnquiryEvent.Kind.WAITLIST_NUDGE, entry.getValue().get(0),
                        hostelName, entry.getValue().size(), free));
                entry.getValue().forEach(e -> {
                    e.setWaitlistNudgedOn(today);
                    repo.save(e);
                });
            } catch (Exception ex) {
                log.error("Waitlist nudge failed for hostel {}: {}", entry.getKey(), ex.getMessage());
            }
        }
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    static String normaliseMobile(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D", "");
        if (d.length() == 12 && d.startsWith("91")) d = d.substring(2);
        if (d.length() == 11 && d.startsWith("0")) d = d.substring(1);
        return MOBILE.matcher(d).matches() ? d : null;
    }

    private static String text(Object v, int max) {
        if (v == null) return null;
        String s = String.valueOf(v).trim().replaceAll("[\\p{Cntrl}&&[^\\n]]", "");
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static LocalDate date(Object v) {
        String s = text(v, 10);
        if (s == null) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw bad("Invalid date: " + s);
        }
    }

    private static AppException bad(String msg) {
        return new AppException(msg, HttpStatus.BAD_REQUEST);
    }
}
