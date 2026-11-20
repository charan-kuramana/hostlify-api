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
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.repository.PaymentDueRepository;
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
    private final PaymentDueRepository dueRepo;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(BookingCheckinRepository bookingRepo,
                          BedRepository bedRepo,
                          HostellerProfileRepository profileRepo,
                          AuthUserRepository authUserRepo,
                          HostelRepository hostelRepo,
                          RoomRepository roomRepo,
                          PaymentDueRepository dueRepo,
                          ApplicationEventPublisher eventPublisher) {
        this.bookingRepo = bookingRepo;
        this.bedRepo = bedRepo;
        this.profileRepo = profileRepo;
        this.authUserRepo = authUserRepo;
        this.hostelRepo = hostelRepo;
        this.roomRepo = roomRepo;
        this.dueRepo = dueRepo;
        this.eventPublisher = eventPublisher;
    }

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private com.hostlify.module.hostel.policy.PolicyService policies;

    /**
     * CRITICAL: Allocate a bed to a pending hosteller.
     * Uses SERIALIZABLE isolation + PESSIMISTIC_WRITE lock on the bed row.
     * All steps are atomic — if any fails, everything rolls back.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BookingCheckin allocateBed(Long hostellerAuthUserId, Long bedId,
                                      LocalDate checkInDate, BigDecimal rentAmount,
                                      BigDecimal advanceAmount, String advancePaymentMode) {
        return allocateBed(hostellerAuthUserId, bedId, checkInDate, rentAmount, advanceAmount, advancePaymentMode, null);
    }

    /** A short stay: planned check-out date and the total for the whole stay (billed once, no monthly rent). */
    public record ShortStay(LocalDate checkOutDate, int days, BigDecimal amount, String description) {}

    @Transactional
    public BookingCheckin allocateBed(Long hostellerAuthUserId, Long bedId,
                                      LocalDate checkInDate, BigDecimal rentAmount,
                                      BigDecimal advanceAmount, String advancePaymentMode, ShortStay shortStay) {
        // 1. A new applicant, or someone who has checked out of their previous hostel (moving)
        HostellerProfile profile = profileRepo.findByAuthUserId(hostellerAuthUserId)
                .orElseThrow(() -> new AppException("Hosteller profile not found", HttpStatus.NOT_FOUND));

        if (bookingRepo.findFirstByHostellerIdAndStatus(profile.getId(), "ACTIVE").isPresent()) {
            String where = profile.getHostelId() == null ? "another hostel"
                    : hostelRepo.findById(profile.getHostelId()).map(Hostel::getName).orElse("another hostel");
            throw new AppException("This resident still has an active stay at " + where
                    + ". They need to check out there first (they can request it from their app).", HttpStatus.CONFLICT);
        }
        if (!"PENDING".equals(profile.getStatus()) && !"CHECKED_OUT".equals(profile.getStatus())) {
            throw new AppException("This resident cannot be allocated a bed right now (status: " + profile.getStatus() + ")",
                    HttpStatus.CONFLICT);
        }

        AuthUser authUser = authUserRepo.findById(hostellerAuthUserId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        // 2. Acquire PESSIMISTIC_WRITE lock on the bed
        Bed bed = bedRepo.findByIdForUpdate(bedId)
                .orElseThrow(() -> new AppException("Bed not found", HttpStatus.NOT_FOUND));

        // AVAILABLE → direct staff allocation. RESERVED → approving a booking request that
        // already reserved this bed for this hosteller. Both transition to OCCUPIED.
        if (!"AVAILABLE".equals(bed.getStatus()) && !"RESERVED".equals(bed.getStatus())) {
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
        // No rent typed by staff → the price the resident was shown for this room type (not the hostel default)
        booking.setRentAmount(rentAmount != null ? rentAmount : policies.advertisedRent(room, hostel));
        try {
            booking.setTermsJson(policies.termsSnapshot(hostel.getId(), bed.getId()));
        } catch (RuntimeException e) {
            // terms are informational; never block a check-in on them
        }
        booking.setAdvanceAmount(advanceAmount != null ? advanceAmount : BigDecimal.ZERO);
        booking.setStatus("ACTIVE");
        if (shortStay != null) {
            booking.setRentFrequency("SHORT");
            booking.setCustomFrequencyDays(shortStay.days());
            booking.setExpectedCheckoutDate(shortStay.checkOutDate());
            booking.setRentAmount(shortStay.amount());
        }
        BookingCheckin saved = bookingRepo.save(booking);

        // 6b. Generate the first rent due (move-in month) so the hosteller has something to pay.
        //     A short stay is billed once for the whole stay instead.
        LocalDate checkin = saved.getCheckinDate();
        int month = checkin.getMonthValue();
        int year = checkin.getYear();
        // Only a rent due from THIS hostel counts (a resident moving mid-month still owes the new hostel)
        boolean dueExists = shortStay == null && dueRepo.findRentDue(profile.getId(), year, month)
                .filter(d -> java.util.Objects.equals(d.getHostelId(), hostel.getId())).isPresent();
        if (shortStay != null) {
            PaymentDue due = new PaymentDue();
            due.setHostellerId(profile.getId());
            due.setBookingId(saved.getId());
            due.setHostelId(hostel.getId());
            due.setPeriodMonth(month);
            due.setPeriodYear(year);
            due.setDueDate(checkin);
            due.setRentAmount(shortStay.amount());
            due.setDescription(shortStay.description());
            due.setStatus("UNPAID");
            dueRepo.save(due);
        } else if (!dueExists) {
            PaymentDue due = new PaymentDue();
            due.setHostellerId(profile.getId());
            due.setBookingId(saved.getId());
            due.setHostelId(hostel.getId());
            due.setPeriodMonth(month);
            due.setPeriodYear(year);
            due.setDueDate(checkin); // first rent is due at move-in
            due.setRentAmount(saved.getRentAmount());
            due.setStatus("UNPAID");
            dueRepo.save(due);
        }

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
            // The login stays active: a former resident still needs receipts, their deposit refund,
            // to review the stay, and to book their next hostel.
            authUser = authUserRepo.findById(profile.getAuthUserId()).orElse(null);
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
