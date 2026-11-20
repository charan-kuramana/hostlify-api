package com.hostlify.module.resident.gate;

import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.compliance.ResidentNoticeEvent;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Visitor gate passes and the parcel log. */
@Service
public class GateService {

    private static final int MAX_DAYS_AHEAD = 30;
    private static final int MAX_UPCOMING_PER_RESIDENT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private final GateRepos.Visitors visitorRepo;
    private final GateRepos.Parcels parcelRepo;
    private final HostellerProfileRepository profileRepo;
    private final BookingCheckinRepository bookingRepo;
    private final RoomRepository roomRepo;
    private final ApplicationEventPublisher events;

    public GateService(GateRepos.Visitors visitorRepo, GateRepos.Parcels parcelRepo, HostellerProfileRepository profileRepo,
                       BookingCheckinRepository bookingRepo, RoomRepository roomRepo, ApplicationEventPublisher events) {
        this.visitorRepo = visitorRepo;
        this.parcelRepo = parcelRepo;
        this.profileRepo = profileRepo;
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
        this.events = events;
    }

    // ═══ Visitors ══════════════════════════════════════════════════════════

    /** Resident pre-approves a visitor (EXPECTED with a 6-digit pass code), or staff log a walk-in (INSIDE now). */
    @Transactional
    public VisitorPass createPass(Long hostelId, Long hostellerId, Map<String, Object> body, Long createdBy, String role, boolean walkIn) {
        String name = text(body.get("visitorName"), 100);
        if (name == null || name.length() < 2) throw bad("Visitor name is required");
        String mobile = text(body.get("visitorMobile"), 15);
        if (mobile != null && !mobile.replaceAll("[\\s-]", "").matches("(\\+91)?[6-9]\\d{9}")) throw bad("Enter a valid 10-digit mobile number");
        LocalDate today = DateUtils.todayKolkata();
        LocalDate date = walkIn || body.get("visitDate") == null ? today : LocalDate.parse(String.valueOf(body.get("visitDate")));
        if (date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw bad("Visit date must be within the next " + MAX_DAYS_AHEAD + " days");
        }
        if (hostellerId != null) {
            HostellerProfile p = profileRepo.findById(hostellerId).orElseThrow(() -> bad("Resident not found"));
            if (!Objects.equals(p.getHostelId(), hostelId)) throw bad("This resident is not in this hostel");
            if (!walkIn && visitorRepo.countByHostellerIdAndStatusAndVisitDateGreaterThanEqual(hostellerId, "EXPECTED", today) >= MAX_UPCOMING_PER_RESIDENT) {
                throw bad("You already have " + MAX_UPCOMING_PER_RESIDENT + " upcoming visitor passes");
            }
        }
        VisitorPass v = new VisitorPass();
        v.setHostelId(hostelId);
        v.setHostellerId(hostellerId);
        v.setVisitorName(name);
        v.setVisitorMobile(mobile);
        v.setRelation(text(body.get("relation"), 40));
        v.setPurpose(text(body.get("purpose"), 150));
        v.setVisitDate(date);
        v.setPassCode(newCode(hostelId));
        v.setCreatedBy(createdBy);
        v.setCreatedByRole(role);
        if (walkIn) {
            v.setStatus("INSIDE");
            v.setEntryAt(LocalDateTime.now());
            v.setEntryBy(createdBy);
        }
        v = visitorRepo.save(v);
        if (walkIn && hostellerId != null) notifyArrival(v);
        return enrich(v);
    }

    /** Staff: today's passes plus anyone still inside from earlier days. */
    public List<VisitorPass> forDate(Long hostelId, LocalDate date) {
        Map<Long, VisitorPass> m = new LinkedHashMap<>();
        visitorRepo.findByHostelIdAndStatus(hostelId, "INSIDE").forEach(v -> m.put(v.getId(), v));
        visitorRepo.findByHostelIdAndVisitDateOrderByCreatedAtDesc(hostelId, date).forEach(v -> m.putIfAbsent(v.getId(), v));
        return m.values().stream().map(this::enrich).toList();
    }

    public List<VisitorPass> findByCode(Long hostelId, String code) {
        return visitorRepo.findByHostelIdAndPassCodeAndStatus(hostelId, code == null ? "" : code.trim(), "EXPECTED").stream()
                .map(this::enrich).toList();
    }

    public Map<String, Object> mine(Long hostellerId) {
        LocalDate today = DateUtils.todayKolkata();
        return Map.of(
                "upcoming", visitorRepo.findByHostellerIdAndVisitDateGreaterThanEqualOrderByVisitDateAsc(hostellerId, today),
                "past", visitorRepo.findTop20ByHostellerIdAndVisitDateBeforeOrderByVisitDateDesc(hostellerId, today));
    }

    @Transactional
    public VisitorPass checkIn(Long id, Long staffUserId) {
        VisitorPass v = pass(id);
        if (!"EXPECTED".equals(v.getStatus())) throw bad("This pass is " + v.getStatus().toLowerCase());
        if (!v.getVisitDate().equals(DateUtils.todayKolkata())) throw bad("This pass is for " + v.getVisitDate() + ", not today");
        v.setStatus("INSIDE");
        v.setEntryAt(LocalDateTime.now());
        v.setEntryBy(staffUserId);
        v = visitorRepo.save(v);
        if (v.getHostellerId() != null) notifyArrival(v);
        return enrich(v);
    }

    @Transactional
    public VisitorPass checkOut(Long id, Long staffUserId) {
        VisitorPass v = pass(id);
        if (!"INSIDE".equals(v.getStatus())) throw bad("This visitor is not marked inside");
        v.setStatus("LEFT");
        v.setExitAt(LocalDateTime.now());
        v.setExitBy(staffUserId);
        return enrich(visitorRepo.save(v));
    }

    @Transactional
    public VisitorPass cancel(Long id) {
        VisitorPass v = pass(id);
        if (!"EXPECTED".equals(v.getStatus())) throw bad("Only an upcoming pass can be cancelled");
        v.setStatus("CANCELLED");
        return visitorRepo.save(v);
    }

    public VisitorPass pass(Long id) {
        return visitorRepo.findById(id).orElseThrow(() -> new AppException("Visitor pass not found", HttpStatus.NOT_FOUND));
    }

    private void notifyArrival(VisitorPass v) {
        events.publishEvent(new ResidentNoticeEvent(this, v.getHostellerId(), "VISITOR_ARRIVED",
                "Your visitor " + v.getVisitorName() + " has arrived",
                v.getVisitorName() + " checked in at the gate at " + v.getEntryAt().format(HM) + ".", "/app/visitors"));
    }

    private String newCode(Long hostelId) {
        for (int i = 0; i < 20; i++) {
            String code = String.format("%06d", RANDOM.nextInt(1_000_000));
            if (visitorRepo.findByHostelIdAndPassCodeAndStatus(hostelId, code, "EXPECTED").isEmpty()) return code;
        }
        throw new AppException("Could not create a pass code, please try again", HttpStatus.SERVICE_UNAVAILABLE);
    }

    // ═══ Parcels ═══════════════════════════════════════════════════════════

    @Transactional
    public Parcel logParcel(Long hostelId, Map<String, Object> body, Long staffUserId) {
        if (!(body.get("hostellerId") instanceof Number n)) throw bad("Choose the resident the parcel is for");
        HostellerProfile p = profileRepo.findById(n.longValue()).orElseThrow(() -> bad("Resident not found"));
        if (!Objects.equals(p.getHostelId(), hostelId)) throw bad("This resident is not in this hostel");
        Parcel parcel = new Parcel();
        parcel.setHostelId(hostelId);
        parcel.setHostellerId(p.getId());
        parcel.setCourier(text(body.get("courier"), 60));
        parcel.setDescription(text(body.get("description"), 200));
        parcel.setShelf(text(body.get("shelf"), 30));
        parcel.setReceivedBy(staffUserId);
        parcel = parcelRepo.save(parcel);
        String what = parcel.getCourier() != null ? "A " + parcel.getCourier() + " parcel" : "A parcel";
        events.publishEvent(new ResidentNoticeEvent(this, p.getId(), "PARCEL_RECEIVED", "You have a parcel at the desk",
                what + (parcel.getDescription() != null ? " (" + parcel.getDescription() + ")" : "")
                        + " is waiting for you at the hostel desk" + (parcel.getShelf() != null ? ", shelf " + parcel.getShelf() : "") + ".",
                "/app/parcels"));
        return enrich(parcel);
    }

    @Transactional
    public Parcel collect(Long id, String collectedBy, Long staffUserId) {
        Parcel parcel = parcel(id);
        if (!"WAITING".equals(parcel.getStatus())) throw bad("This parcel was already collected");
        parcel.setStatus("COLLECTED");
        parcel.setCollectedAt(LocalDateTime.now());
        parcel.setHandedBy(staffUserId);
        parcel.setCollectedByName(text(collectedBy, 100));
        return enrich(parcelRepo.save(parcel));
    }

    /** Waiting parcels plus those collected in the last 7 days. */
    public Map<String, Object> hostelParcels(Long hostelId) {
        return Map.of(
                "waiting", parcelRepo.findByHostelIdAndStatusOrderByReceivedAtDesc(hostelId, "WAITING").stream().map(this::enrich).toList(),
                "collected", parcelRepo.findByHostelIdAndStatusAndCollectedAtAfterOrderByCollectedAtDesc(hostelId, "COLLECTED",
                        LocalDateTime.now().minusDays(7)).stream().map(this::enrich).toList());
    }

    public List<Parcel> myParcels(Long hostellerId) {
        return parcelRepo.findTop30ByHostellerIdOrderByReceivedAtDesc(hostellerId);
    }

    public Parcel parcel(Long id) {
        return parcelRepo.findById(id).orElseThrow(() -> new AppException("Parcel not found", HttpStatus.NOT_FOUND));
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private VisitorPass enrich(VisitorPass v) {
        if (v.getHostellerId() != null) {
            v.setResidentName(name(v.getHostellerId()));
            v.setRoomNumber(room(v.getHostellerId()));
        }
        return v;
    }

    private Parcel enrich(Parcel p) {
        p.setResidentName(name(p.getHostellerId()));
        p.setRoomNumber(room(p.getHostellerId()));
        return p;
    }

    private String name(Long hostellerId) {
        return profileRepo.findById(hostellerId)
                .map(p -> (p.getFirstName() + " " + (p.getLastName() == null ? "" : p.getLastName())).trim()).orElse(null);
    }

    private String room(Long hostellerId) {
        return bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .flatMap(b -> roomRepo.findById(b.getRoomId())).map(r -> r.getRoomName()).orElse(null);
    }

    private static String text(Object v, int max) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static AppException bad(String msg) {
        return new AppException(msg, HttpStatus.BAD_REQUEST);
    }
}
