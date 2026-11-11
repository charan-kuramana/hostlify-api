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
        // A resident with an ACTIVE booking can't request a second bed
        if (profileId != null && checkinRepo.findFirstByHostellerIdAndStatus(profileId, "ACTIVE").isPresent()) {
            throw new AppException("You already have an active stay. Contact your hostel to change beds.", HttpStatus.CONFLICT);
        }
        if (checkInDate.isBefore(LocalDate.now())) {
            throw new AppException("Check-in date cannot be in the past", HttpStatus.BAD_REQUEST);
        }
        if (checkInDate.isAfter(LocalDate.now().plusDays(90))) {
            throw new AppException("Check-in date must be within the next 90 days", HttpStatus.BAD_REQUEST);
        }
        if (notes != null && notes.length() > 1000) {
            throw new AppException("Preference notes must be 1000 characters or less", HttpStatus.BAD_REQUEST);
        }

        // BR-02: cancel any existing pending request
        requestRepo.findFirstByHostellerUserIdAndStatusIn(userId,
                Arrays.asList("PENDING_APPROVAL", "REASSIGNED"))
                .ifPresent(existing -> {
                    // release the bed actually held (the reassigned one, if staff moved the request)
                    bedRepo.findById(existing.getReassignedBedId() != null ? existing.getReassignedBedId() : existing.getBedId()).ifPresent(b -> {
                        b.setStatus("AVAILABLE");
                        bedRepo.save(b);
                    });
                    existing.setStatus("CANCELLED");
                    requestRepo.save(existing);
                });

        // BR-01: check bed is AVAILABLE
        Bed bed = bedRepo.findByIdForUpdate(bedId)
                .orElseThrow(() -> new AppException("Bed not found", HttpStatus.NOT_FOUND));
        if (!java.util.Objects.equals(bed.getHostelId(), hostelId)) {
            throw new AppException("Bed does not belong to this hostel", HttpStatus.BAD_REQUEST);
        }
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
        // Return the most recent request (any status) so the hosteller sees the live
        // outcome — including APPROVED (→ payment) and REJECTED — not just pending ones.
        return requestRepo.findByHostellerUserIdOrderByCreatedAtDesc(userId).stream()
                .findFirst()
                .orElseThrow(() -> new AppException("No active booking request found", HttpStatus.NOT_FOUND));
    }

    public List<java.util.Map<String, Object>> getRequestsForHostel(Long hostelId, String status) {
        List<BookingRequest> requests = (status != null && !status.isEmpty())
                ? requestRepo.findByHostelIdAndStatusOrderByCreatedAtDesc(hostelId, status)
                : requestRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
        List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        for (BookingRequest r : requests) {
            // Privacy: personal details stay hidden until the hostel approves the booking.
            boolean approved = "APPROVED".equals(r.getStatus());
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("hostellerProfileId", r.getHostellerProfileId());
            m.put("status", r.getStatus());
            m.put("bedId", r.getBedId());
            m.put("roomId", r.getRoomId());
            m.put("floorId", r.getFloorId());
            m.put("checkInDate", r.getCheckInDate());
            m.put("expiresAt", r.getExpiresAt());
            m.put("preferenceNotes", r.getPreferenceNotes());
            m.put("rejectionReason", r.getRejectionReason());
            m.put("createdAt", r.getCreatedAt());
            m.put("detailsLocked", !approved);
            if (r.getReassignedBedId() != null) {
                m.put("reassignedBedId", r.getReassignedBedId());
                bedRepo.findById(r.getReassignedBedId()).ifPresent(b -> {
                    m.put("reassignedBedName", b.getBedName());
                    roomRepo.findById(b.getRoomId()).ifPresent(rm -> {
                        m.put("reassignedRoomName", rm.getRoomName());
                        floorRepo.findById(rm.getFloorId()).ifPresent(f -> m.put("reassignedFloorName", f.getFloorName()));
                    });
                });
            }
            // Resolve unit names (not personal data)
            bedRepo.findById(r.getBedId()).ifPresent(b -> m.put("bedName", b.getBedName()));
            roomRepo.findById(r.getRoomId()).ifPresent(rm -> m.put("roomName", rm.getRoomName()));
            floorRepo.findById(r.getFloorId()).ifPresent(f -> m.put("floorName", f.getFloorName()));
            // Applicant identity: masked while pending, full once approved
            profileRepo.findById(r.getHostellerProfileId()).ifPresent(p -> {
                String first = p.getFirstName() != null ? p.getFirstName() : "";
                String last = p.getLastName() != null ? p.getLastName() : "";
                if (approved) {
                    m.put("applicantName", (first + " " + last).trim());
                    m.put("mobile", p.getMobile());
                    m.put("email", p.getEmail());
                } else {
                    // first name + last initial only — no contact / ID / address exposed
                    String masked = first + (last.isEmpty() ? "" : " " + last.charAt(0) + ".");
                    m.put("applicantName", masked.isEmpty() ? "New Applicant" : masked);
                }
            });
            out.add(m);
        }
        return out;
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
        // After a reassign the original bed is already free and the new one is RESERVED — release that one
        Long bedToRelease = req.getReassignedBedId() != null ? req.getReassignedBedId() : req.getBedId();
        bedRepo.findById(bedToRelease).ifPresent(b -> {
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
        // Only open requests can move: reassigning an APPROVED request used to free its OCCUPIED bed
        if (!"PENDING_APPROVAL".equals(req.getStatus()) && !"REASSIGNED".equals(req.getStatus())) {
            throw new AppException("Request cannot be reassigned in status: " + req.getStatus(), HttpStatus.CONFLICT);
        }
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
