package com.hostlify.module.compliance.agreement;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.compliance.ResidentNoticeEvent;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.repository.BedRepository;
import com.hostlify.module.hostel.repository.HostelOwnerRepository;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.notification.service.EmailService;
import com.hostlify.module.payment.entity.HostelPaymentSetting;
import com.hostlify.module.payment.service.PaymentSettingsService;
import com.hostlify.shared.exception.AppException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgreementService {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-z_]+)\\s*}}");
    private static final int OTP_MINUTES = 10;

    public static final List<String> PLACEHOLDERS = List.of(
            "tenant_name", "tenant_mobile", "tenant_email", "tenant_permanent_address",
            "hostel_name", "hostel_address", "owner_name", "room", "bed", "start_date",
            "monthly_rent", "deposit", "rent_due_day", "notice_days", "late_fee_policy", "today");

    public static final String DEFAULT_TITLE = "Accommodation (Leave and Licence) Agreement";
    public static final String DEFAULT_BODY = """
            This agreement is made on {{today}} between {{owner_name}} ("Licensor"), operating {{hostel_name}} at {{hostel_address}}, and {{tenant_name}} ("Licensee"), mobile {{tenant_mobile}}, permanent address {{tenant_permanent_address}}.

            1. Accommodation. The Licensor grants the Licensee the right to use bed {{bed}} in room {{room}} at {{hostel_name}} from {{start_date}}, on a leave-and-licence basis. This does not create any tenancy or right to the property.

            2. Rent. The monthly charge is Rs. {{monthly_rent}}, payable on or before day {{rent_due_day}} of each month. {{late_fee_policy}}

            3. Security deposit. The Licensee has paid a refundable security deposit of Rs. {{deposit}}. It carries no interest and is refunded at checkout after deducting unpaid dues and the cost of any damage or missing items recorded in the move-out checklist.

            4. Notice period. Either party may end this agreement by giving {{notice_days}} days' notice. If the Licensee leaves without notice, rent for the notice period may be deducted from the deposit.

            5. House rules. The Licensee will follow the hostel rules shared by the Licensor, including timings, visitor rules, cleanliness and safe use of electrical appliances. Smoking, alcohol, illegal substances and any unlawful activity on the premises are not allowed.

            6. Use and care. The Licensee will not sublet or share the bed, will not make alterations, and will take reasonable care of furniture and fittings. Items handed over are listed in the move-in checklist.

            7. Identity. The Licensee confirms the identity details and documents provided are true. The Licensor may share them with the police if required by law.

            8. Termination. The Licensor may end this agreement immediately for non-payment beyond 15 days after the due date, or for serious breach of these terms.

            9. Acceptance. The Licensee accepts these terms electronically in the Hostlify app. The acceptance record (name, date and time, IP address and a fingerprint of this document) forms part of this agreement.
            """;

    private final AgreementTemplateRepository templateRepo;
    private final RentalAgreementRepository repo;
    private final HostellerProfileRepository profileRepo;
    private final HostelRepository hostelRepo;
    private final HostelOwnerRepository ownerRepo;
    private final BookingCheckinRepository bookingRepo;
    private final RoomRepository roomRepo;
    private final BedRepository bedRepo;
    private final PaymentSettingsService paymentSettings;
    private final EmailService emailService;
    private final com.hostlify.module.auth.repository.AuthUserRepository authUserRepo;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public AgreementService(AgreementTemplateRepository templateRepo, RentalAgreementRepository repo,
                            HostellerProfileRepository profileRepo, HostelRepository hostelRepo, HostelOwnerRepository ownerRepo,
                            BookingCheckinRepository bookingRepo, RoomRepository roomRepo, BedRepository bedRepo,
                            PaymentSettingsService paymentSettings, EmailService emailService,
                            com.hostlify.module.auth.repository.AuthUserRepository authUserRepo, ApplicationEventPublisher events) {
        this.templateRepo = templateRepo;
        this.repo = repo;
        this.profileRepo = profileRepo;
        this.hostelRepo = hostelRepo;
        this.ownerRepo = ownerRepo;
        this.bookingRepo = bookingRepo;
        this.roomRepo = roomRepo;
        this.bedRepo = bedRepo;
        this.paymentSettings = paymentSettings;
        this.emailService = emailService;
        this.authUserRepo = authUserRepo;
        this.events = events;
    }

    // ─── Template ──────────────────────────────────────────────────────────

    public AgreementTemplate template(Long hostelId) {
        return templateRepo.findByHostelId(hostelId).orElseGet(() -> {
            AgreementTemplate t = new AgreementTemplate();
            t.setHostelId(hostelId);
            t.setTitle(DEFAULT_TITLE);
            t.setBody(DEFAULT_BODY);
            return t;
        });
    }

    @Transactional
    public AgreementTemplate saveTemplate(Long hostelId, String title, String body, Integer noticeDays, Long userId) {
        if (title == null || title.isBlank() || title.length() > 150) throw bad("Title is required (max 150 characters)");
        if (body == null || body.trim().length() < 50) throw bad("The agreement text is too short");
        if (body.length() > 30000) throw bad("The agreement text is too long (max 30,000 characters)");
        Matcher m = PLACEHOLDER.matcher(body);
        while (m.find()) {
            if (!PLACEHOLDERS.contains(m.group(1))) throw bad("Unknown placeholder {{" + m.group(1) + "}}");
        }
        AgreementTemplate t = template(hostelId);
        t.setTitle(title.trim());
        t.setBody(body.trim());
        if (noticeDays != null) {
            if (noticeDays < 0 || noticeDays > 180) throw bad("Notice period must be 0-180 days");
            t.setNoticeDays(noticeDays);
        }
        t.setUpdatedBy(userId);
        t.setUpdatedAt(LocalDateTime.now());
        return templateRepo.save(t);
    }

    @Transactional
    public AgreementTemplate resetTemplate(Long hostelId, Long userId) {
        return saveTemplate(hostelId, DEFAULT_TITLE, DEFAULT_BODY, 30, userId);
    }

    // ─── Agreements ────────────────────────────────────────────────────────

    /** Fills the template for the resident's active booking and sends it to them. One open agreement at a time. */
    @Transactional
    public RentalAgreement create(Long hostellerId, Long staffUserId) {
        HostellerProfile p = profile(hostellerId);
        if (p.getHostelId() == null) throw bad("This resident isn't allocated to a hostel");
        if (repo.existsByHostellerIdAndStatusIn(hostellerId, List.of("SENT", "ACCEPTED"))) {
            throw new AppException("This resident already has an agreement. Void it first to send a new one.", HttpStatus.CONFLICT);
        }
        BookingCheckin booking = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE")
                .orElseThrow(() -> bad("This resident has no active booking"));
        AgreementTemplate t = template(p.getHostelId());
        String body = fill(t.getBody(), values(p, booking, t));

        RentalAgreement a = new RentalAgreement();
        a.setHostelId(p.getHostelId());
        a.setHostellerId(hostellerId);
        a.setBookingId(booking.getId());
        a.setTitle(t.getTitle());
        a.setBody(body);
        a.setBodySha256(sha256(t.getTitle() + "\n" + body));
        a.setStatus("SENT");
        a.setCreatedBy(staffUserId);
        RentalAgreement saved = repo.save(a);
        events.publishEvent(new ResidentNoticeEvent(this, hostellerId, "AGREEMENT_SENT",
                "Please review and accept your accommodation agreement",
                "Your hostel has shared your accommodation agreement. Please read it and accept it in the app.", "/app/documents"));
        return saved;
    }

    @Transactional
    public RentalAgreement voidAgreement(Long id, String reason) {
        RentalAgreement a = get(id);
        if ("VOID".equals(a.getStatus())) throw new AppException("Already void", HttpStatus.CONFLICT);
        a.setStatus("VOID");
        a.setVoidedAt(LocalDateTime.now());
        a.setVoidReason(reason == null ? null : reason.length() > 300 ? reason.substring(0, 300) : reason);
        return repo.save(a);
    }

    /** Emails a 6-digit code when email is set up. Returns whether a code is needed to accept. */
    @Transactional
    public Map<String, Object> requestOtp(Long id, Long hostellerId) {
        RentalAgreement a = ownOpen(id, hostellerId);
        if (!emailService.isEnabled()) return Map.of("otpRequired", false);
        HostellerProfile p = profile(hostellerId);
        String email = authUserRepo.findById(p.getAuthUserId()).map(u -> u.getEmail()).orElse(p.getEmail());
        if (email == null || email.isBlank()) return Map.of("otpRequired", false);
        String otp = String.format("%06d", random.nextInt(1_000_000));
        a.setOtpHash(sha256(a.getId() + ":" + otp));
        a.setOtpExpiresAt(LocalDateTime.now().plusMinutes(OTP_MINUTES));
        a.setOtpAttempts(0);
        repo.save(a);
        emailService.sendEmail(email, "Your code to accept the agreement: " + otp,
                "Your code to accept \"" + a.getTitle() + "\" is " + otp + ". It expires in " + OTP_MINUTES + " minutes.\n\n"
                        + "If you didn't request this, contact your hostel.");
        return Map.of("otpRequired", true, "sentTo", maskEmail(email));
    }

    @Transactional(noRollbackFor = AppException.class)
    public RentalAgreement accept(Long id, Long hostellerId, String typedName, String otp, boolean agree, String ip, String userAgent) {
        RentalAgreement a = ownOpen(id, hostellerId);
        if (!agree) throw bad("Tick the box to confirm you agree");
        HostellerProfile p = profile(hostellerId);
        String expected = (p.getFirstName() + " " + (p.getLastName() == null ? "" : p.getLastName())).trim();
        if (typedName == null || !norm(typedName).equals(norm(expected))) {
            throw bad("Type your full name exactly as: " + expected);
        }
        boolean otpUsed = false;
        if (a.getOtpHash() != null) {
            if (a.getOtpExpiresAt() == null || a.getOtpExpiresAt().isBefore(LocalDateTime.now())) throw bad("The code has expired. Request a new one.");
            if (a.getOtpAttempts() >= 5) throw bad("Too many wrong codes. Request a new one.");
            if (otp == null || !sha256(a.getId() + ":" + otp.trim()).equals(a.getOtpHash())) {
                a.setOtpAttempts(a.getOtpAttempts() + 1);
                repo.save(a);
                throw bad("Wrong code");
            }
            otpUsed = true;
        } else if (emailService.isEnabled() && hasEmail(p)) {
            throw bad("Request the verification code first");
        }
        // The document must be exactly what was sent
        if (!sha256(a.getTitle() + "\n" + a.getBody()).equals(a.getBodySha256())) {
            throw new AppException("This agreement can't be accepted because its text has changed. Ask the hostel to send it again.", HttpStatus.CONFLICT);
        }
        a.setStatus("ACCEPTED");
        a.setAcceptedAt(LocalDateTime.now());
        a.setAcceptedName(typedName.trim());
        a.setAcceptedIp(ip == null ? null : ip.length() > 64 ? ip.substring(0, 64) : ip);
        a.setAcceptedUserAgent(userAgent == null ? null : userAgent.length() > 300 ? userAgent.substring(0, 300) : userAgent);
        a.setAcceptedWithOtp(otpUsed);
        a.setOtpHash(null);
        return repo.save(a);
    }

    public RentalAgreement get(Long id) {
        return repo.findById(id).orElseThrow(() -> new AppException("Agreement not found", HttpStatus.NOT_FOUND));
    }

    public List<RentalAgreement> forResident(Long hostellerId) {
        return repo.findByHostellerIdOrderBySentAtDesc(hostellerId);
    }

    /** Preview of the filled-in template for a resident (nothing saved). */
    public Map<String, String> preview(Long hostellerId) {
        HostellerProfile p = profile(hostellerId);
        BookingCheckin booking = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE").orElse(null);
        AgreementTemplate t = template(p.getHostelId());
        return Map.of("title", t.getTitle(), "body", fill(t.getBody(), values(p, booking, t)));
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private RentalAgreement ownOpen(Long id, Long hostellerId) {
        RentalAgreement a = get(id);
        if (!a.getHostellerId().equals(hostellerId)) throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        if (!"SENT".equals(a.getStatus())) throw new AppException("This agreement is " + a.getStatus().toLowerCase(), HttpStatus.CONFLICT);
        return a;
    }

    Map<String, String> values(HostellerProfile p, BookingCheckin b, AgreementTemplate t) {
        Hostel h = hostelRepo.findById(p.getHostelId()).orElseThrow(() -> bad("Hostel not found"));
        HostelPaymentSetting ps = paymentSettings.getOrDefault(h.getId());
        String owner = ps.getLandlordName() != null ? ps.getLandlordName()
                : ownerRepo.findById(h.getOwnerId()).map(o -> o.getOwnerName()).orElse(h.getName());
        Map<String, String> v = new HashMap<>();
        v.put("tenant_name", (p.getFirstName() + " " + (p.getLastName() == null ? "" : p.getLastName())).trim());
        v.put("tenant_mobile", nz(p.getMobile()));
        v.put("tenant_email", nz(p.getEmail()));
        v.put("tenant_permanent_address", nz(p.getPermanentAddress()));
        v.put("hostel_name", h.getName());
        v.put("hostel_address", String.join(", ", List.of(nz(h.getAddress()), nz(h.getCity()), nz(h.getPincode())).stream().filter(s -> !s.isBlank()).toList()));
        v.put("owner_name", owner);
        v.put("room", b == null ? "-" : roomRepo.findById(b.getRoomId()).map(r -> r.getRoomName()).orElse("-"));
        v.put("bed", b == null ? "-" : bedRepo.findById(b.getBedId()).map(x -> x.getBedName()).orElse("-"));
        v.put("start_date", b == null || b.getCheckinDate() == null ? "-" : b.getCheckinDate().format(D));
        v.put("monthly_rent", b == null ? "-" : money(b.getRentAmount()));
        v.put("deposit", b == null ? "-" : money(b.getAdvanceAmount()));
        v.put("rent_due_day", String.valueOf(ps.getDueDay()));
        v.put("notice_days", String.valueOf(t.getNoticeDays()));
        v.put("late_fee_policy", !ps.isLateFeeEnabled() ? "No late fee is charged."
                : "PER_DAY".equals(ps.getLateFeeType())
                ? "A late fee of Rs. " + money(ps.getLateFeeAmount()) + " per day applies after " + ps.getLateFeeGraceDays() + " day(s) past the due date"
                        + (ps.getLateFeeMaxAmount() != null ? ", up to Rs. " + money(ps.getLateFeeMaxAmount()) : "") + "."
                : "A late fee of Rs. " + money(ps.getLateFeeAmount()) + " applies if rent is paid more than " + ps.getLateFeeGraceDays() + " day(s) after the due date.");
        v.put("today", LocalDate.now().format(D));
        return v;
    }

    static String fill(String template, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String val = values.getOrDefault(m.group(1), "");
            m.appendReplacement(sb, Matcher.quoteReplacement(val.isBlank() ? "________" : val));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean hasEmail(HostellerProfile p) {
        String e = authUserRepo.findById(p.getAuthUserId()).map(u -> u.getEmail()).orElse(p.getEmail());
        return e != null && !e.isBlank();
    }

    private HostellerProfile profile(Long id) {
        return profileRepo.findById(id).orElseThrow(() -> new AppException("Resident not found", HttpStatus.NOT_FOUND));
    }

    private static String norm(String s) { return s.trim().replaceAll("\\s+", " ").toLowerCase(); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static String money(BigDecimal b) { return b == null ? "0" : b.stripTrailingZeros().toPlainString(); }
    private static String maskEmail(String e) {
        int at = e.indexOf('@');
        return at <= 1 ? e : e.charAt(0) + "***" + e.substring(at);
    }
    private static AppException bad(String m) { return new AppException(m, HttpStatus.BAD_REQUEST); }
}
