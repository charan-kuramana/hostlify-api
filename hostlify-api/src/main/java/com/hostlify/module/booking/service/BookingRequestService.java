package com.hostlify.module.booking.service;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.entity.BookingRequest;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.booking.repository.BookingRequestRepository;
import com.hostlify.module.hostel.entity.Bed;
import com.hostlify.module.hostel.entity.HostelBookingSetting;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.hostel.repository.FloorRepository;
import com.hostlify.module.hostel.repository.HostelBookingSettingRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class BookingRequestService {

    private final BookingRequestRepository requestRepo;
    private final BedRepository bedRepo;
    private final HostellerProfileRepository profileRepo;
    private final HostelBookingSettingRepository settingRepo;
    private final BookingService bookingService;
    private final BookingCheckinRepository checkinRepo;
    private final RoomRepository roomRepo;
    private final FloorRepository floorRepo;

    public BookingRequestService(BookingRequestRepository requestRepo,
                                  BedRepository bedRepo,
                                  HostellerProfileRepository profileRepo,
                                  HostelBookingSettingRepository settingRepo,
                                  BookingService bookingService,
                                  BookingCheckinRepository checkinRepo,
                                  RoomRepository roomRepo,
                                  FloorRepository floorRepo) {
        this.requestRepo = requestRepo;
        this.bedRepo = bedRepo;
        this.profileRepo = profileRepo;
        this.settingRepo = settingRepo;
        this.bookingService = bookingService;
        this.checkinRepo = checkinRepo;
        this.roomRepo = roomRepo;
        this.floorRepo = floorRepo;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingRequest createRequest(Long userId, Long profileId, Long hostelId,
                                         Long bedId, LocalDate checkInDate, String notes) {
        // BR-02: cancel any existing pending request
        requestRepo.findFirstByHostellerUserIdAndStatusIn(userId,
                Arrays.asList("PENDING_APPROVAL", "REASSIGNED"))
                .ifPresent(existing -> {
                    bedRepo.findById(existing.getBedId()).ifPresent(b -> {
                        b.setStatus("AVAILABLE");
                        bedRepo.save(b);
                    });
                    existing.setStatus("CANCELLED");
                    requestRepo.save(existing);
                });

        // BR-01: check bed is AVAILABLE
        Bed bed = bedRepo.findByIdForUpdate(bedId)
                .orElseThrow(() -> new AppException("Bed not found", HttpStatus.NOT_FOUND));
        if (!"AVAILABLE".equals(bed.getStatus())) {
            throw new AppException("Bed is not available (status: " + bed.getStatus() + ")", HttpStatus.CONFLICT);
        }

        // Reserve the bed
        bed.setStatus("RESERVED");
        bedRepo.save(bed);

        // Get auto-expire setting
        HostelBookingSetting settings = settingRepo.findByHostelId(hostelId)
                .orElseGet(() -> {
                    HostelBookingSetting s = new HostelBookingSetting();
                    s.setAutoExpireDays(3);
                    return s;
                });
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(settings.getAutoExpireDays());

        // Build request
        var room = roomRepo.findById(bed.getRoomId()).orElseThrow();
        BookingRequest req = new BookingRequest();
        req.setHostellerUserId(userId);
        req.setHostellerProfileId(profileId);
        req.setHostelId(hostelId);
        req.setBedId(bedId);
        req.setRoomId(bed.getRoomId());
        req.setFloorId(room.getFloorId());
        req.setCheckInDate(checkInDate);
        req.setPreferenceNotes(notes);
        req.setStatus("PENDING_APPROVAL");
        req.setExpiresAt(expiresAt);

        BookingRequest saved = requestRepo.save(req);

        // If no approval required → auto-approve immediately
        if (!settings.isRequiresAdminApproval()) {
            return approveRequest(saved.getId(), null, null, null, null);
        }

        return saved;
    }

    public BookingRequest getMyRequest(Long userId) {
        return requestRepo.findFirstByHostellerUserIdAndStatusIn(userId,
                Arrays.asList("PENDING_APPROVAL", "REASSIGNED"))
                .orElseThrow(() -> new AppException("No active booking request found", HttpStatus.NOT_FOUND));
    }

    public List<BookingRequest> getRequestsForHostel(Long hostelId, String status) {
        if (status != null && !status.isEmpty()) {
            return requestRepo.findByHostelIdAndStatusOrderByCreatedAtDesc(hostelId, status);
        }
        return requestRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
    }

    @Transactional
    public BookingRequest cancelRequest(Long requestId, Long userId) {
        BookingRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Request not found", HttpStatus.NOT_FOUND));
        if (!req.getHostellerUserId().equals(userId)) {
            throw new AppException("Not authorized to cancel this request", HttpStatus.FORBIDDEN);
        }
        if (!"PENDING_APPROVAL".equals(req.getStatus()) && !"REASSIGNED".equals(req.getStatus())) {
            throw new AppException("Cannot cancel request in status: " + req.getStatus(), HttpStatus.CONFLICT);
        }
        bedRepo.findById(req.getBedId()).ifPresent(b -> {
            b.setStatus("AVAILABLE");
            bedRepo.save(b);
        });
        req.setStatus("CANCELLED");
        return requestRepo.save(req);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingRequest approveRequest(Long requestId, Long staffId,
                                          BigDecimal monthlyRent, BigDecimal advance, String paymentMode) {
        BookingRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Request not found", HttpStatus.NOT_FOUND));
        if (!"PENDING_APPROVAL".equals(req.getStatus()) && !"REASSIGNED".equals(req.getStatus())) {
            throw new AppException("Request is not in approvable state: " + req.getStatus(), HttpStatus.CONFLICT);
        }
        Long bedToAllocate = req.getReassignedBedId() != null ? req.getReassignedBedId() : req.getBedId();
        // Delegate to existing allocateBed (handles pessimistic lock + status transitions)
        BookingCheckin checkin = bookingService.allocateBed(
                req.getHostellerUserId(), bedToAllocate, req.getCheckInDate(),
                monthlyRent, advance != null ? advance : BigDecimal.ZERO,
                paymentMode != null ? paymentMode : "CASH");
        req.setStatus("APPROVED");
        req.setReviewedByStaffId(staffId);
        req.setBookingCheckinId(checkin.getId());
        return requestRepo.save(req);
    }

    @Transactional
    public BookingRequest rejectRequest(Long requestId, Long staffId, String reason) {
        BookingRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Request not found", HttpStatus.NOT_FOUND));
        if (!"PENDING_APPROVAL".equals(req.getStatus()) && !"REASSIGNED".equals(req.getStatus())) {
            throw new AppException("Request cannot be rejected in status: " + req.getStatus(), HttpStatus.CONFLICT);
        }
        // Release the bed
        Long bedToRelease = req.getReassignedBedId() != null ? req.getReassignedBedId() : req.getBedId();
        bedRepo.findById(bedToRelease).ifPresent(b -> {
            b.setStatus("AVAILABLE");
            bedRepo.save(b);
        });
        req.setStatus("REJECTED");
        req.setReviewedByStaffId(staffId);
        req.setRejectionReason(reason);
        return requestRepo.save(req);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingRequest reassignBed(Long requestId, Long staffId, Long newBedId) {
        BookingRequest req = requestRepo.findById(requestId)
                .orElseThrow(() -> new AppException("Request not found", HttpStatus.NOT_FOUND));
        // Release old bed
        Long oldBedId = req.getReassignedBedId() != null ? req.getReassignedBedId() : req.getBedId();
        bedRepo.findById(oldBedId).ifPresent(b -> {
            b.setStatus("AVAILABLE");
            bedRepo.save(b);
        });
        // Reserve new bed
        Bed newBed = bedRepo.findByIdForUpdate(newBedId)
                .orElseThrow(() -> new AppException("New bed not found", HttpStatus.NOT_FOUND));
        if (!"AVAILABLE".equals(newBed.getStatus())) {
            throw new AppException("New bed is not available", HttpStatus.CONFLICT);
        }
        newBed.setStatus("RESERVED");
        bedRepo.save(newBed);
        req.setReassignedBedId(newBedId);
        req.setStatus("REASSIGNED");
        req.setReviewedByStaffId(staffId);
        return requestRepo.save(req);
    }

    /** Runs daily at 9 AM — expires pending requests past their expires_at */
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void expirePendingRequests() {
        List<BookingRequest> expired = requestRepo.findExpiredRequests(LocalDateTime.now());
        for (BookingRequest req : expired) {
            Long bedToRelease = req.getReassignedBedId() != null ? req.getReassignedBedId() : req.getBedId();
            bedRepo.findById(bedToRelease).ifPresent(b -> {
                b.setStatus("AVAILABLE");
                bedRepo.save(b);
            });
            req.setStatus("EXPIRED");
            requestRepo.save(req);
        }
    }
}
