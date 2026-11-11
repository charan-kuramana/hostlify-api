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

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComplaintService {

    private final ComplaintRecordRepository complaintRepo;
    private final ComplaintMaintenanceRepository maintenanceRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final HostellerProfileRepository profileRepo;
    private final AuthUserRepository authUserRepo;
    private final BookingCheckinRepository bookingRepo;
    private final RoomRepository roomRepo;

    public ComplaintService(ComplaintRecordRepository complaintRepo,
                            ComplaintMaintenanceRepository maintenanceRepo,
                            ApplicationEventPublisher eventPublisher,
                            HostellerProfileRepository profileRepo,
                            AuthUserRepository authUserRepo,
                            BookingCheckinRepository bookingRepo,
                            RoomRepository roomRepo) {
        this.complaintRepo = complaintRepo;
        this.maintenanceRepo = maintenanceRepo;
        this.eventPublisher = eventPublisher;
        this.profileRepo = profileRepo;
        this.authUserRepo = authUserRepo;
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
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
        return complaintRepo.save(complaint);
    }

    @Transactional
    public ComplaintRecord resolveComplaint(Long id, Long resolvedBy) {
        return updateComplaintStatus(id, "RESOLVED", resolvedBy, null);
    }

    @Transactional
    public ComplaintRecord updateComplaintStatus(Long id, String status, Long resolvedBy, String resolution) {
        ComplaintRecord complaint = getComplaint(id);
        complaint.setStatus(status != null ? status : "RESOLVED");
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
