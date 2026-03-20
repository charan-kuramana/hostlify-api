package com.hostlify.module.service.service;

import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.service.entity.CleaningRequest;
import com.hostlify.module.service.repository.CleaningRequestRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class CleaningService {

    private final CleaningRequestRepository cleaningRepo;
    private final HostellerProfileRepository profileRepo;
    private final BookingCheckinRepository bookingRepo;

    public CleaningService(CleaningRequestRepository cleaningRepo,
                           HostellerProfileRepository profileRepo,
                           BookingCheckinRepository bookingRepo) {
        this.cleaningRepo = cleaningRepo;
        this.profileRepo = profileRepo;
        this.bookingRepo = bookingRepo;
    }

    public List<CleaningRequest> getByHostel(Long hostelId) {
        List<CleaningRequest> list = cleaningRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
        list.forEach(item -> {
            profileRepo.findById(item.getHostellerId()).ifPresent(p ->
                item.setHostellerName(p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "").trim()));
        });
        return list;
    }

    public List<CleaningRequest> getByHostelAndStatus(Long hostelId, String status) {
        return cleaningRepo.findByHostelIdAndStatusOrderByCreatedAtDesc(hostelId, status);
    }

    public List<CleaningRequest> getMyRequests(Long hostellerId) {
        return cleaningRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId);
    }

    @Transactional
    public CleaningRequest submitRequest(Long hostellerId, Long hostelId, String roomNumber, String notes) {
        // Resolve hostelId from active booking if not provided in JWT
        Long resolvedHostelId = hostelId;
        if (resolvedHostelId == null) {
            resolvedHostelId = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                    .map(b -> b.getHostelId())
                    .orElse(null);
        }

        CleaningRequest req = new CleaningRequest();
        req.setHostellerId(hostellerId);
        req.setHostelId(resolvedHostelId);
        req.setRoomNumber(roomNumber);
        req.setRequestDate(LocalDate.now());
        req.setNotes(notes);
        req.setStatus("PENDING");
        return cleaningRepo.save(req);
    }

    @Transactional
    public CleaningRequest updateStatus(Long requestId, String status, Long completedBy) {
        CleaningRequest req = cleaningRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Cleaning request not found", HttpStatus.NOT_FOUND));
        req.setStatus(status);
        if ("COMPLETED".equals(status) && completedBy != null) {
            req.setCompletedBy(completedBy);
        }
        return cleaningRepo.save(req);
    }
}
