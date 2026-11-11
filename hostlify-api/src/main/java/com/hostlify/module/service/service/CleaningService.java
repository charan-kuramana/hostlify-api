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
    private final com.hostlify.module.hostel.repository.RoomRepository roomRepo;

    public CleaningService(CleaningRequestRepository cleaningRepo,
                           HostellerProfileRepository profileRepo,
                           BookingCheckinRepository bookingRepo,
                           com.hostlify.module.hostel.repository.RoomRepository roomRepo) {
        this.cleaningRepo = cleaningRepo;
        this.profileRepo = profileRepo;
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
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
    public CleaningRequest submitRequest(Long hostellerId, Long hostelId, LocalDate preferredDate, String notes) {
        // Resolve hostel + room from the hosteller's active booking (JWT hostelId can be stale)
        var booking = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .orElseThrow(() -> new AppException("You need an active stay to request cleaning", HttpStatus.BAD_REQUEST));
        LocalDate date = preferredDate != null ? preferredDate : LocalDate.now();
        if (date.isBefore(LocalDate.now())) {
            throw new AppException("Preferred date cannot be in the past", HttpStatus.BAD_REQUEST);
        }
        if (date.isAfter(LocalDate.now().plusDays(30))) {
            throw new AppException("Preferred date must be within the next 30 days", HttpStatus.BAD_REQUEST);
        }

        CleaningRequest req = new CleaningRequest();
        req.setHostellerId(hostellerId);
        req.setHostelId(booking.getHostelId());
        req.setRoomNumber(roomRepo.findById(booking.getRoomId()).map(r -> r.getRoomName()).orElse(null));
        req.setRequestDate(date);
        req.setNotes(notes);
        req.setStatus("PENDING");
        return cleaningRepo.save(req);
    }

    /** Hosteller cancels their own request — only while it is still PENDING. */
    @Transactional
    public CleaningRequest cancelByHosteller(Long requestId, Long hostellerId) {
        CleaningRequest req = cleaningRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Cleaning request not found", HttpStatus.NOT_FOUND));
        if (!java.util.Objects.equals(req.getHostellerId(), hostellerId)) {
            throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (!"PENDING".equals(req.getStatus())) {
            throw new AppException("Only pending requests can be cancelled", HttpStatus.CONFLICT);
        }
        req.setStatus("CANCELLED");
        return cleaningRepo.save(req);
    }

    @Transactional
    /** Hostel id of a cleaning request (for staff access checks); 404 when missing. */
    public Long hostelIdOfRequest(Long requestId) {
        return cleaningRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Cleaning request not found", HttpStatus.NOT_FOUND))
                .getHostelId();
    }

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
