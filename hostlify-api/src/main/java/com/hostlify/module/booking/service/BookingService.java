package com.hostlify.module.booking.service;

import com.hostlify.module.auth.entity.AuthUser;
import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.event.BookingCheckedOutEvent;
import com.hostlify.module.booking.event.BookingCreatedEvent;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hostel.entity.Bed;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.entity.Room;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.exception.AppException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private final BookingCheckinRepository bookingRepo;
    private final BedRepository bedRepo;
    private final HostellerProfileRepository profileRepo;
    private final AuthUserRepository authUserRepo;
    private final HostelRepository hostelRepo;
    private final RoomRepository roomRepo;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(BookingCheckinRepository bookingRepo,
                          BedRepository bedRepo,
                          HostellerProfileRepository profileRepo,
                          AuthUserRepository authUserRepo,
                          HostelRepository hostelRepo,
                          RoomRepository roomRepo,
                          ApplicationEventPublisher eventPublisher) {
        this.bookingRepo = bookingRepo;
        this.bedRepo = bedRepo;
        this.profileRepo = profileRepo;
        this.authUserRepo = authUserRepo;
        this.hostelRepo = hostelRepo;
        this.roomRepo = roomRepo;
        this.eventPublisher = eventPublisher;
    }

    /**
     * CRITICAL: Allocate a bed to a pending hosteller.
     * Uses SERIALIZABLE isolation + PESSIMISTIC_WRITE lock on the bed row.
     * All steps are atomic — if any fails, everything rolls back.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingCheckin allocateBed(Long hostellerAuthUserId, Long bedId,
                                      LocalDate checkInDate, BigDecimal rentAmount,
                                      BigDecimal advanceAmount, String advancePaymentMode) {
        // 1. Check hosteller is PENDING
        HostellerProfile profile = profileRepo.findByAuthUserId(hostellerAuthUserId)
                .orElseThrow(() -> new AppException("Hosteller profile not found", HttpStatus.NOT_FOUND));

        if (!"PENDING".equals(profile.getStatus())) {
            throw new AppException("Hosteller is not in PENDING status (current: " + profile.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }

        AuthUser authUser = authUserRepo.findById(hostellerAuthUserId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        // 2. Acquire PESSIMISTIC_WRITE lock on the bed
        Bed bed = bedRepo.findByIdForUpdate(bedId)
                .orElseThrow(() -> new AppException("Bed not found", HttpStatus.NOT_FOUND));

        if (!"AVAILABLE".equals(bed.getStatus())) {
            throw new AppException("Bed is not available (current status: " + bed.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }

        // 3. Mark bed as OCCUPIED
        bed.setStatus("OCCUPIED");
        bedRepo.save(bed);

        // 4. Update hosteller profile to ACTIVE
        Room room = roomRepo.findById(bed.getRoomId())
                .orElseThrow(() -> new AppException("Room not found", HttpStatus.NOT_FOUND));
        Hostel hostel = hostelRepo.findById(bed.getHostelId())
                .orElseThrow(() -> new AppException("Hostel not found", HttpStatus.NOT_FOUND));

        profile.setStatus("ACTIVE");
        profile.setHostelId(hostel.getId());
        profile.setJoinedDate(checkInDate);
        profileRepo.save(profile);

        // 5. Update auth_users status to ACTIVE
        authUser.setStatus("ACTIVE");
        authUserRepo.save(authUser);

        // 6. Create booking record
        BookingCheckin booking = new BookingCheckin();
        booking.setHostellerId(profile.getId());
        booking.setHostelId(hostel.getId());
        booking.setFloorId(room.getFloorId());
        booking.setRoomId(room.getId());
        booking.setBedId(bed.getId());
        booking.setCheckinDate(checkInDate != null ? checkInDate : LocalDate.now());
        booking.setRentAmount(rentAmount != null ? rentAmount : hostel.getDefaultRent());
        booking.setAdvanceAmount(advanceAmount != null ? advanceAmount : BigDecimal.ZERO);
        booking.setStatus("ACTIVE");
        BookingCheckin saved = bookingRepo.save(booking);

        // 7. Publish event (async notification will pick it up)
        eventPublisher.publishEvent(new BookingCreatedEvent(
                this, saved,
                authUser.getEmail(),
                authUser.getMobile(),
                profile.getFirstName() + " " + (profile.getLastName() != null ? profile.getLastName() : ""),
                hostel.getName(),
                room.getRoomName(),
                bed.getBedName()
        ));

        return saved;
    }

    public List<HostellerProfile> getPendingAllocations(Long hostelId) {
        // All profiles with status=PENDING linked to this hostel (or no hostel yet)
        // In practice, filter by hostelId from registration token
        return profileRepo.findByStatusOrderByCreatedAtDesc("PENDING");
    }

    @Transactional
    public BookingCheckin checkout(Long bookingId, String reason) {
        BookingCheckin booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new AppException("Booking not found", HttpStatus.NOT_FOUND));

        if (!"ACTIVE".equals(booking.getStatus())) {
            throw new AppException("Booking is not active", HttpStatus.CONFLICT);
        }

        // Free up the bed
        bedRepo.findById(booking.getBedId()).ifPresent(bed -> {
            bed.setStatus("AVAILABLE");
            bed.setOccupiedByBookingId(null);
            bedRepo.save(bed);
        });

        // Update hosteller status
        HostellerProfile profile = profileRepo.findById(booking.getHostellerId()).orElse(null);
        AuthUser authUser = null;
        if (profile != null) {
            profile.setStatus("CHECKED_OUT");
            profileRepo.save(profile);
            authUser = authUserRepo.findById(profile.getAuthUserId()).orElse(null);
            if (authUser != null) {
                authUser.setStatus("INACTIVE");
                authUserRepo.save(authUser);
            }
        }

        booking.setStatus("CHECKED_OUT");
        booking.setCheckoutDate(LocalDate.now());
        booking.setCheckoutReason(reason);
        BookingCheckin saved = bookingRepo.save(booking);

        // Publish event
        if (profile != null && authUser != null) {
            final AuthUser finalAuthUser = authUser;
            eventPublisher.publishEvent(new BookingCheckedOutEvent(
                    this, saved,
                    finalAuthUser.getEmail(),
                    profile.getFirstName(),
                    booking.getAdvanceAmount()
            ));
        }

        return saved;
    }

    @Transactional
    public BookingCheckin cancelAllocation(Long bookingId) {
        BookingCheckin booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new AppException("Booking not found", HttpStatus.NOT_FOUND));

        if ("CHECKED_OUT".equals(booking.getStatus()) || "CANCELLED".equals(booking.getStatus())) {
            throw new AppException("Booking already " + booking.getStatus(), HttpStatus.CONFLICT);
        }

        bedRepo.findById(booking.getBedId()).ifPresent(bed -> {
            bed.setStatus("AVAILABLE");
            bedRepo.save(bed);
        });

        profileRepo.findById(booking.getHostellerId()).ifPresent(p -> {
            p.setStatus("PENDING");
            profileRepo.save(p);
        });

        booking.setStatus("CANCELLED");
        return bookingRepo.save(booking);
    }

    public List<BookingCheckin> getByHostel(Long hostelId) {
        return bookingRepo.findByHostelIdOrderByCreatedAtDesc(hostelId);
    }

    public List<BookingCheckin> getActiveByHostel(Long hostelId) {
        return bookingRepo.findByHostelIdAndStatus(hostelId, "ACTIVE");
    }

    public List<BookingCheckin> getByHosteller(Long hostellerId) {
        return bookingRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId);
    }

    public BookingCheckin getMyBooking(Long authUserId) {
        HostellerProfile profile = profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        return bookingRepo.findFirstByHostellerIdAndStatus(profile.getId(), "ACTIVE")
                .orElseThrow(() -> new AppException("No active booking found", HttpStatus.NOT_FOUND));
    }

    public BookingCheckin getById(Long id) {
        return bookingRepo.findById(id)
                .orElseThrow(() -> new AppException("Booking not found", HttpStatus.NOT_FOUND));
    }

    /** Legacy: direct checkin without allocation flow */
    @Transactional
    public BookingCheckin checkin(BookingCheckin booking) {
        booking.setStatus("ACTIVE");
        return bookingRepo.save(booking);
    }
}
