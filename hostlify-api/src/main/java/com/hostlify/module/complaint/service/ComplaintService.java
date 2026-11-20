package com.hostlify.module.complaint.service;

import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.complaint.entity.ComplaintMaintenance;
import com.hostlify.module.complaint.entity.ComplaintRecord;
import com.hostlify.module.complaint.event.ComplaintResolvedEvent;
import com.hostlify.module.complaint.repository.ComplaintMaintenanceRepository;
import com.hostlify.module.complaint.repository.ComplaintRecordRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hostlify.module.complaint.entity.ComplaintSlaTarget;
import com.hostlify.shared.event.StaffNoticeEvent;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ComplaintService {

    private final ComplaintRecordRepository complaintRepo;
    private final ComplaintMaintenanceRepository maintenanceRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final HostellerProfileRepository profileRepo;
    private final AuthUserRepository authUserRepo;
    private final BookingCheckinRepository bookingRepo;
    private final RoomRepository roomRepo;
    private final ComplaintRecordRepository.SlaTargets slaRepo;

    public ComplaintService(ComplaintRecordRepository complaintRepo,
                            ComplaintMaintenanceRepository maintenanceRepo,
                            ApplicationEventPublisher eventPublisher,
                            HostellerProfileRepository profileRepo,
                            AuthUserRepository authUserRepo,
                            BookingCheckinRepository bookingRepo,
                            RoomRepository roomRepo,
                            ComplaintRecordRepository.SlaTargets slaRepo) {
        this.complaintRepo = complaintRepo;
        this.maintenanceRepo = maintenanceRepo;
        this.eventPublisher = eventPublisher;
        this.profileRepo = profileRepo;
        this.authUserRepo = authUserRepo;
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
        this.slaRepo = slaRepo;
    }

    public List<ComplaintRecord> getComplaintsByHostel(Long hostelId) {
        List<ComplaintRecord> list = complaintRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
        list.forEach(item -> {
            profileRepo.findById(item.getHostellerId()).ifPresent(p ->
                item.setHostellerName(p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "").trim()));
            bookingRepo.findFirstByHostellerIdAndStatus(item.getHostellerId(), "ACTIVE")
                .ifPresent(b -> roomRepo.findById(b.getRoomId())
                    .ifPresent(r -> item.setRoomNumber(r.getRoomName())));
        });
        return list;
    }

    public List<ComplaintRecord> getComplaintsByHosteller(Long hostellerId) {
        return complaintRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId);
    }

    public ComplaintRecord getComplaint(Long id) {
        return complaintRepo.findById(id)
                .orElseThrow(() -> new AppException("Complaint not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ComplaintRecord createComplaint(ComplaintRecord complaint) {
        // If hostelId not provided, look up from active booking
        if (complaint.getHostelId() == null && complaint.getHostellerId() != null) {
            bookingRepo.findFirstByHostellerIdAndStatus(complaint.getHostellerId(), "ACTIVE")
                    .ifPresent(b -> {
                        complaint.setHostelId(b.getHostelId());
                        if (complaint.getRoomId() == null) complaint.setRoomId(b.getRoomId());
                    });
        }
        if (complaint.getHostelId() == null || complaint.getHostellerId() == null) {
            throw new AppException("You need an active stay to raise a complaint", HttpStatus.BAD_REQUEST);
        }
        complaint.setAcknowledgedAt(null);
        complaint.setResidentRating(null);
        complaint.setResidentFeedback(null);
        complaint.setOverdueAlerted(false);
        int hours = targetHours(complaint.getHostelId(), complaint.getCategory());
        complaint.setDueAt(LocalDateTime.now().plusHours(hours));
        ComplaintRecord saved = complaintRepo.save(complaint);
        String who = profileRepo.findById(saved.getHostellerId()).map(p -> p.getFirstName()).orElse("A resident");
        eventPublisher.publishEvent(new StaffNoticeEvent(this, saved.getHostelId(), "COMPLAINT_CREATED",
                "New complaint: " + saved.getTitle(),
                who + " reported a " + saved.getCategory().toLowerCase() + " issue. Target: fix within " + hours + " hours.",
                "/admin/complaints", true, false));
        return saved;
    }

    // ─── SLA targets ───────────────────────────────────────────────

    /** Hours to fix a complaint of this category: the hostel's own target, else the default. */
    public int targetHours(Long hostelId, String category) {
        return slaRepo.findByHostelId(hostelId).stream()
                .filter(t -> t.getCategory().equalsIgnoreCase(category)).map(ComplaintSlaTarget::getTargetHours).findFirst()
                .orElse(ComplaintSlaTarget.DEFAULT_HOURS.getOrDefault(category, ComplaintSlaTarget.FALLBACK_HOURS));
    }

    /** Targets for every category plus last-30-day performance. */
    public Map<String, Object> slaOverview(Long hostelId) {
        Map<String, Integer> targets = new LinkedHashMap<>();
        ComplaintSlaTarget.DEFAULT_HOURS.keySet().stream().sorted().forEach(c -> targets.put(c, targetHours(hostelId, c)));
        slaRepo.findByHostelId(hostelId).forEach(t -> targets.put(t.getCategory(), t.getTargetHours()));
        List<ComplaintRecord> recent = complaintRepo.findByHostelIdAndCreatedAtAfter(hostelId, LocalDateTime.now().minusDays(30));
        List<ComplaintRecord> fixed = recent.stream().filter(c -> c.getResolvedAt() != null).toList();
        long onTime = fixed.stream().filter(c -> !c.isOverdue()).count();
        double avgFixHours = fixed.stream().mapToLong(c -> Duration.between(c.getCreatedAt(), c.getResolvedAt()).toMinutes())
                .average().orElse(0) / 60.0;
        List<ComplaintRecord> acked = recent.stream().filter(c -> c.getAcknowledgedAt() != null).toList();
        double avgAckHours = acked.stream().mapToLong(c -> Duration.between(c.getCreatedAt(), c.getAcknowledgedAt()).toMinutes())
                .average().orElse(0) / 60.0;
        List<ComplaintRecord> rated = recent.stream().filter(c -> c.getResidentRating() != null).toList();
        Map<String, Object> perf = new LinkedHashMap<>();
        perf.put("total", recent.size());
        perf.put("resolved", fixed.size());
        perf.put("onTimePercent", fixed.isEmpty() ? 0 : Math.round(onTime * 100.0 / fixed.size()));
        perf.put("openOverdue", recent.stream().filter(c -> c.getResolvedAt() == null && c.isOverdue()).count());
        perf.put("avgAcknowledgeHours", Math.round(avgAckHours * 10) / 10.0);
        perf.put("avgFixHours", Math.round(avgFixHours * 10) / 10.0);
        perf.put("avgRating", rated.isEmpty() ? 0 : Math.round(rated.stream().mapToInt(ComplaintRecord::getResidentRating).average().orElse(0) * 10) / 10.0);
        perf.put("ratings", rated.size());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("targets", targets);
        out.put("last30Days", perf);
        return out;
    }

    @Transactional
    public Map<String, Integer> saveTargets(Long hostelId, Map<String, Object> hoursByCategory) {
        Map<String, ComplaintSlaTarget> existing = new HashMap<>();
        slaRepo.findByHostelId(hostelId).forEach(t -> existing.put(t.getCategory(), t));
        Map<String, Integer> out = new LinkedHashMap<>();
        hoursByCategory.forEach((category, raw) -> {
            if (category == null || category.isBlank() || category.length() > 40) {
                throw new AppException("Invalid category", HttpStatus.BAD_REQUEST);
            }
            int h = raw instanceof Number n ? n.intValue() : -1;
            if (h < 1 || h > 720) throw new AppException("Target for " + category + " must be 1 to 720 hours", HttpStatus.BAD_REQUEST);
            ComplaintSlaTarget t = existing.getOrDefault(category, new ComplaintSlaTarget());
            t.setHostelId(hostelId);
            t.setCategory(category);
            t.setTargetHours(h);
            slaRepo.save(t);
            out.put(category, h);
        });
        return out;
    }

    /** Resident: rate how a resolved complaint was handled. */
    @Transactional
    public ComplaintRecord rate(Long id, Long hostellerId, int rating, String feedback) {
        ComplaintRecord c = getComplaint(id);
        if (!Objects.equals(c.getHostellerId(), hostellerId)) throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        if (!"RESOLVED".equals(c.getStatus()) && !"CLOSED".equals(c.getStatus())) {
            throw new AppException("You can rate a complaint after it is resolved", HttpStatus.BAD_REQUEST);
        }
        if (rating < 1 || rating > 5) throw new AppException("Rating must be 1 to 5 stars", HttpStatus.BAD_REQUEST);
        String fb = feedback == null ? "" : feedback.trim();
        c.setResidentRating((short) rating);
        c.setResidentFeedback(fb.isEmpty() ? null : fb.substring(0, Math.min(500, fb.length())));
        return complaintRepo.save(c);
    }

    /** Every 30 minutes: alert staff once about complaints that passed their target time. */
    @Scheduled(cron = "0 */30 * * * *")
    @Transactional
    public void alertOverdue() {
        for (ComplaintRecord c : complaintRepo.findByStatusInAndDueAtBeforeAndOverdueAlertedFalse(List.of("OPEN", "IN_PROGRESS"), LocalDateTime.now())) {
            c.setOverdueAlerted(true);
            complaintRepo.save(c);
            eventPublisher.publishEvent(new StaffNoticeEvent(this, c.getHostelId(), "COMPLAINT_OVERDUE",
                    "Overdue complaint: " + c.getTitle(),
                    "This " + c.getCategory().toLowerCase() + " complaint has passed its target time and is still "
                            + ("OPEN".equals(c.getStatus()) ? "not picked up." : "in progress."),
                    "/admin/complaints", true, true));
        }
    }

    @Transactional
    public ComplaintRecord resolveComplaint(Long id, Long resolvedBy) {
        return updateComplaintStatus(id, "RESOLVED", resolvedBy, null);
    }

    @Transactional
    public ComplaintRecord updateComplaintStatus(Long id, String status, Long resolvedBy, String resolution) {
        ComplaintRecord complaint = getComplaint(id);
        complaint.setStatus(status != null ? status : "RESOLVED");
        if (!"OPEN".equals(complaint.getStatus()) && complaint.getAcknowledgedAt() == null) {
            complaint.setAcknowledgedAt(LocalDateTime.now());
        }
        if (resolvedBy != null) complaint.setAssignedTo(resolvedBy);
        if (resolution != null) complaint.setResolutionNotes(resolution);
        if ("RESOLVED".equals(status) || "CLOSED".equals(status)) {
            complaint.setResolvedAt(LocalDateTime.now());
        }
        ComplaintRecord saved = complaintRepo.save(complaint);

        // Publish notification event for RESOLVED status
        if ("RESOLVED".equals(status) && saved.getHostellerId() != null) {
            profileRepo.findById(saved.getHostellerId()).ifPresent(profile ->
                    authUserRepo.findById(profile.getAuthUserId()).ifPresent(authUser ->
                            eventPublisher.publishEvent(new ComplaintResolvedEvent(
                                    this, saved, authUser.getEmail(), profile.getFirstName()))));
        }

        return saved;
    }

    public List<ComplaintMaintenance> getMaintenanceByHostel(Long hostelId) {
        return maintenanceRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
    }

    @Transactional
    public ComplaintMaintenance createMaintenance(ComplaintMaintenance maintenance) {
        return maintenanceRepo.save(maintenance);
    }

    @Transactional
    public ComplaintMaintenance resolveMaintenance(Long id) {
        return updateMaintenanceStatus(id, "RESOLVED");
    }

    @Transactional
    public ComplaintMaintenance updateMaintenanceStatus(Long id, String status) {
        ComplaintMaintenance m = maintenanceRepo.findById(id)
                .orElseThrow(() -> new AppException("Maintenance task not found", HttpStatus.NOT_FOUND));
        m.setStatus(status != null ? status : "OPEN");
        if ("RESOLVED".equals(status) || "COMPLETED".equals(status)) {
            m.setCompletedAt(LocalDateTime.now());
        }
        return maintenanceRepo.save(m);
    }
}
