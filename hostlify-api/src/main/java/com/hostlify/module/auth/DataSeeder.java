package com.hostlify.module.auth;

import com.hostlify.module.admin.entity.FeatureFlag;
import com.hostlify.module.admin.entity.Subscription;
import com.hostlify.module.admin.repository.FeatureFlagRepository;
import com.hostlify.module.admin.repository.SubscriptionRepository;
import com.hostlify.module.announcement.entity.Announcement;
import com.hostlify.module.announcement.repository.AnnouncementRepository;
import com.hostlify.module.auth.entity.AuthUser;
import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.complaint.entity.ComplaintMaintenance;
import com.hostlify.module.complaint.entity.ComplaintRecord;
import com.hostlify.module.complaint.repository.ComplaintMaintenanceRepository;
import com.hostlify.module.complaint.repository.ComplaintRecordRepository;
import com.hostlify.module.expense.entity.ExpenseEmployee;
import com.hostlify.module.expense.entity.ExpenseRecord;
import com.hostlify.module.expense.entity.ExpenseSalaryRecord;
import com.hostlify.module.expense.repository.ExpenseEmployeeRepository;
import com.hostlify.module.expense.repository.ExpenseRecordRepository;
import com.hostlify.module.expense.repository.ExpenseSalaryRecordRepository;
import com.hostlify.module.hostel.entity.*;
import com.hostlify.module.hostel.repository.*;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.leave.entity.LeaveRequest;
import com.hostlify.module.leave.repository.LeaveRequestRepository;
import com.hostlify.module.menu.entity.MenuItem;
import com.hostlify.module.menu.entity.WeeklyMenu;
import com.hostlify.module.menu.repository.MenuItemRepository;
import com.hostlify.module.menu.repository.WeeklyMenuRepository;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import com.hostlify.module.payment.repository.PaymentRecordRepository;
import com.hostlify.module.referral.entity.ReferralCode;
import com.hostlify.module.referral.entity.ReferralRecord;
import com.hostlify.module.referral.repository.ReferralCodeRepository;
import com.hostlify.module.referral.repository.ReferralRecordRepository;
import com.hostlify.module.review.entity.HostelReview;
import com.hostlify.module.review.repository.HostelReviewRepository;
import com.hostlify.module.service.entity.CleaningRequest;
import com.hostlify.module.service.entity.ParkingRecord;
import com.hostlify.module.service.repository.CleaningRequestRepository;
import com.hostlify.module.service.repository.ParkingRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private HostelOwnerRepository hostelOwnerRepository;
    @Autowired private HostelRepository hostelRepository;
    @Autowired private FloorRepository floorRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private StaffAssignmentRepository staffAssignmentRepository;
    @Autowired private HostellerProfileRepository hostellerProfileRepository;
    @Autowired private BookingCheckinRepository bookingCheckinRepository;
    @Autowired private PaymentDueRepository paymentDueRepository;
    @Autowired private PaymentRecordRepository paymentRecordRepository;
    @Autowired private ComplaintRecordRepository complaintRecordRepository;
    @Autowired private ComplaintMaintenanceRepository complaintMaintenanceRepository;
    @Autowired private AnnouncementRepository announcementRepository;
    @Autowired private WeeklyMenuRepository weeklyMenuRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private ReferralCodeRepository referralCodeRepository;
    @Autowired private ReferralRecordRepository referralRecordRepository;
    @Autowired private ParkingRecordRepository parkingRecordRepository;
    @Autowired private CleaningRequestRepository cleaningRequestRepository;
    @Autowired private HostelReviewRepository hostelReviewRepository;
    @Autowired private ExpenseEmployeeRepository expenseEmployeeRepository;
    @Autowired private ExpenseRecordRepository expenseRecordRepository;
    @Autowired private ExpenseSalaryRecordRepository expenseSalaryRecordRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private FeatureFlagRepository featureFlagRepository;

    @Override
    public void run(String... args) throws Exception {
        seedSuperAdmin();
        AuthUser staffUser = seedStaff();
        seedHosteller();
        seedDemoData(staffUser);
    }

    // ─── Auth Users ───────────────────────────────────────────────────────────

    private void seedSuperAdmin() {
        if (authUserRepository.existsByEmail("superadmin@test.com")) return;
        AuthUser sa = new AuthUser();
        sa.setUuid(UUID.randomUUID().toString());
        sa.setEmail("superadmin@test.com");
        sa.setPasswordHash(passwordEncoder.encode("Test@1234"));
        sa.setRole("SUPER_ADMIN");
        sa.setStatus("ACTIVE");
        authUserRepository.save(sa);
        System.out.println("[DEV] Seeded SUPER_ADMIN: superadmin@test.com / Test@1234");
    }

    private AuthUser seedStaff() {
        if (authUserRepository.existsByEmail("admin@test.com")) {
            return authUserRepository.findByEmail("admin@test.com").orElse(null);
        }
        AuthUser staff = new AuthUser();
        staff.setUuid(UUID.randomUUID().toString());
        staff.setEmail("admin@test.com");
        staff.setPasswordHash(passwordEncoder.encode("Test@1234"));
        staff.setRole("HOSTEL_STAFF");
        staff.setSubRole("OWNER");
        staff.setStatus("ACTIVE");
        staff = authUserRepository.save(staff);
        System.out.println("[DEV] Seeded HOSTEL_STAFF (OWNER): admin@test.com / Test@1234");
        return staff;
    }

    private void seedHosteller() {
        if (authUserRepository.existsByEmail("hosteller@test.com")) return;
        AuthUser hosteller = new AuthUser();
        hosteller.setUuid(UUID.randomUUID().toString());
        hosteller.setEmail("hosteller@test.com");
        hosteller.setPasswordHash(passwordEncoder.encode("Test@1234"));
        hosteller.setRole("HOSTELLER");
        hosteller.setStatus("ACTIVE");
        hosteller = authUserRepository.save(hosteller);

        HostellerProfile profile = new HostellerProfile();
        profile.setAuthUserId(hosteller.getId());
        profile.setFirstName("Arjun");
        profile.setLastName("Sharma");
        profile.setEmail("hosteller@test.com");
        profile.setMobile("9876543210");
        profile.setStatus("ACTIVE");
        hostellerProfileRepository.save(profile);
        System.out.println("[DEV] Seeded HOSTELLER (ACTIVE): hosteller@test.com / Test@1234");
    }

    // ─── Full Demo Data ───────────────────────────────────────────────────────

    private void seedDemoData(AuthUser staffUser) {
        if (hostelRepository.existsByName("Sunrise Premier Hostel")) {
            System.out.println("[DEV] Demo data already seeded. Skipping.");
            return;
        }

        System.out.println("[DEV] Seeding full demo data...");

        // ── Owner & subscription ──────────────────────────────────────────────
        HostelOwner owner = new HostelOwner();
        owner.setAuthUserId(staffUser.getId());
        owner.setBusinessName("Sunrise Hostels Pvt. Ltd.");
        owner.setOwnerName("Rajesh Mehta");
        owner.setMobile("9900001234");
        owner.setEmail("admin@test.com");
        owner.setSubscriptionPlan("PROFESSIONAL");
        owner.setPlanExpiresAt(LocalDateTime.now().plusYears(1));
        owner.setStatus("ACTIVE");
        owner = hostelOwnerRepository.save(owner);

        Subscription sub = new Subscription();
        sub.setOwnerId(owner.getId());
        sub.setPlanName("PROFESSIONAL");
        sub.setMaxHostels(3);
        sub.setMaxBeds(200);
        sub.setPriceMonthly(new BigDecimal("2999.00"));
        subscriptionRepository.save(sub);

        AuthUser saUser = authUserRepository.findByEmail("superadmin@test.com").orElse(null);
        Long saId = saUser != null ? saUser.getId() : staffUser.getId();
        seedFeatureFlags(owner.getId(), saId);

        // ── Hostel ────────────────────────────────────────────────────────────
        Hostel hostel = new Hostel();
        hostel.setOwnerId(owner.getId());
        hostel.setName("Sunrise Premier Hostel");
        hostel.setHostelType("MIXED");
        hostel.setCity("Bengaluru");
        hostel.setPincode("560001");
        hostel.setAddress("No. 12, MG Road, Shivajinagar, Bengaluru - 560001");
        hostel.setTotalFloors(3);
        hostel.setTotalRooms(9);
        hostel.setTotalBeds(18);
        hostel.setDefaultRent(new BigDecimal("8000.00"));
        hostel = hostelRepository.save(hostel);
        final Long hostelId = hostel.getId();

        // ── Staff assignment ──────────────────────────────────────────────────
        StaffAssignment assignment = new StaffAssignment();
        assignment.setStaffUserId(staffUser.getId());
        assignment.setHostelId(hostelId);
        assignment.setOwnerId(owner.getId());
        assignment.setActive(true);
        assignment.setAssignedAt(LocalDateTime.now());
        assignment.setAssignedBy(staffUser.getId());
        staffAssignmentRepository.save(assignment);

        // ── Additional staff users (ADMIN + MANAGER sub-roles) ────────────────
        seedStaffUser("manager@test.com", "MANAGER", hostelId, owner.getId(), staffUser.getId());
        seedStaffUser("staffadmin@test.com", "ADMIN", hostelId, owner.getId(), staffUser.getId());

        // ── Floors ────────────────────────────────────────────────────────────
        Floor gFloor = makeFloor(hostelId, "Ground Floor", 0);
        Floor fFloor = makeFloor(hostelId, "1st Floor", 1);
        Floor sFloor = makeFloor(hostelId, "2nd Floor", 2);

        // ── Rooms (3 per floor) ───────────────────────────────────────────────
        // Ground Floor rooms
        Room gR1 = makeRoom(gFloor.getId(), hostelId, "G-101", "TRIPLE", 3, new BigDecimal("7500.00"));
        Room gR2 = makeRoom(gFloor.getId(), hostelId, "G-102", "DOUBLE", 2, new BigDecimal("8500.00"));
        Room gR3 = makeRoom(gFloor.getId(), hostelId, "G-103", "SINGLE", 1, new BigDecimal("10000.00"));
        // 1st Floor rooms
        Room fR1 = makeRoom(fFloor.getId(), hostelId, "1-101", "TRIPLE", 3, new BigDecimal("7500.00"));
        Room fR2 = makeRoom(fFloor.getId(), hostelId, "1-102", "DOUBLE", 2, new BigDecimal("8500.00"));
        Room fR3 = makeRoom(fFloor.getId(), hostelId, "1-103", "SINGLE", 1, new BigDecimal("10000.00"));
        // 2nd Floor rooms
        Room sR1 = makeRoom(sFloor.getId(), hostelId, "2-101", "TRIPLE", 3, new BigDecimal("7500.00"));
        Room sR2 = makeRoom(sFloor.getId(), hostelId, "2-102", "DOUBLE", 2, new BigDecimal("8500.00"));
        Room sR3 = makeRoom(sFloor.getId(), hostelId, "2-103", "SINGLE", 1, new BigDecimal("10000.00"));

        // ── Beds ──────────────────────────────────────────────────────────────
        // G-101: 3 beds
        Bed gR1Ba = makeBed(gR1.getId(), hostelId, "Bed A", "OCCUPIED");
        Bed gR1Bb = makeBed(gR1.getId(), hostelId, "Bed B", "OCCUPIED");
        Bed gR1Bc = makeBed(gR1.getId(), hostelId, "Bed C", "OCCUPIED");
        // G-102: 2 beds
        Bed gR2Ba = makeBed(gR2.getId(), hostelId, "Bed A", "OCCUPIED");
        Bed gR2Bb = makeBed(gR2.getId(), hostelId, "Bed B", "AVAILABLE");
        // G-103: 1 bed
        makeBed(gR3.getId(), hostelId, "Bed A", "AVAILABLE");
        // 1-101: 3 beds
        Bed fR1Ba = makeBed(fR1.getId(), hostelId, "Bed A", "OCCUPIED");
        Bed fR1Bb = makeBed(fR1.getId(), hostelId, "Bed B", "RESERVED");
        makeBed(fR1.getId(), hostelId, "Bed C", "AVAILABLE");
        // 1-102: 2 beds
        makeBed(fR2.getId(), hostelId, "Bed A", "AVAILABLE");
        makeBed(fR2.getId(), hostelId, "Bed B", "AVAILABLE");
        // 1-103: 1 bed
        makeBed(fR3.getId(), hostelId, "Bed A", "AVAILABLE");
        // 2-101: 3 beds (all available)
        makeBed(sR1.getId(), hostelId, "Bed A", "AVAILABLE");
        makeBed(sR1.getId(), hostelId, "Bed B", "AVAILABLE");
        makeBed(sR1.getId(), hostelId, "Bed C", "AVAILABLE");
        // 2-102: 2 beds (all available)
        makeBed(sR2.getId(), hostelId, "Bed A", "AVAILABLE");
        makeBed(sR2.getId(), hostelId, "Bed B", "AVAILABLE");
        // 2-103: 1 bed
        makeBed(sR3.getId(), hostelId, "Bed A", "AVAILABLE");

        // Update room occupancy
        gR1.setCurrentOccupancy(3); roomRepository.save(gR1);
        gR2.setCurrentOccupancy(1); roomRepository.save(gR2);
        fR1.setCurrentOccupancy(1); roomRepository.save(fR1);

        // ── Hostellers ────────────────────────────────────────────────────────
        // Update existing hosteller@test.com profile
        HostellerProfile arjunProfile = hostellerProfileRepository
                .findByEmail("hosteller@test.com")
                .orElse(null);
        if (arjunProfile != null) {
            arjunProfile.setFirstName("Arjun");
            arjunProfile.setLastName("Sharma");
            arjunProfile.setMobile("9876543210");
            arjunProfile.setDateOfBirth(LocalDate.of(1999, 7, 15));
            arjunProfile.setUserType("STUDENT");
            arjunProfile.setCompanyCollege("Bangalore University");
            arjunProfile.setPermanentAddress("45, Gandhi Nagar, Mysuru, Karnataka");
            arjunProfile.setCity("Bengaluru");
            arjunProfile.setPincode("560001");
            arjunProfile.setIdProofType("AADHAAR");
            arjunProfile.setIdProofNumber("1234-5678-9012");
            arjunProfile.setJoinedDate(LocalDate.of(2025, 9, 1));
            arjunProfile.setHostelId(hostelId);
            arjunProfile.setStatus("ACTIVE");
            arjunProfile = hostellerProfileRepository.save(arjunProfile);
        }

        // Additional hostellers
        HostellerProfile raviProfile   = seedHostellerUser("ravi.kumar@test.com",   "Ravi",   "Kumar",   "9876500001", hostelId, LocalDate.of(2025, 10, 1));
        HostellerProfile priyaProfile  = seedHostellerUser("priya.singh@test.com",  "Priya",  "Singh",   "9876500002", hostelId, LocalDate.of(2025, 10, 15));
        HostellerProfile anilProfile   = seedHostellerUser("anil.mehta@test.com",   "Anil",   "Mehta",   "9876500003", hostelId, LocalDate.of(2025, 11, 1));
        HostellerProfile deepaProfile  = seedHostellerUser("deepa.nair@test.com",   "Deepa",  "Nair",    "9876500004", hostelId, LocalDate.of(2025, 11, 15));
        HostellerProfile kiranProfile  = seedHostellerUser("kiran.rao@test.com",    "Kiran",  "Rao",     "9876500005", hostelId, LocalDate.of(2026, 1, 5));
        // Pending applicant
        HostellerProfile vikramProfile = seedHostellerUser("vikram.sharma@test.com","Vikram", "Sharma",  "9876500006", hostelId, LocalDate.of(2026, 3, 1));

        if (arjunProfile == null) {
            System.out.println("[DEV] Warning: could not find hosteller@test.com profile to update.");
            return;
        }

        // ── Bookings ──────────────────────────────────────────────────────────
        BookingCheckin arjunBooking = makeBooking(arjunProfile.getId(), hostelId, gFloor.getId(), gR1.getId(), gR1Ba.getId(),
                LocalDate.of(2025, 9, 1), new BigDecimal("7500.00"));
        makeBooking(raviProfile.getId(),   hostelId, gFloor.getId(), gR1.getId(), gR1Bb.getId(), LocalDate.of(2025, 10, 1),  new BigDecimal("7500.00"));
        makeBooking(priyaProfile.getId(),  hostelId, gFloor.getId(), gR1.getId(), gR1Bc.getId(), LocalDate.of(2025, 10, 15), new BigDecimal("7500.00"));
        makeBooking(anilProfile.getId(),   hostelId, gFloor.getId(), gR2.getId(), gR2Ba.getId(), LocalDate.of(2025, 11, 1),  new BigDecimal("8500.00"));
        makeBooking(deepaProfile.getId(),  hostelId, fFloor.getId(), fR1.getId(), fR1Ba.getId(), LocalDate.of(2025, 11, 15), new BigDecimal("7500.00"));
        BookingCheckin kiranBooking = makeBooking(kiranProfile.getId(), hostelId, fFloor.getId(), fR1.getId(), fR1Bb.getId(), LocalDate.of(2026, 1, 5), new BigDecimal("7500.00"));
        // Pending allocation for Vikram
        BookingCheckin vikramBooking = makePendingBooking(vikramProfile.getId(), hostelId, fFloor.getId(), fR2.getId(), gR2Bb.getId(), LocalDate.of(2026, 3, 10), new BigDecimal("8500.00"));

        // Mark kiran's bed as occupied (already RESERVED, now ACTIVE)
        kiranBooking.setStatus("ACTIVE");
        bookingCheckinRepository.save(kiranBooking);

        // ── Payment Dues & Records (for Arjun - main demo user) ───────────────
        int[] paidMonths = {12, 1, 2}; // Dec 2025, Jan 2026, Feb 2026
        int[] paidYears  = {2025, 2026, 2026};
        for (int i = 0; i < paidMonths.length; i++) {
            int m = paidMonths[i]; int y = paidYears[i];
            PaymentDue due = makeDue(arjunProfile.getId(), arjunBooking.getId(), hostelId, m, y,
                    LocalDate.of(y, m == 12 ? 12 : m, 1).plusMonths(1),
                    new BigDecimal("7500.00"), "PAID");
            PaymentRecord rec = new PaymentRecord();
            rec.setHostellerId(arjunProfile.getId());
            rec.setHostelId(hostelId);
            rec.setDueId(due.getId());
            rec.setAmount(new BigDecimal("7500.00"));
            rec.setPaymentMethod("CASH");
            rec.setTransactionRef("CASH-" + String.format("%04d", i + 1));
            rec.setPaymentDate(LocalDate.of(y, m == 12 ? 12 : m, 28));
            rec.setStatus("SUCCESS");
            paymentRecordRepository.save(rec);
        }
        // Current month due (unpaid)
        makeDue(arjunProfile.getId(), arjunBooking.getId(), hostelId, 3, 2026,
                LocalDate.of(2026, 4, 1), new BigDecimal("7500.00"), "UNPAID");

        // Dues for other hostellers (just current month unpaid)
        makeDue(raviProfile.getId(),  makeBookingIdLookup(raviProfile.getId()),  hostelId, 3, 2026, LocalDate.of(2026, 4, 1), new BigDecimal("7500.00"), "UNPAID");
        makeDue(priyaProfile.getId(), makeBookingIdLookup(priyaProfile.getId()), hostelId, 3, 2026, LocalDate.of(2026, 4, 1), new BigDecimal("7500.00"), "UNPAID");
        makeDue(anilProfile.getId(),  makeBookingIdLookup(anilProfile.getId()),  hostelId, 3, 2026, LocalDate.of(2026, 4, 1), new BigDecimal("8500.00"), "UNPAID");

        // ── Complaints (for Arjun) ────────────────────────────────────────────
        makeComplaint(arjunProfile.getId(), hostelId, gR1.getId(), "Maintenance", "AC not cooling properly",
                "The AC in room G-101 is not cooling properly since last week. It makes loud noise and barely reduces temperature.", "HIGH", "IN_PROGRESS");
        makeComplaint(arjunProfile.getId(), hostelId, gR1.getId(), "Cleanliness", "Common washroom dirty",
                "The washroom on Ground Floor near G-103 has not been cleaned for 3 days. There is a bad odour.", "MEDIUM", "OPEN");
        ComplaintRecord resolved = makeComplaint(arjunProfile.getId(), hostelId, gR1.getId(), "Food", "Dinner quality issue",
                "Dinner served on March 8 had undercooked rice and the dal was too watery. Please improve quality.", "LOW", "RESOLVED");
        resolved.setResolutionNotes("Issue acknowledged. Cook has been instructed. Quality checks added to daily routine.");
        resolved.setResolvedAt(LocalDateTime.of(2026, 3, 10, 10, 30));
        complaintRecordRepository.save(resolved);
        makeComplaint(raviProfile.getId(), hostelId, gR1.getId(), "Maintenance", "Room light not working",
                "The ceiling light in G-101 has not been working since yesterday evening.", "MEDIUM", "OPEN");
        makeComplaint(anilProfile.getId(), hostelId, gR2.getId(), "Security", "Outsiders entering premises",
                "Unknown people are entering the hostel premises during evening hours without any verification.", "HIGH", "OPEN");

        // ── Maintenance Logs ──────────────────────────────────────────────────
        ComplaintMaintenance m1 = new ComplaintMaintenance();
        m1.setHostelId(hostelId); m1.setRoomId(gR1.getId()); m1.setReportedBy(staffUser.getId());
        m1.setCategory("Electrical"); m1.setDescription("Main electrical panel in Ground Floor corridor needs rewiring. Old wiring is causing trips.");
        m1.setEstimatedCost(new BigDecimal("4500.00")); m1.setVendorName("Sai Electricals"); m1.setStatus("IN_PROGRESS");
        complaintMaintenanceRepository.save(m1);

        ComplaintMaintenance m2 = new ComplaintMaintenance();
        m2.setHostelId(hostelId); m2.setRoomId(gR2.getId()); m2.setReportedBy(staffUser.getId());
        m2.setCategory("Plumbing"); m2.setDescription("Water pipeline in Ground Floor bathroom is leaking. Needs immediate repair.");
        m2.setEstimatedCost(new BigDecimal("2000.00")); m2.setActualCost(new BigDecimal("1800.00"));
        m2.setVendorName("Kumar Plumbing Works"); m2.setStatus("COMPLETED");
        m2.setCompletedAt(LocalDateTime.of(2026, 3, 5, 14, 0));
        complaintMaintenanceRepository.save(m2);

        ComplaintMaintenance m3 = new ComplaintMaintenance();
        m3.setHostelId(hostelId); m3.setRoomId(fR1.getId()); m3.setReportedBy(staffUser.getId());
        m3.setCategory("AC / Cooling"); m3.setDescription("2 AC units on 1st Floor are not cooling properly. Gas refill and servicing required.");
        m3.setEstimatedCost(new BigDecimal("6000.00")); m3.setVendorName("Cool Zone Services"); m3.setStatus("OPEN");
        complaintMaintenanceRepository.save(m3);

        // ── Announcements ─────────────────────────────────────────────────────
        makeAnnouncement(hostelId, staffUser.getId(), "April 2026 Fee Reminder",
                "Dear residents, the fee for April 2026 is due on April 1st. Please ensure timely payment to avoid a late charge of ₹500. Pay via the Hostlify app or at the reception.",
                "Fee", "URGENT", LocalDate.of(2026, 3, 15), LocalDate.of(2026, 4, 5));

        makeAnnouncement(hostelId, staffUser.getId(), "Hostel Annual Fest – March 28",
                "We are excited to announce the Annual Hostel Fest on March 28th, 2026! Events include cultural performances, food stalls, gaming zone, and lucky draw. All residents are invited. Register by March 22nd at the reception.",
                "Event", "NORMAL", LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 28));

        makeAnnouncement(hostelId, staffUser.getId(), "Maintenance: Water Supply Interruption",
                "Due to pipeline maintenance work, water supply will be interrupted on March 20th from 10:00 AM to 3:00 PM. Please store water in advance. We apologize for the inconvenience.",
                "Maintenance", "HIGH", LocalDate.of(2026, 3, 17), LocalDate.of(2026, 3, 20));

        makeAnnouncement(hostelId, staffUser.getId(), "Updated Mess Timings Effective April 1",
                "New mess timings starting April 1, 2026: Breakfast 7:30–9:30 AM | Lunch 12:30–2:30 PM | Dinner 7:30–9:30 PM. Weekend brunch available 9:00–11:00 AM. Late entry to mess will not be permitted after closing time.",
                "General", "NORMAL", LocalDate.of(2026, 3, 15), LocalDate.of(2026, 6, 30));

        makeAnnouncement(hostelId, staffUser.getId(), "Gym & Common Area Renovation",
                "The hostel gym and common recreation room will be closed for renovation from March 25 to April 10. New equipment including treadmills, cycle machines, and a foosball table will be installed. Thank you for your patience.",
                "Maintenance", "NORMAL", LocalDate.of(2026, 3, 20), LocalDate.of(2026, 4, 10));

        // ── Weekly Menu ───────────────────────────────────────────────────────
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!weeklyMenuRepository.existsByHostelIdAndWeekStart(hostelId, weekStart)) {
            WeeklyMenu menu = new WeeklyMenu();
            menu.setHostelId(hostelId);
            menu.setWeekStart(weekStart);
            menu.setCreatedBy(staffUser.getId());
            menu = weeklyMenuRepository.save(menu);
            seedMenuItems(menu.getId());
        }

        // ── Leave Requests (for Arjun) ────────────────────────────────────────
        LeaveRequest lr1 = new LeaveRequest();
        lr1.setHostellerId(arjunProfile.getId()); lr1.setBookingId(arjunBooking.getId());
        lr1.setLeaveType("HOME"); lr1.setFromDate(LocalDate.of(2026, 2, 10)); lr1.setToDate(LocalDate.of(2026, 2, 13));
        lr1.setReason("Medical appointment at hometown – need to visit family doctor for annual check-up.");
        lr1.setStatus("APPROVED"); lr1.setReviewedBy(staffUser.getId()); lr1.setReviewedAt(LocalDateTime.of(2026, 2, 7, 9, 0));
        leaveRequestRepository.save(lr1);

        LeaveRequest lr2 = new LeaveRequest();
        lr2.setHostellerId(arjunProfile.getId()); lr2.setBookingId(arjunBooking.getId());
        lr2.setLeaveType("VACATION"); lr2.setFromDate(LocalDate.of(2026, 1, 14)); lr2.setToDate(LocalDate.of(2026, 1, 18));
        lr2.setReason("Family function – attending brother's engagement ceremony at Mysuru.");
        lr2.setStatus("APPROVED"); lr2.setReviewedBy(staffUser.getId()); lr2.setReviewedAt(LocalDateTime.of(2026, 1, 11, 10, 30));
        leaveRequestRepository.save(lr2);

        LeaveRequest lr3 = new LeaveRequest();
        lr3.setHostellerId(arjunProfile.getId()); lr3.setBookingId(arjunBooking.getId());
        lr3.setLeaveType("HOME"); lr3.setFromDate(LocalDate.of(2026, 3, 22)); lr3.setToDate(LocalDate.of(2026, 3, 25));
        lr3.setReason("Going home for Ugadi festival and family gathering.");
        lr3.setStatus("PENDING");
        leaveRequestRepository.save(lr3);

        // Pending leave from another hosteller (for staff Leave management page)
        LeaveRequest lr4 = new LeaveRequest();
        lr4.setHostellerId(raviProfile.getId()); lr4.setBookingId(makeBookingIdLookup(raviProfile.getId()));
        lr4.setLeaveType("PERSONAL"); lr4.setFromDate(LocalDate.of(2026, 3, 18)); lr4.setToDate(LocalDate.of(2026, 3, 20));
        lr4.setReason("Personal work – need to visit college for document submission.");
        lr4.setStatus("PENDING");
        leaveRequestRepository.save(lr4);

        // ── Referral ──────────────────────────────────────────────────────────
        if (!referralCodeRepository.existsByCode("ARJUN2026")) {
            ReferralCode rc = new ReferralCode();
            rc.setHostellerId(arjunProfile.getId()); rc.setCode("ARJUN2026");
            rc.setTotalReferrals(2); rc.setTotalDiscountEarned(new BigDecimal("1000.00"));
            rc = referralCodeRepository.save(rc);

            ReferralRecord rr1 = new ReferralRecord();
            rr1.setReferrerId(arjunProfile.getId()); rr1.setReferredId(raviProfile.getId());
            rr1.setStatus("COMPLETED"); rr1.setDiscountAmount(new BigDecimal("500.00"));
            referralRecordRepository.save(rr1);

            ReferralRecord rr2 = new ReferralRecord();
            rr2.setReferrerId(arjunProfile.getId()); rr2.setReferredId(priyaProfile.getId());
            rr2.setStatus("PENDING"); rr2.setDiscountAmount(new BigDecimal("500.00"));
            referralRecordRepository.save(rr2);
        }

        // ── Parking ───────────────────────────────────────────────────────────
        ParkingRecord parking = new ParkingRecord();
        parking.setHostellerId(arjunProfile.getId()); parking.setHostelId(hostelId);
        parking.setVehicleNumber("KA-01-AB-1234"); parking.setVehicleType("Car");
        parking.setSlotNumber("P-07"); parking.setStatus("ACTIVE");
        parking.setRegisteredAt(LocalDateTime.of(2025, 9, 5, 10, 0));
        parkingRecordRepository.save(parking);

        ParkingRecord parking2 = new ParkingRecord();
        parking2.setHostellerId(raviProfile.getId()); parking2.setHostelId(hostelId);
        parking2.setVehicleNumber("KA-04-MN-5678"); parking2.setVehicleType("Bike");
        parking2.setSlotNumber("P-03"); parking2.setStatus("ACTIVE");
        parking2.setRegisteredAt(LocalDateTime.of(2025, 10, 3, 11, 0));
        parkingRecordRepository.save(parking2);

        // ── Cleaning Requests (for Arjun) ─────────────────────────────────────
        CleaningRequest cr1 = new CleaningRequest();
        cr1.setHostellerId(arjunProfile.getId()); cr1.setHostelId(hostelId);
        cr1.setRoomNumber("G-101"); cr1.setRequestDate(LocalDate.of(2026, 3, 10));
        cr1.setStatus("COMPLETED"); cr1.setNotes("Regular cleaning – please focus on bathroom area.");
        cr1.setCompletedBy(staffUser.getId());
        cleaningRequestRepository.save(cr1);

        CleaningRequest cr2 = new CleaningRequest();
        cr2.setHostellerId(arjunProfile.getId()); cr2.setHostelId(hostelId);
        cr2.setRoomNumber("G-101"); cr2.setRequestDate(LocalDate.of(2026, 3, 16));
        cr2.setStatus("PENDING"); cr2.setNotes("Deep cleaning needed – have exams coming up and need a fresh room.");
        cleaningRequestRepository.save(cr2);

        CleaningRequest cr3 = new CleaningRequest();
        cr3.setHostellerId(priyaProfile.getId()); cr3.setHostelId(hostelId);
        cr3.setRoomNumber("G-101"); cr3.setRequestDate(LocalDate.of(2026, 3, 15));
        cr3.setStatus("SCHEDULED"); cr3.setNotes("Please clean after 11 AM.");
        cleaningRequestRepository.save(cr3);

        // ── Reviews ───────────────────────────────────────────────────────────
        makeReview(arjunProfile.getId(), hostelId, arjunBooking.getId(), 4,
                "Overall a great hostel! Clean rooms, good food, and helpful staff. The AC could be better maintained but other than that no complaints. Would recommend to friends.");
        makeReview(raviProfile.getId(), hostelId, makeBookingIdLookup(raviProfile.getId()), 5,
                "Excellent hostel with all amenities. The mess food is amazing especially the South Indian breakfast. Very safe and secure premises. Staff is very responsive.");
        makeReview(priyaProfile.getId(), hostelId, makeBookingIdLookup(priyaProfile.getId()), 4,
                "Good hostel with nice environment. The WiFi speed could be improved. Common areas are clean and well-maintained. Happy with my stay so far.");

        // ── Employees ─────────────────────────────────────────────────────────
        ExpenseEmployee cook = makeEmployee(hostelId, "EMP001", "Ramesh Yadav", "Cook", "9800001111", new BigDecimal("18000.00"), new BigDecimal("2000.00"));
        ExpenseEmployee guard = makeEmployee(hostelId, "EMP002", "Suresh Kumar", "Security Guard", "9800002222", new BigDecimal("15000.00"), new BigDecimal("1500.00"));
        ExpenseEmployee cleaner = makeEmployee(hostelId, "EMP003", "Meena Devi", "Housekeeping Staff", "9800003333", new BigDecimal("12000.00"), new BigDecimal("1200.00"));

        // Salary records (Feb 2026 - PAID, Mar 2026 - PENDING)
        makeSalary(cook.getId(),    hostelId, 2, 2026, new BigDecimal("20000.00"), new BigDecimal("0.00"), new BigDecimal("20000.00"), "PAID");
        makeSalary(guard.getId(),   hostelId, 2, 2026, new BigDecimal("16500.00"), new BigDecimal("0.00"), new BigDecimal("16500.00"), "PAID");
        makeSalary(cleaner.getId(), hostelId, 2, 2026, new BigDecimal("13200.00"), new BigDecimal("0.00"), new BigDecimal("13200.00"), "PAID");
        makeSalary(cook.getId(),    hostelId, 3, 2026, new BigDecimal("20000.00"), new BigDecimal("0.00"), new BigDecimal("20000.00"), "PENDING");
        makeSalary(guard.getId(),   hostelId, 3, 2026, new BigDecimal("16500.00"), new BigDecimal("0.00"), new BigDecimal("16500.00"), "PENDING");
        makeSalary(cleaner.getId(), hostelId, 3, 2026, new BigDecimal("13200.00"), new BigDecimal("0.00"), new BigDecimal("13200.00"), "PENDING");

        // ── Expenses ──────────────────────────────────────────────────────────
        makeExpense(hostelId, staffUser.getId(), "Electricity", "Electricity bill for February 2026", new BigDecimal("12500.00"), "BESCOM", LocalDate.of(2026, 2, 5), 2, 2026);
        makeExpense(hostelId, staffUser.getId(), "Electricity", "Electricity bill for March 2026", new BigDecimal("13200.00"), "BESCOM", LocalDate.of(2026, 3, 5), 3, 2026);
        makeExpense(hostelId, staffUser.getId(), "Grocery", "Monthly grocery purchase for kitchen", new BigDecimal("22000.00"), "Fresh Mart Wholesalers", LocalDate.of(2026, 3, 1), 3, 2026);
        makeExpense(hostelId, staffUser.getId(), "Grocery", "Monthly grocery purchase for kitchen", new BigDecimal("21500.00"), "Fresh Mart Wholesalers", LocalDate.of(2026, 2, 1), 2, 2026);
        makeExpense(hostelId, staffUser.getId(), "Internet", "Broadband internet (1 Gbps) - March 2026", new BigDecimal("4500.00"), "ACT Fibernet", LocalDate.of(2026, 3, 7), 3, 2026);
        makeExpense(hostelId, staffUser.getId(), "Maintenance", "Water pump motor replacement", new BigDecimal("8500.00"), "Sai Electricals", LocalDate.of(2026, 3, 3), 3, 2026);
        makeExpense(hostelId, staffUser.getId(), "Maintenance", "Plumbing repair - Ground Floor bathroom", new BigDecimal("1800.00"), "Kumar Plumbing Works", LocalDate.of(2026, 3, 5), 3, 2026);
        makeExpense(hostelId, staffUser.getId(), "Housekeeping", "Cleaning supplies and toiletries restock", new BigDecimal("3200.00"), "CleanPro Supplies", LocalDate.of(2026, 3, 10), 3, 2026);

        System.out.println("[DEV] Full demo data seeded successfully!");
    }

    // ─── Helper: seed feature flags ───────────────────────────────────────────

    private void seedFeatureFlags(Long ownerId, Long setBy) {
        String[] flags = {"online_payment", "referral_program", "parking_management", "cleaning_requests", "weekly_menu", "hostel_reviews"};
        for (String flag : flags) {
            FeatureFlag ff = new FeatureFlag();
            ff.setOwnerId(ownerId); ff.setFlagName(flag);
            ff.setEnabled(true); ff.setSetBy(setBy);
            ff.setReason("Enabled for PROFESSIONAL plan");
            featureFlagRepository.save(ff);
        }
    }

    // ─── Helper: seed weekly menu items ──────────────────────────────────────

    private void seedMenuItems(Long menuId) {
        String[][] days = {
            {"Monday",    "Idli, Sambar, Coconut Chutney", "Rice, Dal Tadka, Aloo Sabzi, Curd", "Chapati, Paneer Butter Masala, Dal Fry"},
            {"Tuesday",   "Poha, Banana, Tea/Coffee",      "Rice, Rajma, Cucumber Salad",        "Chapati, Egg Curry / Soya Curry, Rice"},
            {"Wednesday", "Paratha, Pickle, Curd",         "Rice, Sambar, Papad, Stir-fried Veg","Chapati, Mixed Vegetable Curry, Dal"},
            {"Thursday",  "Upma, Chutney, Tea/Coffee",     "Rice, Dal Makhani, Gobi Sabzi",      "Chapati, Kadai Paneer / Chicken, Rice"},
            {"Friday",    "Puri, Chole, Pickle",           "Veg Biryani / Chicken Biryani, Raita","Chapati, Dal, Jeera Rice"},
            {"Saturday",  "Dosa, Sambar, Chutney",         "Rice, Dal Makhani, Salad, Papad",    "Fried Rice, Manchurian, Soup"},
            {"Sunday",    "Bread, Omelette / Poached Egg, Tea", "Special Thali – Butter Chicken / Paneer Tikka, Naan, Raita, Sweet", "Chapati, Paneer Tikka Masala, Kheer"}
        };
        String[] timings = {"7:30 – 9:30 AM", "12:30 – 2:30 PM", "7:30 – 9:30 PM"};
        String[] mealTypes = {"BREAKFAST", "LUNCH", "DINNER"};

        for (String[] row : days) {
            for (int i = 0; i < 3; i++) {
                MenuItem item = new MenuItem();
                item.setMenuId(menuId);
                item.setDayOfWeek(row[0]);
                item.setMealType(mealTypes[i]);
                item.setItems(row[i + 1]);
                item.setTiming(timings[i]);
                menuItemRepository.save(item);
            }
        }
    }

    // ─── Helper: create additional staff user + hostel assignment ────────────

    private void seedStaffUser(String email, String subRole, Long hostelId, Long ownerId, Long assignedBy) {
        if (authUserRepository.existsByEmail(email)) return;
        AuthUser u = new AuthUser();
        u.setUuid(UUID.randomUUID().toString());
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Test@1234"));
        u.setRole("HOSTEL_STAFF");
        u.setSubRole(subRole);
        u.setStatus("ACTIVE");
        u = authUserRepository.save(u);

        StaffAssignment sa = new StaffAssignment();
        sa.setStaffUserId(u.getId());
        sa.setHostelId(hostelId);
        sa.setOwnerId(ownerId);
        sa.setActive(true);
        sa.setAssignedAt(LocalDateTime.now());
        sa.setAssignedBy(assignedBy);
        staffAssignmentRepository.save(sa);
        System.out.println("[DEV] Seeded HOSTEL_STAFF (" + subRole + "): " + email + " / Test@1234");
    }

    // ─── Helper: create additional hosteller user + profile ──────────────────

    private HostellerProfile seedHostellerUser(String email, String firstName, String lastName, String mobile, Long hostelId, LocalDate joinedDate) {
        if (!authUserRepository.existsByEmail(email)) {
            AuthUser u = new AuthUser();
            u.setUuid(UUID.randomUUID().toString()); u.setEmail(email);
            u.setPasswordHash(passwordEncoder.encode("Test@1234"));
            u.setRole("HOSTELLER"); u.setStatus("ACTIVE");
            u = authUserRepository.save(u);

            HostellerProfile p = new HostellerProfile();
            p.setAuthUserId(u.getId()); p.setFirstName(firstName); p.setLastName(lastName);
            p.setEmail(email); p.setMobile(mobile); p.setHostelId(hostelId);
            p.setJoinedDate(joinedDate); p.setStatus("ACTIVE");
            p.setCity("Bengaluru"); p.setPincode("560001");
            return hostellerProfileRepository.save(p);
        }
        return hostellerProfileRepository.findByEmail(email).orElse(null);
    }

    // ─── Helper: create booking (ACTIVE) ─────────────────────────────────────

    private BookingCheckin makeBooking(Long hostellerId, Long hostelId, Long floorId, Long roomId, Long bedId, LocalDate checkIn, BigDecimal rent) {
        BookingCheckin b = new BookingCheckin();
        b.setHostellerId(hostellerId); b.setHostelId(hostelId); b.setFloorId(floorId);
        b.setRoomId(roomId); b.setBedId(bedId); b.setCheckinDate(checkIn);
        b.setRentAmount(rent); b.setAdvanceAmount(rent); b.setStatus("ACTIVE");
        return bookingCheckinRepository.save(b);
    }

    private BookingCheckin makePendingBooking(Long hostellerId, Long hostelId, Long floorId, Long roomId, Long bedId, LocalDate checkIn, BigDecimal rent) {
        BookingCheckin b = new BookingCheckin();
        b.setHostellerId(hostellerId); b.setHostelId(hostelId); b.setFloorId(floorId);
        b.setRoomId(roomId); b.setBedId(bedId); b.setCheckinDate(checkIn);
        b.setRentAmount(rent); b.setAdvanceAmount(BigDecimal.ZERO); b.setStatus("PENDING");
        return bookingCheckinRepository.save(b);
    }

    private Long makeBookingIdLookup(Long hostellerId) {
        return bookingCheckinRepository.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .map(b -> b.getId()).orElse(1L);
    }

    // ─── Helper: PaymentDue ───────────────────────────────────────────────────

    private PaymentDue makeDue(Long hostellerId, Long bookingId, Long hostelId, int month, int year, LocalDate dueDate, BigDecimal amount, String status) {
        PaymentDue d = new PaymentDue();
        d.setHostellerId(hostellerId); d.setBookingId(bookingId); d.setHostelId(hostelId);
        d.setPeriodMonth(month); d.setPeriodYear(year); d.setDueDate(dueDate);
        d.setRentAmount(amount); d.setStatus(status);
        return paymentDueRepository.save(d);
    }

    // ─── Helper: ComplaintRecord ──────────────────────────────────────────────

    private ComplaintRecord makeComplaint(Long hostellerId, Long hostelId, Long roomId, String category, String title, String description, String priority, String status) {
        ComplaintRecord c = new ComplaintRecord();
        c.setHostellerId(hostellerId); c.setHostelId(hostelId); c.setRoomId(roomId);
        c.setCategory(category); c.setTitle(title); c.setDescription(description);
        c.setPriority(priority); c.setStatus(status);
        return complaintRecordRepository.save(c);
    }

    // ─── Helper: Announcement ────────────────────────────────────────────────

    private void makeAnnouncement(Long hostelId, Long createdBy, String title, String body, String category, String priority, LocalDate validFrom, LocalDate validTill) {
        Announcement a = new Announcement();
        a.setHostelId(hostelId); a.setTitle(title); a.setBody(body);
        a.setCategory(category); a.setPriority(priority);
        a.setValidFrom(validFrom); a.setValidTill(validTill);
        a.setCreatedBy(createdBy);
        announcementRepository.save(a);
    }

    // ─── Helper: Review ───────────────────────────────────────────────────────

    private void makeReview(Long hostellerId, Long hostelId, Long bookingId, int rating, String text) {
        HostelReview r = new HostelReview();
        r.setHostellerId(hostellerId); r.setHostelId(hostelId); r.setBookingId(bookingId);
        r.setRating(rating); r.setReviewText(text); r.setVisible(true);
        hostelReviewRepository.save(r);
    }

    // ─── Helper: ExpenseEmployee ──────────────────────────────────────────────

    private ExpenseEmployee makeEmployee(Long hostelId, String code, String name, String role, String mobile, BigDecimal base, BigDecimal hra) {
        ExpenseEmployee e = new ExpenseEmployee();
        e.setHostelId(hostelId); e.setEmployeeCode(code); e.setName(name);
        e.setRole(role); e.setMobile(mobile); e.setBaseSalary(base); e.setHra(hra);
        return expenseEmployeeRepository.save(e);
    }

    private void makeSalary(Long empId, Long hostelId, int month, int year, BigDecimal gross, BigDecimal deductions, BigDecimal net, String status) {
        ExpenseSalaryRecord s = new ExpenseSalaryRecord();
        s.setEmployeeId(empId); s.setHostelId(hostelId); s.setPeriodMonth(month); s.setPeriodYear(year);
        s.setGrossSalary(gross); s.setDeductions(deductions); s.setNetSalary(net); s.setStatus(status);
        if ("PAID".equals(status)) s.setPaidAt(LocalDateTime.of(year, month, 28, 10, 0));
        expenseSalaryRecordRepository.save(s);
    }

    // ─── Helper: ExpenseRecord ────────────────────────────────────────────────

    private void makeExpense(Long hostelId, Long recordedBy, String category, String description, BigDecimal amount, String vendor, LocalDate date, int month, int year) {
        ExpenseRecord e = new ExpenseRecord();
        e.setHostelId(hostelId); e.setCategory(category); e.setAmount(amount);
        e.setDescription(description); e.setVendorName(vendor); e.setExpenseDate(date);
        e.setPeriodMonth(month); e.setPeriodYear(year); e.setRecordedBy(recordedBy);
        expenseRecordRepository.save(e);
    }

    // ─── Helper: Hostel structure ─────────────────────────────────────────────

    private Floor makeFloor(Long hostelId, String name, int number) {
        Floor f = new Floor();
        f.setHostelId(hostelId); f.setFloorName(name); f.setFloorNumber(number);
        return floorRepository.save(f);
    }

    private Room makeRoom(Long floorId, Long hostelId, String name, String type, int capacity, BigDecimal rent) {
        Room r = new Room();
        r.setFloorId(floorId); r.setHostelId(hostelId); r.setRoomName(name);
        r.setRoomType(type); r.setCapacity(capacity); r.setRentAmount(rent);
        return roomRepository.save(r);
    }

    private Bed makeBed(Long roomId, Long hostelId, String name, String status) {
        Bed b = new Bed();
        b.setRoomId(roomId); b.setHostelId(hostelId); b.setBedName(name); b.setStatus(status);
        return bedRepository.save(b);
    }
}
