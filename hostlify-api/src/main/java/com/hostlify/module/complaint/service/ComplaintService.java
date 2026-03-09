package com.hostlify.module.complaint.service;

import com.hostlify.module.complaint.entity.ComplaintMaintenance;
import com.hostlify.module.complaint.entity.ComplaintRecord;
import com.hostlify.module.complaint.repository.ComplaintMaintenanceRepository;
import com.hostlify.module.complaint.repository.ComplaintRecordRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ComplaintService {

    private final ComplaintRecordRepository complaintRepo;
    private final ComplaintMaintenanceRepository maintenanceRepo;

    public ComplaintService(ComplaintRecordRepository complaintRepo,
                            ComplaintMaintenanceRepository maintenanceRepo) {
        this.complaintRepo = complaintRepo;
        this.maintenanceRepo = maintenanceRepo;
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
        return complaintRepo.save(complaint);
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
