package com.hostlify.module.complaint.service;

import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.complaint.entity.ComplaintMaintenance;
import com.hostlify.module.complaint.entity.ComplaintRecord;
import com.hostlify.module.complaint.event.ComplaintResolvedEvent;
import com.hostlify.module.complaint.repository.ComplaintMaintenanceRepository;
import com.hostlify.module.complaint.repository.ComplaintRecordRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
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

    public ComplaintService(ComplaintRecordRepository complaintRepo,
                            ComplaintMaintenanceRepository maintenanceRepo,
                            ApplicationEventPublisher eventPublisher,
                            HostellerProfileRepository profileRepo,
                            AuthUserRepository authUserRepo) {
        this.complaintRepo = complaintRepo;
        this.maintenanceRepo = maintenanceRepo;
        this.eventPublisher = eventPublisher;
        this.profileRepo = profileRepo;
        this.authUserRepo = authUserRepo;
    }

    public List<ComplaintRecord> getComplaintsByHostel(Long hostelId) {
        return complaintRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
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
        return complaintRepo.save(complaint);
    }

    @Transactional
    public ComplaintRecord resolveComplaint(Long id, Long resolvedBy) {
        ComplaintRecord complaint = getComplaint(id);
        complaint.setStatus("RESOLVED");
        complaint.setAssignedTo(resolvedBy);
        complaint.setResolvedAt(LocalDateTime.now());
        ComplaintRecord saved = complaintRepo.save(complaint);

        // Publish notification event
        profileRepo.findById(saved.getHostellerId()).ifPresent(profile ->
                authUserRepo.findById(profile.getAuthUserId()).ifPresent(authUser ->
                        eventPublisher.publishEvent(new ComplaintResolvedEvent(
                                this, saved, authUser.getEmail(), profile.getFirstName()))));

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
        ComplaintMaintenance m = maintenanceRepo.findById(id)
                .orElseThrow(() -> new AppException("Maintenance task not found", HttpStatus.NOT_FOUND));
        m.setStatus("RESOLVED");
        m.setCompletedAt(LocalDateTime.now());
        return maintenanceRepo.save(m);
    }
}
