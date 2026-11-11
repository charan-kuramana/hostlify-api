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
import com.hostlify.module.booking.entity.BookingRequest;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.booking.repository.BookingRequestRepository;
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
import com.hostlify.module.hosteller.entity.HostellerEmergencyContact;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerEmergencyContactRepository;
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
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Dev-only seed data based on real PG hostels in Vijayawada, Andhra Pradesh.
 *
 * <p>Hostel names, localities, room-sharing types, rents and amenities come from public
 * listings (hexahome.in). Pincodes, landmarks and coordinates are approximated from the
 * locality. All people (owners, staff, hostellers), emails and phone numbers are fictional.
 *
 * <p>Every account uses the password {@value #PASSWORD}. Dates are relative to "today" so
 * dashboards, dues and announcements always look current.
 */
@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private static final String PASSWORD = "Test@1234";
    private static final String CITY = "Vijayawada";
    private static final String MAIN_HOSTEL = "Bliss PG";

    private static final String[] ALL_AMENITIES = {
        "wifi", "ac", "food", "laundry", "housekeeping", "cctv", "power_backup", "parking",
        "geyser", "ro_water", "tv", "fridge", "washing_machine", "attached_bathroom", "fire_alarm", "gym"
    };

    @Autowired private AuthUserRepository authUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private HostelOwnerRepository hostelOwnerRepository;
    @Autowired private HostelRepository hostelRepository;
    @Autowired private FloorRepository floorRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private BedRepository bedRepository;
    @Autowired private HostelAmenityRepository amenityRepository;
    @Autowired private HostelPricingRepository pricingRepository;
    @Autowired private HostelBookingSettingRepository bookingSettingRepository;
    @Autowired private StaffAssignmentRepository staffAssignmentRepository;
    @Autowired private HostellerProfileRepository hostellerProfileRepository;
    @Autowired private HostellerEmergencyContactRepository emergencyContactRepository;
    @Autowired private BookingCheckinRepository bookingCheckinRepository;
    @Autowired private BookingRequestRepository bookingRequestRepository;
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

    // ─── Seed specs ───────────────────────────────────────────────────────────

    /** One room template: type, beds per room, monthly rent per bed. */
    private record RoomSpec(String type, int beds, String rent) {}

    /** A real Vijayawada hostel plus the (fictional) owner account that manages it. */
    private record HostelSpec(String name, String type, String address, String pincode, String landmark,
                              String lat, String lng, String about, String phone,
                              String ownerEmail, String ownerName, String ownerMobile, String plan,
                              int floors, List<RoomSpec> roomsPerFloor, Set<String> amenities) {}

    /** A fictional resident: who they are and which room/bed (by index) they occupy. */
    private record ResidentSpec(String email, String first, String last, String gender, String mobile,
                                String userType, String college, String hometown, String hometownPin,
                                int monthsAgo) {}

    private static final List<HostelSpec> HOSTELS = List.of(
        new HostelSpec(MAIN_HOSTEL, "MIXED",
            "Sri Ramachandra Nagar, Near Benz Circle, Vijayawada, Andhra Pradesh 520008", "520008",
            "Near Benz Circle", "16.5040000", "80.6550000",
            "Co-ed, fully furnished three-floor PG near Benz Circle with private and shared AC rooms, "
                + "home-style Andhra meals, high-speed WiFi and 24x7 CCTV security.",
            "0866 240 1100",
            "owner.bliss@test.com", "Srinivasa Rao Kommineni", "9848012345", "PROFESSIONAL",
            3, List.of(new RoomSpec("SINGLE", 1, "10000"), new RoomSpec("DOUBLE", 2, "9000"),
                       new RoomSpec("TRIPLE", 3, "8500"), new RoomSpec("TRIPLE", 3, "8500")),
            Set.of("wifi", "ac", "tv", "food", "laundry", "housekeeping", "cctv", "power_backup",
                   "geyser", "fridge", "parking", "ro_water")),
        new HostelSpec("Amigos Hostel", "BOYS",
            "MG Road, Labbipet, Vijayawada, Andhra Pradesh 520010", "520010",
            "Near MG Road / Bandar Road, Labbipet", "16.5050000", "80.6400000",
            "Fully furnished triple-sharing boys PG for students and working professionals in central Labbipet.",
            null,
            "owner.amigos@test.com", "Venkata Ramana Gutta", "9848023456", "STARTER",
            2, List.of(new RoomSpec("TRIPLE", 3, "6500"), new RoomSpec("TRIPLE", 3, "6500"),
                       new RoomSpec("DOUBLE", 2, "7500")),
            Set.of("wifi", "laundry", "housekeeping", "cctv", "power_backup", "fire_alarm", "geyser",
                   "washing_machine", "fridge", "ro_water")),
        new HostelSpec("Sri Medha Hostel", "BOYS",
            "Patamata, Vijayawada, Andhra Pradesh 520010", "520010",
            "Near Patamata Bus Stop / NH-65 (Bandar Road)", "16.4950000", "80.6650000",
            "Two-floor, fully furnished boys PG in Patamata with twin-sharing rooms and attached bathrooms. "
                + "Veg only, no smoking or alcohol.",
            null,
            "owner.srimedha@test.com", "Nageswara Rao Bandaru", "9848034567", "STARTER",
            2, List.of(new RoomSpec("DOUBLE", 2, "6000"), new RoomSpec("DOUBLE", 2, "6000"),
                       new RoomSpec("DOUBLE", 2, "6000"), new RoomSpec("TRIPLE", 3, "5000")),
            Set.of("wifi", "laundry", "housekeeping", "cctv", "power_backup", "tv", "geyser",
                   "ro_water", "attached_bathroom")),
        new HostelSpec("Anand Hostel", "BOYS",
            "Ramavarappadu, Vijayawada, Andhra Pradesh 521108", "521108",
            "Near Ramavarappadu Ring Road junction", "16.5230000", "80.6800000",
            "Budget three-floor boys PG with twin and four-sharing rooms close to the Ring Road. Meals not included.",
            null,
            "owner.anand@test.com", "Anand Kumar Yarlagadda", "9848045678", "FREE",
            3, List.of(new RoomSpec("DOUBLE", 2, "3500"), new RoomSpec("DOUBLE", 2, "3500"),
                       new RoomSpec("DORMITORY", 4, "3000")),
            Set.of("wifi", "laundry", "housekeeping", "cctv", "power_backup", "parking", "geyser",
                   "ro_water", "attached_bathroom")),
        new HostelSpec("Ruchi Sree Hostel", "GIRLS",
            "Veterinary Colony, Gunadala, Vijayawada, Andhra Pradesh 520004", "520004",
            "Near Gunadala Mary Matha Church", "16.5180000", "80.6610000",
            "Spacious, fully furnished girls PG in Veterinary Colony with AC twin and triple-sharing rooms.",
            null,
            "owner.ruchisree@test.com", "Padmavathi Chinta", "9848056789", "PROFESSIONAL",
            2, List.of(new RoomSpec("DOUBLE", 2, "9000"), new RoomSpec("DOUBLE", 2, "9000"),
                       new RoomSpec("TRIPLE", 3, "8000"), new RoomSpec("TRIPLE", 3, "8000")),
            Set.of("wifi", "ac", "laundry", "housekeeping", "cctv", "power_backup", "geyser", "fridge",
                   "ro_water", "attached_bathroom")),
        new HostelSpec("Cherry PG Hostel", "GIRLS",
            "Central Excise Colony, Vijayawada, Andhra Pradesh 520008", "520008",
            "Near Benz Circle / Ramavarappadu side", "16.5120000", "80.6700000",
            "Premium three-floor girls PG with AC twin, triple and four-sharing rooms.",
            null,
            "owner.cherry@test.com", "Sujatha Vemuri", "9848067890", "ENTERPRISE",
            3, List.of(new RoomSpec("DOUBLE", 2, "15000"), new RoomSpec("TRIPLE", 3, "10000"),
                       new RoomSpec("DORMITORY", 4, "9000")),
            Set.of("wifi", "ac", "laundry", "housekeeping", "cctv", "power_backup", "fire_alarm",
                   "washing_machine", "ro_water"))
    );

    // Residents of each hostel (fictional people). The first Bliss PG resident is the main demo login.
    private static final Map<String, List<ResidentSpec>> RESIDENTS = Map.of(
        MAIN_HOSTEL, List.of(
            new ResidentSpec("hosteller@test.com", "Sai Krishna", "Reddy", "MALE", "9876543210", "STUDENT",
                "Velagapudi Ramakrishna Siddhartha Engineering College (VRSEC)", "Ongole", "523001", 13),
            new ResidentSpec("lakshmi.prasanna@test.com", "Lakshmi", "Prasanna", "FEMALE", "9876500001", "STUDENT",
                "Prasad V. Potluri Siddhartha Institute of Technology (PVPSIT)", "Guntur", "522001", 12),
            new ResidentSpec("naveen.kumar@test.com", "Naveen", "Kumar", "MALE", "9876500002", "PROFESSIONAL",
                "HCL Technologies, Gannavaram", "Eluru", "534001", 11),
            new ResidentSpec("harika.devi@test.com", "Harika", "Devi", "FEMALE", "9876500003", "STUDENT",
                "Andhra Loyola College", "Machilipatnam", "521001", 9),
            new ResidentSpec("vamsi.krishna@test.com", "Vamsi", "Krishna", "MALE", "9876500004", "STUDENT",
                "KL University (Koneru Lakshmaiah Education Foundation)", "Tenali", "522201", 7),
            new ResidentSpec("teja.sri@test.com", "Teja", "Sri", "FEMALE", "9876500005", "PROFESSIONAL",
                "Ramesh Hospitals, Vijayawada", "Bhimavaram", "534201", 4)),
        "Amigos Hostel", List.of(
            new ResidentSpec("bhargav.amigos@test.com", "Bhargav", "Naidu", "MALE", "9876500011", "STUDENT",
                "P.B. Siddhartha College of Arts & Science", "Nellore", "524001", 6),
            new ResidentSpec("dinesh.amigos@test.com", "Dinesh", "Varma", "MALE", "9876500012", "PROFESSIONAL",
                "Medha Towers IT Park, Gannavaram", "Rajahmundry", "533101", 3)),
        "Sri Medha Hostel", List.of(
            new ResidentSpec("rohith.medha@test.com", "Rohith", "Chowdary", "MALE", "9876500021", "STUDENT",
                "SRK Institute of Technology", "Narasaraopet", "522601", 5)),
        "Anand Hostel", List.of(
            new ResidentSpec("manoj.anand@test.com", "Manoj", "Babu", "MALE", "9876500031", "STUDENT",
                "NRI Institute of Technology", "Gudivada", "521301", 4),
            new ResidentSpec("chaitanya.anand@test.com", "Chaitanya", "Rao", "MALE", "9876500032", "STUDENT",
                "Dhanekula Institute of Engineering & Technology", "Kakinada", "533001", 2)),
        "Ruchi Sree Hostel", List.of(
            new ResidentSpec("anusha.ruchi@test.com", "Anusha", "Kommu", "FEMALE", "9876500041", "STUDENT",
                "Andhra Loyola Institute of Engineering & Technology (ALIET)", "Vizianagaram", "535001", 8),
            new ResidentSpec("sravani.ruchi@test.com", "Sravani", "Pothula", "FEMALE", "9876500042", "PROFESSIONAL",
                "Medha Towers IT Park, Gannavaram", "Srikakulam", "532001", 3)),
        "Cherry PG Hostel", List.of(
            new ResidentSpec("keerthana.cherry@test.com", "Keerthana", "Malla", "FEMALE", "9876500051", "STUDENT",
                "Velagapudi Ramakrishna Siddhartha Engineering College (VRSEC)", "Tirupati", "517501", 10),
            new ResidentSpec("divya.cherry@test.com", "Divya", "Sree", "FEMALE", "9876500052", "PROFESSIONAL",
                "HCL Technologies, Gannavaram", "Kurnool", "518001", 5))
    );

    /** Built structure of a seeded hostel: rooms in creation order, each with its beds. */
    private record Built(Hostel hostel, HostelOwner owner, AuthUser ownerUser,
                         List<Room> rooms, Map<Long, List<Bed>> bedsByRoom) {}

    // ─── Entry point ──────────────────────────────────────────────────────────

    @Override
    public void run(String... args) {
        AuthUser superAdmin = seedSuperAdmin();

        if (hostelRepository.existsByName(MAIN_HOSTEL)) {
            System.out.println("[DEV] Vijayawada demo data already seeded. Skipping.");
            return;
        }
        System.out.println("[DEV] Seeding Vijayawada demo data...");

        Map<String, Built> built = new LinkedHashMap<>();
        for (HostelSpec spec : HOSTELS) {
            built.put(spec.name(), seedHostel(spec, superAdmin.getId()));
        }

        // Residents + bookings + rent history for every hostel
        Map<String, List<HostellerProfile>> residents = new HashMap<>();
        for (Map.Entry<String, Built> e : built.entrySet()) {
            residents.put(e.getKey(), seedResidents(e.getValue(), RESIDENTS.getOrDefault(e.getKey(), List.of())));
        }

        Built bliss = built.get(MAIN_HOSTEL);
        seedMainHostelExtras(bliss, residents.get(MAIN_HOSTEL));
        seedLighterExtras(built, residents);
        seedApplicants(built);

        System.out.println("[DEV] Vijayawada demo data seeded. All accounts use password " + PASSWORD);
    }

    // ─── Accounts ─────────────────────────────────────────────────────────────

    private AuthUser seedSuperAdmin() {
        return authUserRepository.findByEmail("superadmin@test.com").orElseGet(() -> {
            AuthUser sa = newUser("superadmin@test.com", "9000000001", "SUPER_ADMIN", null, "ACTIVE");
            System.out.println("[DEV] Seeded SUPER_ADMIN: superadmin@test.com / " + PASSWORD);
            return sa;
        });
    }

    private AuthUser newUser(String email, String mobile, String role, String subRole, String status) {
        AuthUser u = new AuthUser();
        u.setUuid(UUID.randomUUID().toString());
        u.setEmail(email);
        u.setMobile(mobile);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRole(role);
        u.setSubRole(subRole);
        u.setStatus(status);
        return authUserRepository.save(u);
    }

    private void assignStaff(AuthUser staff, Long hostelId, Long ownerId, Long assignedBy) {
        StaffAssignment sa = new StaffAssignment();
        sa.setStaffUserId(staff.getId());
        sa.setHostelId(hostelId);
        sa.setOwnerId(ownerId);
        sa.setActive(true);
        sa.setAssignedAt(LocalDateTime.now().minusMonths(12));
        sa.setAssignedBy(assignedBy);
        staffAssignmentRepository.save(sa);
    }

    // ─── Hostel + structure ───────────────────────────────────────────────────

    private Built seedHostel(HostelSpec spec, Long superAdminId) {
        AuthUser ownerUser = newUser(spec.ownerEmail(), spec.ownerMobile(), "HOSTEL_STAFF", "OWNER", "ACTIVE");

        HostelOwner owner = new HostelOwner();
        owner.setAuthUserId(ownerUser.getId());
        owner.setBusinessName(spec.name());
        owner.setOwnerName(spec.ownerName());
        owner.setMobile(spec.ownerMobile());
        owner.setEmail(spec.ownerEmail());
        owner.setSubscriptionPlan(spec.plan());
        owner.setPlanExpiresAt(LocalDateTime.now().plusYears(1));
        owner.setStatus("ACTIVE");
        owner = hostelOwnerRepository.save(owner);

        Subscription sub = new Subscription();
        sub.setOwnerId(owner.getId());
        sub.setPlanName(spec.plan());
        switch (spec.plan()) {
            case "ENTERPRISE" -> { sub.setMaxHostels(10); sub.setMaxBeds(1000); sub.setPriceMonthly(new BigDecimal("7999.00")); }
            case "PROFESSIONAL" -> { sub.setMaxHostels(3); sub.setMaxBeds(200); sub.setPriceMonthly(new BigDecimal("2999.00")); }
            case "STARTER" -> { sub.setMaxHostels(1); sub.setMaxBeds(75); sub.setPriceMonthly(new BigDecimal("999.00")); }
            default -> { sub.setMaxHostels(1); sub.setMaxBeds(30); sub.setPriceMonthly(BigDecimal.ZERO); }
        }
        subscriptionRepository.save(sub);
        seedFeatureFlags(owner.getId(), superAdminId, spec.plan());

        Hostel hostel = new Hostel();
        hostel.setOwnerId(owner.getId());
        hostel.setName(spec.name());
        hostel.setHostelType(spec.type());
        hostel.setCity(CITY);
        hostel.setPincode(spec.pincode());
        hostel.setAddress(spec.address());
        hostel.setLandmark(spec.landmark());
        hostel.setAbout(spec.about());
        hostel.setContactPhone(spec.phone() != null ? spec.phone() : spec.ownerMobile());
        hostel.setLatitude(new BigDecimal(spec.lat()));
        hostel.setLongitude(new BigDecimal(spec.lng()));
        hostel.setOnlinePaymentEnabled(true);
        BigDecimal minRent = spec.roomsPerFloor().stream().map(r -> new BigDecimal(r.rent()))
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        hostel.setDefaultRent(minRent);
        hostel = hostelRepository.save(hostel);
        Long hostelId = hostel.getId();

        assignStaff(ownerUser, hostelId, owner.getId(), ownerUser.getId());

        // Amenities: full catalogue so the Hostel Setup screen shows every toggle
        for (String key : ALL_AMENITIES) {
            HostelAmenity a = new HostelAmenity();
            a.setHostelId(hostelId);
            a.setAmenityKey(key);
            a.setEnabled(spec.amenities().contains(key));
            amenityRepository.save(a);
        }

        // Pricing per room type (lowest rent for that type)
        Map<String, BigDecimal> pricing = new LinkedHashMap<>();
        for (RoomSpec r : spec.roomsPerFloor()) pricing.merge(r.type(), new BigDecimal(r.rent()), BigDecimal::min);
        pricing.forEach((type, rent) -> {
            HostelPricing p = new HostelPricing();
            p.setHostelId(hostelId);
            p.setRoomType(type);
            p.setBaseRent(rent);
            p.setDepositMonths(1);
            p.setLateFee(new BigDecimal("200.00"));
            p.setActive(true);
            pricingRepository.save(p);
        });

        HostelBookingSetting settings = new HostelBookingSetting();
        settings.setHostelId(hostelId);
        settings.setSelfBookingEnabled(true);
        settings.setRequiresAdminApproval(true);
        settings.setAutoExpireDays(3);
        settings.setMaxPendingPerBed(1);
        settings.setGeolocationRadiusKm(new BigDecimal("15.00"));
        bookingSettingRepository.save(settings);

        // Floors / rooms / beds: room names like G-01, 101, 201 ...
        String[] floorNames = {"Ground Floor", "1st Floor", "2nd Floor", "3rd Floor"};
        List<Room> rooms = new ArrayList<>();
        Map<Long, List<Bed>> bedsByRoom = new HashMap<>();
        int bedCount = 0;
        for (int f = 0; f < spec.floors(); f++) {
            Floor floor = new Floor();
            floor.setHostelId(hostelId);
            floor.setFloorName(floorNames[f]);
            floor.setFloorNumber(f);
            floor = floorRepository.save(floor);
            for (int r = 0; r < spec.roomsPerFloor().size(); r++) {
                RoomSpec rs = spec.roomsPerFloor().get(r);
                Room room = new Room();
                room.setFloorId(floor.getId());
                room.setHostelId(hostelId);
                room.setRoomName(f == 0 ? String.format("G-%02d", r + 1) : String.format("%d%02d", f, r + 1));
                room.setRoomType(rs.type());
                room.setCapacity(rs.beds());
                room.setRentAmount(new BigDecimal(rs.rent()));
                room = roomRepository.save(room);
                rooms.add(room);
                List<Bed> beds = new ArrayList<>();
                for (int b = 0; b < rs.beds(); b++) {
                    Bed bed = new Bed();
                    bed.setRoomId(room.getId());
                    bed.setHostelId(hostelId);
                    bed.setBedName("Bed " + (char) ('A' + b));
                    bed.setStatus("AVAILABLE");
                    beds.add(bedRepository.save(bed));
                }
                bedsByRoom.put(room.getId(), beds);
                bedCount += rs.beds();
            }
        }
        hostel.setTotalFloors(spec.floors());
        hostel.setTotalRooms(rooms.size());
        hostel.setTotalBeds(bedCount);
        hostel = hostelRepository.save(hostel);

        System.out.println("[DEV] Seeded hostel " + spec.name() + " (" + bedCount + " beds) – owner login "
                + spec.ownerEmail() + " / " + PASSWORD);
        return new Built(hostel, owner, ownerUser, rooms, bedsByRoom);
    }

    // ─── Residents ────────────────────────────────────────────────────────────

    /** Places residents into beds (filling rooms in order), with booking + rent history. */
    private List<HostellerProfile> seedResidents(Built b, List<ResidentSpec> specs) {
        List<Bed> freeBeds = new ArrayList<>();
        for (Room room : b.rooms()) freeBeds.addAll(b.bedsByRoom().get(room.getId()));
        Map<Long, Room> roomsById = new HashMap<>();
        for (Room room : b.rooms()) roomsById.put(room.getId(), room);

        List<HostellerProfile> out = new ArrayList<>();
        YearMonth now = YearMonth.now();
        int i = 0;
        for (ResidentSpec rs : specs) {
            Bed bed = freeBeds.get(i++);
            Room room = roomsById.get(bed.getRoomId());
            LocalDate joined = now.minusMonths(rs.monthsAgo()).atDay(Math.min(1 + i * 3, 25));

            AuthUser u = newUser(rs.email(), rs.mobile(), "HOSTELLER", null, "ACTIVE");
            HostellerProfile p = new HostellerProfile();
            p.setAuthUserId(u.getId());
            p.setFirstName(rs.first());
            p.setLastName(rs.last());
            p.setEmail(rs.email());
            p.setMobile(rs.mobile());
            p.setGender(rs.gender());
            p.setDateOfBirth(LocalDate.of(2001 + (i % 4), 1 + (i * 2) % 12, 5 + i));
            p.setUserType(rs.userType());
            p.setCompanyCollege(rs.college());
            p.setPermanentAddress("Door No. " + (10 + i) + "-" + (2 + i) + ", Main Road, " + rs.hometown() + ", Andhra Pradesh");
            p.setCity(rs.hometown());
            p.setPincode(rs.hometownPin());
            p.setIdProofType("AADHAAR");
            p.setIdProofNumber("XXXX-XXXX-" + (4100 + i * 37));
            p.setJoinedDate(joined);
            p.setHostelId(b.hostel().getId());
            p.setStatus("ACTIVE");
            p = hostellerProfileRepository.save(p);

            HostellerEmergencyContact ec = new HostellerEmergencyContact();
            ec.setHostellerId(p.getId());
            ec.setName(rs.last() + " (Parent)");
            ec.setRelationship("FATHER");
            ec.setMobile("94400" + String.format("%05d", 10000 + i * 111));
            emergencyContactRepository.save(ec);

            BookingCheckin booking = new BookingCheckin();
            booking.setHostellerId(p.getId());
            booking.setHostelId(b.hostel().getId());
            booking.setFloorId(room.getFloorId());
            booking.setRoomId(room.getId());
            booking.setBedId(bed.getId());
            booking.setCheckinDate(joined);
            booking.setRentAmount(room.getRentAmount());
            booking.setAdvanceAmount(room.getRentAmount());
            booking.setStatus("ACTIVE");
            booking = bookingCheckinRepository.save(booking);

            bed.setStatus("OCCUPIED");
            bed.setOccupiedByBookingId(booking.getId());
            bedRepository.save(bed);
            room.setCurrentOccupancy(room.getCurrentOccupancy() + 1);
            roomRepository.save(room);

            seedRentHistory(p, booking, b.ownerUser().getId(), i);
            out.add(p);
        }
        return out;
    }

    /** Last 3 months paid (cash / UPI), current month unpaid (every 3rd resident also has last month overdue). */
    private void seedRentHistory(HostellerProfile p, BookingCheckin booking, Long recordedBy, int seq) {
        YearMonth now = YearMonth.now();
        YearMonth joined = YearMonth.from(booking.getCheckinDate());
        boolean lastMonthOverdue = seq % 3 == 0;
        for (int back = 3; back >= 0; back--) {
            YearMonth period = now.minusMonths(back);
            if (period.isBefore(joined)) continue;
            boolean paid = back > 0 && !(back == 1 && lastMonthOverdue);
            PaymentDue due = new PaymentDue();
            due.setHostellerId(p.getId());
            due.setBookingId(booking.getId());
            due.setHostelId(booking.getHostelId());
            due.setPeriodMonth(period.getMonthValue());
            due.setPeriodYear(period.getYear());
            due.setDueDate(period.atDay(5));
            due.setRentAmount(booking.getRentAmount());
            due.setStatus(paid ? "PAID" : "UNPAID");
            due = paymentDueRepository.save(due);
            if (paid) {
                boolean upi = (seq + back) % 2 == 0;
                PaymentRecord rec = new PaymentRecord();
                rec.setHostellerId(p.getId());
                rec.setHostelId(booking.getHostelId());
                rec.setDueId(due.getId());
                rec.setAmount(booking.getRentAmount());
                rec.setPaymentMethod(upi ? "UPI" : "CASH");
                rec.setTransactionRef(upi ? "UPI" + (412300000L + p.getId() * 1000 + back) : "CASH-" + p.getId() + "-" + back);
                rec.setRecordedBy(recordedBy);
                rec.setPaymentDate(period.atDay(3));
                rec.setStatus("SUCCESS");
                paymentRecordRepository.save(rec);
            }
        }
    }

    // ─── Pending applicants (staff "Pending Allocations" + "Booking Requests") ─

    private void seedApplicants(Map<String, Built> built) {
        // 1) Registered, no bed yet -> appears in Pending Allocations
        HostellerProfile mounika = newPendingHosteller("mounika.pending@test.com", "Mounika", "Gorantla",
                "FEMALE", "9876500091", "STUDENT", "P.B. Siddhartha College of Arts & Science");

        // 2) Registered + requested a specific bed at Bliss PG -> appears in Booking Requests
        HostellerProfile pranav = newPendingHosteller("pranav.request@test.com", "Pranav", "Tummala",
                "MALE", "9876500092", "PROFESSIONAL", "HCL Technologies, Gannavaram");
        Built bliss = built.get(MAIN_HOSTEL);
        Bed bed = null;
        Room room = null;
        for (Room r : bliss.rooms()) {
            for (Bed candidate : bliss.bedsByRoom().get(r.getId())) {
                if ("AVAILABLE".equals(candidate.getStatus())) { bed = candidate; room = r; break; }
            }
            if (bed != null) break;
        }
        if (bed != null) {
            bed.setStatus("RESERVED");
            bed.setReservedForHostellerId(pranav.getId());
            bed.setReservedAt(LocalDateTime.now().minusHours(6));
            bedRepository.save(bed);

            BookingRequest req = new BookingRequest();
            req.setHostellerUserId(pranav.getAuthUserId());
            req.setHostellerProfileId(pranav.getId());
            req.setHostelId(bliss.hostel().getId());
            req.setBedId(bed.getId());
            req.setRoomId(room.getId());
            req.setFloorId(room.getFloorId());
            req.setCheckInDate(LocalDate.now().plusDays(5));
            req.setPreferenceNotes("Joining HCL Gannavaram next week – prefer a lower bunk and a quiet room.");
            req.setStatus("PENDING_APPROVAL");
            req.setExpiresAt(LocalDateTime.now().plusDays(3));
            bookingRequestRepository.save(req);
        }
        System.out.println("[DEV] Seeded pending applicants: " + mounika.getEmail() + ", " + pranav.getEmail());
    }

    private HostellerProfile newPendingHosteller(String email, String first, String last, String gender,
                                                 String mobile, String userType, String college) {
        AuthUser u = newUser(email, mobile, "HOSTELLER", null, "PENDING");
        HostellerProfile p = new HostellerProfile();
        p.setAuthUserId(u.getId());
        p.setFirstName(first);
        p.setLastName(last);
        p.setEmail(email);
        p.setMobile(mobile);
        p.setGender(gender);
        p.setUserType(userType);
        p.setCompanyCollege(college);
        p.setStatus("PENDING");
        return hostellerProfileRepository.save(p);
    }

    // ─── Rich demo data for the main hostel (Bliss PG) ────────────────────────

    private void seedMainHostelExtras(Built b, List<HostellerProfile> res) {
        Long hostelId = b.hostel().getId();
        Long ownerUserId = b.ownerUser().getId();
        LocalDate today = LocalDate.now();

        // Extra staff with sub-roles
        assignStaff(newUser("manager.bliss@test.com", "9848012346", "HOSTEL_STAFF", "MANAGER", "ACTIVE"),
                hostelId, b.owner().getId(), ownerUserId);
        assignStaff(newUser("admin.bliss@test.com", "9848012347", "HOSTEL_STAFF", "ADMIN", "ACTIVE"),
                hostelId, b.owner().getId(), ownerUserId);
        System.out.println("[DEV] Seeded Bliss PG staff: manager.bliss@test.com (MANAGER), admin.bliss@test.com (ADMIN)");

        HostellerProfile sai = res.get(0), lakshmi = res.get(1), naveen = res.get(2),
                harika = res.get(3), vamsi = res.get(4), teja = res.get(5);
        Long saiRoom = bookingFor(sai).getRoomId();
        String saiRoomName = roomRepository.findById(saiRoom).map(Room::getRoomName).orElse("G-01");

        // ── Complaints
        makeComplaint(sai.getId(), hostelId, saiRoom, "Maintenance", "AC not cooling properly",
                "The AC in room " + saiRoomName + " is not cooling since the last power cut. It makes a rattling noise.",
                "HIGH", "IN_PROGRESS", null);
        makeComplaint(sai.getId(), hostelId, saiRoom, "Cleanliness", "Common washroom not cleaned",
                "The ground floor common washroom has not been cleaned for two days.", "MEDIUM", "OPEN", null);
        makeComplaint(sai.getId(), hostelId, saiRoom, "Food", "Curd rice too sour at dinner",
                "Dinner curd rice was very sour on Tuesday and the pappu was undercooked.", "LOW", "RESOLVED",
                "Spoke to the cook; curd is now set fresh daily and pappu cooking time increased.");
        makeComplaint(lakshmi.getId(), hostelId, bookingFor(lakshmi).getRoomId(), "Other", "WiFi drops every evening",
                "WiFi disconnects between 8 PM and 11 PM almost daily, making online classes difficult.", "MEDIUM", "OPEN", null);
        makeComplaint(naveen.getId(), hostelId, bookingFor(naveen).getRoomId(), "Security", "Main gate left open at night",
                "The main gate was left open after 11 PM twice this week. Please ensure the watchman locks it.", "HIGH", "OPEN", null);

        // ── Maintenance log
        makeMaintenance(hostelId, saiRoom, ownerUserId, "Electrical",
                "Ground floor DB panel tripping frequently; MCB replacement and rewiring needed.",
                "4500.00", null, "Sri Sai Electricals, Benz Circle", "IN_PROGRESS", null);
        makeMaintenance(hostelId, bookingFor(lakshmi).getRoomId(), ownerUserId, "Plumbing",
                "Leak in 1st floor bathroom inlet pipe. Replaced with CPVC fittings.",
                "2000.00", "1800.00", "Durga Plumbing Works", "COMPLETED", today.minusDays(9).atTime(15, 0));
        makeMaintenance(hostelId, bookingFor(naveen).getRoomId(), ownerUserId, "AC / Cooling",
                "Two split ACs on 1st floor need gas refill and servicing before summer.",
                "6000.00", null, "Cool Point AC Services", "OPEN", null);

        // ── Announcements (relative to today)
        YearMonth next = YearMonth.now().plusMonths(1);
        String nextMonth = next.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + " " + next.getYear();
        makeAnnouncement(hostelId, ownerUserId, nextMonth + " Rent Reminder",
                "Dear residents, rent for " + nextMonth + " is due by the 5th. A late fee of ₹200 applies after the due date. "
                        + "Pay via UPI in the Hostlify app or at the reception.",
                "Fee", "URGENT", today.minusDays(2), next.atDay(5));
        makeAnnouncement(hostelId, ownerUserId, "Power Shutdown – APCPDCL Maintenance",
                "APCPDCL has scheduled line maintenance in the Benz Circle area on " + today.plusDays(3)
                        + " from 9:00 AM to 2:00 PM. Inverter backup covers lights and fans only; lifts and ACs will be off.",
                "Maintenance", "HIGH", today.minusDays(1), today.plusDays(3));
        makeAnnouncement(hostelId, ownerUserId, "Dussehra Celebrations at Bliss PG",
                "Join us on the terrace this weekend for Dussehra celebrations – special festive lunch with pulihora, "
                        + "bobbatlu and payasam. Families of residents are welcome for lunch.",
                "Event", "NORMAL", today.minusDays(4), today.plusDays(6));
        makeAnnouncement(hostelId, ownerUserId, "Updated Mess Timings",
                "Breakfast 7:30–9:30 AM | Lunch 12:30–2:30 PM | Dinner 7:30–9:30 PM. Sunday brunch 9:00–11:00 AM.",
                "General", "NORMAL", today.minusDays(10), today.plusMonths(3));
        makeAnnouncement(hostelId, ownerUserId, "Water Tank Cleaning",
                "Overhead tanks will be cleaned on Sunday morning. Water supply will be off from 7 AM to 11 AM; please store water in advance.",
                "Maintenance", "NORMAL", today, today.plusDays(7));

        // ── Weekly menu (Andhra home-style)
        seedWeeklyMenu(hostelId, ownerUserId);

        // ── Leave requests
        BookingCheckin saiBooking = bookingFor(sai);
        makeLeave(sai.getId(), saiBooking.getId(), "HOME", today.minusDays(40), today.minusDays(37),
                "Going home to Ongole for my sister's engagement.", "APPROVED", ownerUserId);
        makeLeave(sai.getId(), saiBooking.getId(), "CASUAL", today.minusDays(75), today.minusDays(70),
                "Sankranti-style family trip to Araku with parents.", "APPROVED", ownerUserId);
        makeLeave(sai.getId(), saiBooking.getId(), "HOME", today.plusDays(8), today.plusDays(12),
                "Going home for Deepavali.", "PENDING", null);
        makeLeave(lakshmi.getId(), bookingFor(lakshmi).getId(), "CASUAL", today.plusDays(2), today.plusDays(4),
                "Need to visit Guntur for college document verification.", "PENDING", null);
        makeLeave(harika.getId(), bookingFor(harika).getId(), "SICK", today.minusDays(15), today.minusDays(13),
                "Fever – resting at home in Machilipatnam.", "APPROVED", ownerUserId);

        // ── Referral
        ReferralCode rc = new ReferralCode();
        rc.setHostellerId(sai.getId());
        rc.setCode("SAIVJA26");
        rc.setTotalReferrals(2);
        rc.setTotalDiscountEarned(new BigDecimal("500.00"));
        referralCodeRepository.save(rc);
        makeReferral(sai.getId(), vamsi.getId(), "COMPLETED", "500.00");
        makeReferral(sai.getId(), teja.getId(), "PENDING", "500.00");

        // ── Parking (Krishna district AP-16 registrations)
        makeParking(sai.getId(), hostelId, "AP 16 CK 4521", "Bike", "P-03", saiBooking.getCheckinDate());
        makeParking(naveen.getId(), hostelId, "AP 16 DM 7788", "Car", "P-07", bookingFor(naveen).getCheckinDate());
        makeParking(vamsi.getId(), hostelId, "AP 39 BQ 2210", "Bike", "P-05", bookingFor(vamsi).getCheckinDate());

        // ── Cleaning requests
        makeCleaning(sai.getId(), hostelId, saiRoomName, today.minusDays(6), "COMPLETED",
                "Regular cleaning – please focus on the bathroom.", ownerUserId);
        makeCleaning(sai.getId(), hostelId, saiRoomName, today, "PENDING",
                "Deep cleaning needed before semester exams.", null);
        String lakshmiRoom = roomRepository.findById(bookingFor(lakshmi).getRoomId()).map(Room::getRoomName).orElse("");
        makeCleaning(lakshmi.getId(), hostelId, lakshmiRoom, today.plusDays(1), "SCHEDULED",
                "Please clean after 11 AM.", null);

        // ── Reviews
        makeReview(sai.getId(), hostelId, saiBooking.getId(), 4,
                "Clean rooms, tasty Andhra meals and very close to Benz Circle. AC maintenance could be faster.");
        makeReview(lakshmi.getId(), hostelId, bookingFor(lakshmi).getId(), 5,
                "Very safe for girls, CCTV everywhere and the warden is responsive. Pesarattu on Wednesdays is the best!");
        makeReview(naveen.getId(), hostelId, bookingFor(naveen).getId(), 4,
                "Good value for money. Easy auto/bus access to Gannavaram for office. WiFi speed can improve in the evenings.");

        // ── Employees + salaries
        ExpenseEmployee cook = makeEmployee(hostelId, "BL-EMP01", "Ramulu Dasari", "Cook", "9701100011", "16000.00", "2000.00");
        ExpenseEmployee guard = makeEmployee(hostelId, "BL-EMP02", "Koteswara Rao", "Security Guard", "9701100022", "13000.00", "1500.00");
        ExpenseEmployee cleaner = makeEmployee(hostelId, "BL-EMP03", "Mangamma", "Housekeeping Staff", "9701100033", "11000.00", "1000.00");
        YearMonth thisMonth = YearMonth.now(), lastMonth = thisMonth.minusMonths(1);
        for (ExpenseEmployee e : List.of(cook, guard, cleaner)) {
            BigDecimal gross = e.getBaseSalary().add(e.getHra());
            makeSalary(e.getId(), hostelId, lastMonth, gross, "PAID");
            makeSalary(e.getId(), hostelId, thisMonth, gross, "PENDING");
        }

        // ── Expenses (local Vijayawada vendors / utilities)
        for (YearMonth ym : List.of(lastMonth, thisMonth)) {
            boolean current = ym.equals(thisMonth);
            makeExpense(hostelId, ownerUserId, "Electricity Bill", "APCPDCL electricity bill – " + ym,
                    current ? "14200.00" : "13650.00", "APCPDCL", ym.atDay(6), ym);
            makeExpense(hostelId, ownerUserId, "Vegetables", "Monthly rice, dal, oil & vegetables for mess – " + ym,
                    current ? "38500.00" : "36200.00", "Sri Lakshmi Ganapathi Traders, One Town", ym.atDay(2), ym);
            makeExpense(hostelId, ownerUserId, "Other Expenses", "Milk & curd supply – " + ym,
                    current ? "9800.00" : "9450.00", "Vijaya Dairy (Krishna Milk Union)", ym.atDay(1), ym);
            makeExpense(hostelId, ownerUserId, "Other Expenses", "300 Mbps broadband – " + ym,
                    "2360.00", "ACT Fibernet", ym.atDay(8), ym);
        }
        makeExpense(hostelId, ownerUserId, "Gas Bill", "Commercial LPG cylinders x4", "7600.00",
                "Indane Gas Agency, Benz Circle", today.minusDays(12), YearMonth.from(today.minusDays(12)));
        makeExpense(hostelId, ownerUserId, "Other Expenses", "Plumbing repair – 1st floor bathroom", "1800.00",
                "Durga Plumbing Works", today.minusDays(9), YearMonth.from(today.minusDays(9)));
        makeExpense(hostelId, ownerUserId, "Other Expenses", "Phenyl, detergents and toiletries restock", "3100.00",
                "D-Mart, Benz Circle", today.minusDays(5), YearMonth.from(today.minusDays(5)));
        makeExpense(hostelId, ownerUserId, "Other Expenses", "VMC property & water tax (half-yearly)", "18400.00",
                "Vijayawada Municipal Corporation", lastMonth.atDay(20), lastMonth);
    }

    /** A little activity for the other hostels so their dashboards are not empty. */
    private void seedLighterExtras(Map<String, Built> built, Map<String, List<HostellerProfile>> residents) {
        LocalDate today = LocalDate.now();
        for (Built b : built.values()) {
            if (MAIN_HOSTEL.equals(b.hostel().getName())) continue;
            Long hostelId = b.hostel().getId();
            Long ownerUserId = b.ownerUser().getId();
            makeAnnouncement(hostelId, ownerUserId, "Welcome to " + b.hostel().getName(),
                    "Please keep your ID proof updated in the Hostlify app and pay rent by the 5th of every month.",
                    "General", "NORMAL", today.minusDays(7), today.plusMonths(1));
            List<HostellerProfile> rs = residents.getOrDefault(b.hostel().getName(), List.of());
            if (!rs.isEmpty()) {
                HostellerProfile first = rs.get(0);
                makeComplaint(first.getId(), hostelId, bookingFor(first).getRoomId(), "Maintenance",
                        "Geyser not heating", "Bathroom geyser is not heating water since yesterday.", "MEDIUM", "OPEN", null);
            }
            makeExpense(hostelId, ownerUserId, "Electricity Bill", "APCPDCL electricity bill – " + YearMonth.now(),
                    "6200.00", "APCPDCL", YearMonth.now().atDay(6), YearMonth.now());
            makeExpense(hostelId, ownerUserId, "Other Expenses", "Broadband – " + YearMonth.now(),
                    "999.00", "AP State FiberNet (APSFL)", YearMonth.now().atDay(8), YearMonth.now());
        }
    }

    // ─── Small helpers ────────────────────────────────────────────────────────

    private BookingCheckin bookingFor(HostellerProfile p) {
        return bookingCheckinRepository.findFirstByHostellerIdAndStatus(p.getId(), "ACTIVE").orElseThrow();
    }

    private void seedFeatureFlags(Long ownerId, Long setBy, String plan) {
        String[] flags = {"online_payment", "referral_program", "parking_management", "cleaning_requests", "weekly_menu", "hostel_reviews"};
        for (String flag : flags) {
            FeatureFlag ff = new FeatureFlag();
            ff.setOwnerId(ownerId);
            ff.setFlagName(flag);
            ff.setEnabled(!"FREE".equals(plan) || flag.equals("weekly_menu"));
            ff.setSetBy(setBy);
            ff.setReason("Default for " + plan + " plan");
            featureFlagRepository.save(ff);
        }
    }

    private void seedWeeklyMenu(Long hostelId, Long createdBy) {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (weeklyMenuRepository.existsByHostelIdAndWeekStart(hostelId, weekStart)) return;
        WeeklyMenu menu = new WeeklyMenu();
        menu.setHostelId(hostelId);
        menu.setWeekStart(weekStart);
        menu.setCreatedBy(createdBy);
        menu = weeklyMenuRepository.save(menu);

        String[][] days = {
            {"Monday",    "Idli, Sambar, Allam (Ginger) Chutney", "Rice, Tomato Pappu, Bendakaya Fry, Rasam, Curd", "Chapati, Aloo Kurma, Rice, Sambar"},
            {"Tuesday",   "Upma, Coconut Chutney, Tea/Coffee",    "Rice, Gongura Pachadi, Dondakaya Fry, Sambar, Curd", "Pulka, Egg Curry / Paneer Curry, Rice, Rasam"},
            {"Wednesday", "Pesarattu, Upma, Allam Chutney",       "Rice, Palakura Pappu, Cabbage Fry, Majjiga Pulusu", "Chapati, Mixed Veg Curry, Rice, Dal"},
            {"Thursday",  "Poori, Potato Masala",                 "Rice, Mudda Pappu, Avakaya, Vankaya Curry, Curd",  "Pulka, Chicken Curry / Kadai Paneer, Rice"},
            {"Friday",    "Dosa, Peanut Chutney, Sambar",         "Veg Pulao, Raita, Rasam, Curd Rice",               "Chapati, Chana Masala, Rice, Sambar"},
            {"Saturday",  "Vada, Sambar, Chutney",                "Rice, Sambar, Beerakaya Curry, Appadam, Curd",     "Fried Rice, Gobi Manchurian, Soup"},
            {"Sunday",    "Bread Omelette / Mysore Bonda, Tea",   "Chicken Biryani / Veg Biryani, Mirchi ka Salan, Raita, Double ka Meetha", "Pulka, Paneer Butter Masala, Curd Rice"}
        };
        String[] timings = {"7:30 – 9:30 AM", "12:30 – 2:30 PM", "7:30 – 9:30 PM"};
        String[] mealTypes = {"BREAKFAST", "LUNCH", "DINNER"};
        for (String[] row : days) {
            for (int i = 0; i < 3; i++) {
                MenuItem item = new MenuItem();
                item.setMenuId(menu.getId());
                item.setDayOfWeek(row[0]);
                item.setMealType(mealTypes[i]);
                item.setItems(row[i + 1]);
                item.setTiming(timings[i]);
                menuItemRepository.save(item);
            }
        }
    }

    private void makeComplaint(Long hostellerId, Long hostelId, Long roomId, String category, String title,
                               String description, String priority, String status, String resolution) {
        ComplaintRecord c = new ComplaintRecord();
        c.setHostellerId(hostellerId); c.setHostelId(hostelId); c.setRoomId(roomId);
        c.setCategory(category); c.setTitle(title); c.setDescription(description);
        c.setPriority(priority); c.setStatus(status);
        if (resolution != null) {
            c.setResolutionNotes(resolution);
            c.setResolvedAt(LocalDateTime.now().minusDays(3));
        }
        complaintRecordRepository.save(c);
    }

    private void makeMaintenance(Long hostelId, Long roomId, Long reportedBy, String category, String description,
                                 String estimated, String actual, String vendor, String status, LocalDateTime completedAt) {
        ComplaintMaintenance m = new ComplaintMaintenance();
        m.setHostelId(hostelId); m.setRoomId(roomId); m.setReportedBy(reportedBy);
        m.setCategory(category); m.setDescription(description);
        m.setEstimatedCost(new BigDecimal(estimated));
        if (actual != null) m.setActualCost(new BigDecimal(actual));
        m.setVendorName(vendor); m.setStatus(status); m.setCompletedAt(completedAt);
        complaintMaintenanceRepository.save(m);
    }

    private void makeAnnouncement(Long hostelId, Long createdBy, String title, String body, String category,
                                  String priority, LocalDate validFrom, LocalDate validTill) {
        Announcement a = new Announcement();
        a.setHostelId(hostelId); a.setTitle(title); a.setBody(body);
        a.setCategory(category); a.setPriority(priority);
        a.setValidFrom(validFrom); a.setValidTill(validTill);
        a.setCreatedBy(createdBy);
        announcementRepository.save(a);
    }

    private void makeLeave(Long hostellerId, Long bookingId, String type, LocalDate from, LocalDate to,
                           String reason, String status, Long reviewedBy) {
        LeaveRequest lr = new LeaveRequest();
        lr.setHostellerId(hostellerId); lr.setBookingId(bookingId);
        lr.setLeaveType(type); lr.setFromDate(from); lr.setToDate(to);
        lr.setReason(reason); lr.setStatus(status);
        if (reviewedBy != null) {
            lr.setReviewedBy(reviewedBy);
            lr.setReviewedAt(from.minusDays(2).atTime(10, 0));
        }
        leaveRequestRepository.save(lr);
    }

    private void makeReferral(Long referrerId, Long referredId, String status, String discount) {
        ReferralRecord rr = new ReferralRecord();
        rr.setReferrerId(referrerId); rr.setReferredId(referredId);
        rr.setStatus(status); rr.setDiscountAmount(new BigDecimal(discount));
        referralRecordRepository.save(rr);
    }

    private void makeParking(Long hostellerId, Long hostelId, String vehicle, String type, String slot, LocalDate since) {
        ParkingRecord p = new ParkingRecord();
        p.setHostellerId(hostellerId); p.setHostelId(hostelId);
        p.setVehicleNumber(vehicle); p.setVehicleType(type);
        p.setSlotNumber(slot); p.setStatus("ACTIVE");
        p.setRegisteredAt(since.atTime(10, 0));
        parkingRecordRepository.save(p);
    }

    private void makeCleaning(Long hostellerId, Long hostelId, String roomNumber, LocalDate date, String status,
                              String notes, Long completedBy) {
        CleaningRequest cr = new CleaningRequest();
        cr.setHostellerId(hostellerId); cr.setHostelId(hostelId);
        cr.setRoomNumber(roomNumber); cr.setRequestDate(date);
        cr.setStatus(status); cr.setNotes(notes); cr.setCompletedBy(completedBy);
        cleaningRequestRepository.save(cr);
    }

    private void makeReview(Long hostellerId, Long hostelId, Long bookingId, int rating, String text) {
        HostelReview r = new HostelReview();
        r.setHostellerId(hostellerId); r.setHostelId(hostelId); r.setBookingId(bookingId);
        r.setRating(rating); r.setReviewText(text); r.setVisible(true);
        hostelReviewRepository.save(r);
    }

    private ExpenseEmployee makeEmployee(Long hostelId, String code, String name, String role, String mobile,
                                         String base, String hra) {
        ExpenseEmployee e = new ExpenseEmployee();
        e.setHostelId(hostelId); e.setEmployeeCode(code); e.setName(name);
        e.setRole(role); e.setMobile(mobile);
        e.setBaseSalary(new BigDecimal(base)); e.setHra(new BigDecimal(hra));
        return expenseEmployeeRepository.save(e);
    }

    private void makeSalary(Long empId, Long hostelId, YearMonth period, BigDecimal gross, String status) {
        ExpenseSalaryRecord s = new ExpenseSalaryRecord();
        s.setEmployeeId(empId); s.setHostelId(hostelId);
        s.setPeriodMonth(period.getMonthValue()); s.setPeriodYear(period.getYear());
        s.setGrossSalary(gross); s.setDeductions(BigDecimal.ZERO); s.setNetSalary(gross);
        s.setStatus(status);
        if ("PAID".equals(status)) s.setPaidAt(period.atEndOfMonth().atTime(10, 0));
        expenseSalaryRecordRepository.save(s);
    }

    private void makeExpense(Long hostelId, Long recordedBy, String category, String description, String amount,
                             String vendor, LocalDate date, YearMonth period) {
        ExpenseRecord e = new ExpenseRecord();
        e.setHostelId(hostelId); e.setCategory(category); e.setAmount(new BigDecimal(amount));
        e.setDescription(description); e.setVendorName(vendor); e.setExpenseDate(date);
        e.setPeriodMonth(period.getMonthValue()); e.setPeriodYear(period.getYear());
        e.setRecordedBy(recordedBy);
        expenseRecordRepository.save(e);
    }
}
