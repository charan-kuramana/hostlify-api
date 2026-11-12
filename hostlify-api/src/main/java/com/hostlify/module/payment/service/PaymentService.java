package com.hostlify.module.payment.service;

import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hostel.repository.RoomRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.payment.entity.HostelPaymentSetting;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRazorpayOrder;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.event.PaymentReceivedEvent;
import com.hostlify.module.payment.event.UpiPaymentClaimEvent;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import com.hostlify.module.payment.repository.PaymentRazorpayOrderRepository;
import com.hostlify.module.payment.repository.PaymentRecordRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Pattern UTR = Pattern.compile("^[A-Za-z0-9]{6,30}$");

    private final PaymentDueRepository dueRepo;
    private final PaymentRazorpayOrderRepository orderRepo;
    private final PaymentRecordRepository recordRepo;
    private final BookingCheckinRepository bookingRepo;
    private final HostellerProfileRepository profileRepo;
    private final AuthUserRepository authUserRepo;
    private final RoomRepository roomRepo;
    private final HostelRepository hostelRepo;
    private final PaymentSettingsService settingsService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.razorpay.key-id:test_key}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret:test_secret}")
    private String razorpayKeySecret;

    public PaymentService(PaymentDueRepository dueRepo,
                          PaymentRazorpayOrderRepository orderRepo,
                          PaymentRecordRepository recordRepo,
                          BookingCheckinRepository bookingRepo,
                          HostellerProfileRepository profileRepo,
                          AuthUserRepository authUserRepo,
                          RoomRepository roomRepo,
                          HostelRepository hostelRepo,
                          PaymentSettingsService settingsService,
                          ApplicationEventPublisher eventPublisher) {
        this.dueRepo = dueRepo;
        this.orderRepo = orderRepo;
        this.recordRepo = recordRepo;
        this.bookingRepo = bookingRepo;
        this.profileRepo = profileRepo;
        this.authUserRepo = authUserRepo;
        this.roomRepo = roomRepo;
        this.hostelRepo = hostelRepo;
        this.settingsService = settingsService;
        this.eventPublisher = eventPublisher;
    }

    // ─── Due Queries ───────────────────────────────────────────────

    public List<PaymentDue> getDuesByHosteller(Long hostellerId) {
        List<PaymentDue> dues = dueRepo.findByHostellerIdOrderByDueDateDesc(hostellerId);
        dues.forEach(this::enrichAmounts);
        return dues;
    }

    public PaymentDue getCurrentDue(Long authUserId) {
        HostellerProfile profile = profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        LocalDate today = LocalDate.now();
        PaymentDue due = dueRepo.findRentDue(profile.getId(), today.getYear(), today.getMonthValue())
                .orElseThrow(() -> new AppException("No due for current month", HttpStatus.NOT_FOUND));
        enrichAmounts(due);
        return due;
    }

    public List<PaymentDue> getPendingDuesByHostel(Long hostelId) {
        // Anything not fully paid: a PARTIAL due used to vanish from the staff list, leaving no way to
        // collect the balance. enrichDue also tolerates a null bookingId (findById(null) threw → 500).
        List<PaymentDue> dues = new ArrayList<>();
        for (String st : List.of("UNPAID", "PARTIAL", "OVERDUE")) dues.addAll(dueRepo.findByHostelIdAndStatus(hostelId, st));
        dues.sort(Comparator.comparing(PaymentDue::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())));
        dues.forEach(this::enrichDue);
        return dues;
    }

    public PaymentDue getDue(Long id) {
        return dueRepo.findById(id).orElseThrow(() -> new AppException("Due not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public PaymentDue createDue(PaymentDue due) {
        return dueRepo.save(due);
    }

    @Transactional
    public PaymentDue markPaid(Long id) {
        PaymentDue due = getDue(id);
        due.setStatus("PAID");
        return dueRepo.save(due);
    }

    // ─── Charges other than rent (deposit, mess, electricity, fine, other) ──────

    /**
     * Adds a charge for one resident, or for every active resident of the hostel when hostellerId is null.
     * @return the dues created
     */
    @Transactional
    public List<PaymentDue> createCharges(Long hostelId, Long hostellerId, String dueType, BigDecimal amount,
                                          String description, LocalDate dueDate) {
        String type = dueType == null ? "" : dueType.toUpperCase();
        if (!PaymentDue.MANUAL_TYPES.contains(type)) {
            throw new AppException("Charge type must be one of " + PaymentDue.MANUAL_TYPES, HttpStatus.BAD_REQUEST);
        }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("1000000")) > 0) {
            throw new AppException("Amount must be between 1 and 10,00,000", HttpStatus.BAD_REQUEST);
        }
        if (description != null && description.length() > 300) description = description.substring(0, 300);
        LocalDate due = dueDate != null ? dueDate : DateUtils.todayKolkata();

        List<BookingCheckin> targets;
        if (hostellerId != null) {
            HostellerProfile p = profileRepo.findById(hostellerId)
                    .orElseThrow(() -> new AppException("Resident not found", HttpStatus.NOT_FOUND));
            if (!Objects.equals(p.getHostelId(), hostelId)) {
                throw new AppException("This resident is not in your hostel", HttpStatus.BAD_REQUEST);
            }
            BookingCheckin b = bookingRepo.findFirstByHostellerIdAndStatus(hostellerId, "ACTIVE").orElse(null);
            if (b == null) {
                // No active booking (e.g. a fine after checkout): still allowed, without a booking link
                b = new BookingCheckin();
                b.setHostellerId(hostellerId);
            }
            targets = List.of(b);
        } else {
            targets = bookingRepo.findByHostelIdAndStatus(hostelId, "ACTIVE");
            if (targets.isEmpty()) throw new AppException("No active residents in this hostel", HttpStatus.BAD_REQUEST);
        }

        List<PaymentDue> created = new ArrayList<>();
        for (BookingCheckin b : targets) {
            PaymentDue d = new PaymentDue();
            d.setHostellerId(b.getHostellerId());
            d.setBookingId(b.getId());
            d.setHostelId(hostelId);
            d.setPeriodMonth(due.getMonthValue());
            d.setPeriodYear(due.getYear());
            d.setDueDate(due);
            d.setRentAmount(amount);
            d.setDueType(type);
            d.setDescription(description != null && !description.isBlank() ? description.trim() : null);
            d.setStatus("UNPAID");
            created.add(dueRepo.save(d));
        }
        created.forEach(this::enrichDue);
        return created;
    }

    /** Cancels a charge nobody has paid toward yet (e.g. waive a late fee or a fine). */
    @Transactional
    public void waiveDue(Long dueId) {
        PaymentDue due = getDue(dueId);
        if ("PAID".equals(due.getStatus())) {
            throw new AppException("A paid due cannot be waived", HttpStatus.CONFLICT);
        }
        boolean anyPayment = recordRepo.findByDueId(dueId).stream()
                .anyMatch(r -> r.isSuccessful() || PaymentRecord.STATUS_PENDING_VERIFICATION.equals(r.getStatus()));
        if (anyPayment) {
            throw new AppException("This due already has payments against it; use a discount adjustment instead",
                    HttpStatus.CONFLICT);
        }
        due.setDeleted(true);
        due.setDeletedAt(LocalDateTime.now());
        dueRepo.save(due);
    }

    // ─── Razorpay Order ─────────────────────────────────────────────

    private static final long RAZORPAY_TIMEOUT_SECONDS = 10;

    @Transactional
    public PaymentRazorpayOrder initiateRazorpayPayment(Long authUserId, Long dueId) {
        HostellerProfile profile = profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        PaymentDue due = getDue(dueId);
        if (!Objects.equals(due.getHostellerId(), profile.getId())) {
            throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        }

        if ("PAID".equals(due.getStatus())) {
            throw new AppException("Due is already paid", HttpStatus.CONFLICT);
        }

        // Charge only what is still owed (a partial cash payment may already have been recorded)
        BigDecimal outstanding = due.getRentAmount().subtract(paidSoFar(dueId));
        if (outstanding.signum() <= 0) {
            throw new AppException("Due is already paid", HttpStatus.CONFLICT);
        }
        long amountPaise = outstanding.multiply(BigDecimal.valueOf(100)).longValue();

        String razorpayOrderId;
        try {
            // Bounded wait: if the gateway is unreachable or hangs, the resident gets an answer instead of a spinner
            razorpayOrderId = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    com.razorpay.RazorpayClient client = new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);
                    org.json.JSONObject params = new org.json.JSONObject();
                    params.put("amount", amountPaise);
                    params.put("currency", "INR");
                    params.put("receipt", "HOST-" + dueId);
                    com.razorpay.Order order = client.orders.create(params);
                    return order.<String>get("id");
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }).get(RAZORPAY_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException("Online payment is temporarily unavailable. Please pay at the hostel reception.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        } catch (Exception e) {
            // e.g. gateway keys not configured / Razorpay unreachable — tell the resident what to do instead
            throw new AppException("Online payment is temporarily unavailable. Please pay at the hostel reception.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        PaymentRazorpayOrder order = new PaymentRazorpayOrder();
        order.setHostellerId(profile.getId());
        order.setDueId(dueId);
        order.setRazorpayOrderId(razorpayOrderId);
        order.setAmountPaise(amountPaise);
        order.setStatus("CREATED");
        order.setExpiresAt(LocalDateTime.now().plusHours(1));
        return orderRepo.save(order);
    }

    /** Builds everything the frontend Razorpay Checkout widget needs (incl. the public key id). */
    @Transactional
    public Map<String, Object> initiateRazorpayCheckout(Long authUserId, Long dueId) {
        HostellerProfile profile = profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        PaymentRazorpayOrder order = initiateRazorpayPayment(authUserId, dueId);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("keyId", razorpayKeyId);
        info.put("orderId", order.getRazorpayOrderId());
        info.put("amountPaise", order.getAmountPaise());
        info.put("currency", "INR");
        info.put("dueId", dueId);
        info.put("hostellerName", profile.getFirstName() + " " + (profile.getLastName() != null ? profile.getLastName() : "").trim());
        info.put("description", ReceiptPdfService.typeLabel(getDue(dueId).getDueType()) + " payment");
        return info;
    }

    /** Verifies the Razorpay checkout signature, captures the payment, and returns the payment details. */
    @Transactional
    public Map<String, Object> verifyAndCaptureRazorpay(String razorpayPaymentId, String razorpayOrderId, String signature) {
        PaymentRazorpayOrder rpOrder = orderRepo.findByRazorpayOrderId(razorpayOrderId)
                .orElseThrow(() -> new AppException("Order not found", HttpStatus.NOT_FOUND));
        String payload = razorpayOrderId + "|" + razorpayPaymentId;
        if (!com.hostlify.shared.util.HmacUtils.verifyHmac(razorpayKeySecret, payload, signature)) {
            throw new AppException("Payment signature verification failed", HttpStatus.BAD_REQUEST);
        }
        PaymentRecord rec = handleRazorpayPaymentCaptured(razorpayPaymentId, razorpayOrderId, rpOrder.getAmountPaise());
        PaymentDue due = dueRepo.findById(rpOrder.getDueId()).orElse(null);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", "PAID");
        details.put("amount", rec != null ? rec.getAmount() : null);
        details.put("paymentId", razorpayPaymentId);
        details.put("orderId", razorpayOrderId);
        details.put("method", rec != null ? rec.getPaymentMethod() : "RAZORPAY");
        details.put("paymentDate", rec != null ? rec.getPaymentDate() : null);
        details.put("recordId", rec != null ? rec.getId() : null);
        details.put("dueId", rpOrder.getDueId());
        if (due != null) details.put("period", due.getPeriodMonth() + "/" + due.getPeriodYear());
        return details;
    }

    // ─── Direct UPI (to the owner's own UPI ID, no gateway fee) ─────────────

    /** Resident: "I paid by UPI" — creates a payment waiting for staff to confirm it in their UPI app. */
    @Transactional
    public PaymentRecord submitUpiClaim(Long authUserId, Long dueId, BigDecimal amount, String utr) {
        HostellerProfile profile = profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        PaymentDue due = getDue(dueId);
        if (!Objects.equals(due.getHostellerId(), profile.getId())) {
            throw new AppException("Access denied", HttpStatus.FORBIDDEN);
        }
        if ("PAID".equals(due.getStatus())) throw new AppException("This due is already paid", HttpStatus.CONFLICT);

        HostelPaymentSetting settings = settingsService.getOrDefault(due.getHostelId());
        if (settings.getUpiId() == null) {
            throw new AppException("This hostel has not set up UPI payments yet. Please pay at the reception.",
                    HttpStatus.BAD_REQUEST);
        }
        String ref = utr == null ? "" : utr.trim().replace(" ", "");
        if (!UTR.matcher(ref).matches()) {
            throw new AppException("Enter the UPI transaction ID / UTR shown in your UPI app (usually 12 digits)",
                    HttpStatus.BAD_REQUEST);
        }
        boolean duplicate = recordRepo.findByHostelIdOrderByCreatedAtDesc(due.getHostelId()).stream()
                .anyMatch(r -> ref.equalsIgnoreCase(r.getTransactionRef()) && !PaymentRecord.STATUS_REJECTED.equals(r.getStatus()));
        if (duplicate) throw new AppException("This UPI transaction ID has already been submitted", HttpStatus.CONFLICT);

        BigDecimal pending = pendingVerification(dueId);
        BigDecimal outstanding = due.getRentAmount().subtract(paidSoFar(dueId)).subtract(pending);
        if (amount == null || amount.signum() <= 0) throw new AppException("Amount must be greater than 0", HttpStatus.BAD_REQUEST);
        if (amount.compareTo(outstanding) > 0) {
            throw new AppException(outstanding.signum() <= 0
                    ? "A payment for this due is already waiting for confirmation"
                    : "Amount exceeds the outstanding balance of " + outstanding, HttpStatus.BAD_REQUEST);
        }

        PaymentRecord r = new PaymentRecord();
        r.setHostellerId(profile.getId());
        r.setHostelId(due.getHostelId());
        r.setDueId(dueId);
        r.setAmount(amount);
        r.setPaymentMethod("UPI_DIRECT");
        r.setTransactionRef(ref);
        r.setPaymentDate(DateUtils.todayKolkata());
        r.setStatus(PaymentRecord.STATUS_PENDING_VERIFICATION);
        r.setNotes("Paid to " + settings.getUpiId());
        PaymentRecord saved = recordRepo.save(r);

        eventPublisher.publishEvent(new UpiPaymentClaimEvent(this, UpiPaymentClaimEvent.Kind.SUBMITTED, saved,
                fullName(profile), null));
        return saved;
    }

    /** Staff: confirm (money arrived in the owner's UPI app) or reject a resident's UPI payment. */
    @Transactional
    public PaymentRecord verifyUpiClaim(Long staffUserId, Long recordId, boolean approve, String reason) {
        PaymentRecord r = getRecord(recordId);
        if (!PaymentRecord.STATUS_PENDING_VERIFICATION.equals(r.getStatus())) {
            throw new AppException("This payment has already been " + r.getStatus().toLowerCase(), HttpStatus.CONFLICT);
        }
        r.setVerifiedBy(staffUserId);
        r.setVerifiedAt(LocalDateTime.now());
        HostellerProfile profile = profileRepo.findById(r.getHostellerId()).orElse(null);

        if (!approve) {
            r.setStatus(PaymentRecord.STATUS_REJECTED);
            if (reason != null && !reason.isBlank()) {
                String note = (r.getNotes() != null ? r.getNotes() + " | " : "") + "Rejected: " + reason.trim();
                r.setNotes(note.length() > 500 ? note.substring(0, 500) : note);
            }
            PaymentRecord saved = recordRepo.save(r);
            eventPublisher.publishEvent(new UpiPaymentClaimEvent(this, UpiPaymentClaimEvent.Kind.REJECTED, saved,
                    fullName(profile), reason));
            return saved;
        }

        PaymentDue due = r.getDueId() != null ? dueRepo.findById(r.getDueId()).orElse(null) : null;
        if (due != null) {
            BigDecimal outstanding = due.getRentAmount().subtract(paidSoFar(due.getId()));
            if (r.getAmount().compareTo(outstanding) > 0) {
                throw new AppException("Confirming this would exceed the outstanding balance of " + outstanding
                        + ". Reject it and record the correct amount instead.", HttpStatus.CONFLICT);
            }
        }
        r.setStatus(PaymentRecord.STATUS_SUCCESS);
        PaymentRecord saved = recordRepo.save(r);
        if (due != null) {
            refreshDueStatus(due);
            publishPaymentReceived(saved, due, profile);
        }
        return saved;
    }

    public List<Map<String, Object>> getPendingUpiClaims(Long hostelId) {
        return recordRepo.findByHostelIdAndStatusOrderByCreatedAtDesc(hostelId, PaymentRecord.STATUS_PENDING_VERIFICATION)
                .stream().map(this::recordView).toList();
    }

    // ─── Payment records (transactions) ─────────────────────────────

    public PaymentRecord getRecord(Long id) {
        return recordRepo.findById(id).orElseThrow(() -> new AppException("Payment not found", HttpStatus.NOT_FOUND));
    }

    public List<Map<String, Object>> getRecordsByHostellerAuthUser(Long authUserId) {
        HostellerProfile profile = profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        return recordRepo.findByHostellerIdOrderByCreatedAtDesc(profile.getId()).stream().map(this::recordView).toList();
    }

    /** All dues (paid + unpaid) for a hostel, enriched with hosteller name + room. */
    public List<PaymentDue> getAllDuesByHostel(Long hostelId) {
        List<PaymentDue> dues = dueRepo.findByHostelIdOrderByDueDateDesc(hostelId);
        dues.forEach(this::enrichDue);
        return dues;
    }

    private BigDecimal paidSoFar(Long dueId) {
        return recordRepo.findByDueId(dueId).stream()
                .filter(PaymentRecord::isSuccessful)
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal pendingVerification(Long dueId) {
        return recordRepo.findByDueIdAndStatus(dueId, PaymentRecord.STATUS_PENDING_VERIFICATION).stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Sets PAID / PARTIAL / UNPAID from the successful payments recorded against the due. */
    private void refreshDueStatus(PaymentDue due) {
        BigDecimal paid = paidSoFar(due.getId());
        if (paid.compareTo(due.getRentAmount()) >= 0) due.setStatus("PAID");
        else if (paid.signum() > 0) due.setStatus("PARTIAL");
        else due.setStatus("UNPAID");
        dueRepo.save(due);
    }

    private void enrichAmounts(PaymentDue due) {
        due.setPaidAmount(paidSoFar(due.getId()));
        due.setPendingVerificationAmount(pendingVerification(due.getId()));
    }

    private void enrichDue(PaymentDue due) {
        enrichAmounts(due);
        profileRepo.findById(due.getHostellerId()).ifPresent(p -> {
            due.setHostellerName(fullName(p));
            due.setHostellerMobile(p.getMobile());
        });
        if (due.getBookingId() != null) {
            bookingRepo.findById(due.getBookingId()).ifPresent(b ->
                    roomRepo.findById(b.getRoomId()).ifPresent(r -> due.setRoomNumber(r.getRoomName())));
        }
    }

    private Map<String, Object> recordView(PaymentRecord rec) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rec.getId());
        m.put("hostellerId", rec.getHostellerId());
        m.put("amount", rec.getAmount());
        m.put("paymentMethod", rec.getPaymentMethod());
        m.put("transactionRef", rec.getRazorpayPaymentId() != null ? rec.getRazorpayPaymentId() : rec.getTransactionRef());
        m.put("razorpayPaymentId", rec.getRazorpayPaymentId());
        m.put("paymentDate", rec.getPaymentDate());
        m.put("createdAt", rec.getCreatedAt());
        m.put("status", rec.getStatus());
        m.put("notes", rec.getNotes());
        m.put("receiptNo", rec.isSuccessful() && rec.getId() != null ? ReceiptPdfService.receiptNumber(rec) : null);
        profileRepo.findById(rec.getHostellerId()).ifPresent(p -> {
            m.put("hostellerName", fullName(p));
            m.put("hostellerMobile", p.getMobile());
        });
        if (rec.getDueId() != null) {
            dueRepo.findById(rec.getDueId()).ifPresent(d -> {
                m.put("dueId", d.getId());
                m.put("period", d.getPeriodMonth() + "/" + d.getPeriodYear());
                m.put("periodMonth", d.getPeriodMonth());
                m.put("periodYear", d.getPeriodYear());
                m.put("dueType", d.getDueType());
                m.put("dueAmount", d.getRentAmount());
            });
        }
        return m;
    }

    /** All payment transactions received for a hostel, enriched with payer name + room for staff. */
    public List<Map<String, Object>> getRecordsByHostel(Long hostelId) {
        // Pending UPI claims live in their own "To verify" list; rejected ones are noise here
        return recordRepo.findByHostelIdOrderByCreatedAtDesc(hostelId).stream()
                .filter(PaymentRecord::isSuccessful)
                .map(this::recordView)
                .toList();
    }

    // ─── Cash Payment ────────────────────────────────────────────────

    @Transactional
    public PaymentRecord recordCashPayment(Long staffUserId, Long hostellerId,
                                           Long dueId, BigDecimal amount,
                                           String paymentMethod, String transactionRef,
                                           LocalDate paymentDate, String notes) {
        PaymentDue due = getDue(dueId);
        HostellerProfile profile = profileRepo.findById(hostellerId)
                .orElseThrow(() -> new AppException("Hosteller not found", HttpStatus.NOT_FOUND));
        if (!hostellerId.equals(due.getHostellerId())) {
            throw new AppException("This due belongs to a different hosteller", HttpStatus.BAD_REQUEST);
        }
        if ("PAID".equals(due.getStatus())) {
            throw new AppException("This due is already paid", HttpStatus.CONFLICT);
        }
        if (amount == null || amount.signum() <= 0) {
            throw new AppException("Amount must be greater than 0", HttpStatus.BAD_REQUEST);
        }
        BigDecimal alreadyPaid = paidSoFar(dueId);
        BigDecimal outstanding = due.getRentAmount().subtract(alreadyPaid);
        if (amount.compareTo(outstanding) > 0) {
            throw new AppException("Amount exceeds the outstanding balance of " + outstanding, HttpStatus.BAD_REQUEST);
        }

        PaymentRecord record = new PaymentRecord();
        record.setHostellerId(hostellerId);
        record.setHostelId(due.getHostelId());
        record.setDueId(dueId);
        record.setAmount(amount);
        record.setPaymentMethod(paymentMethod != null ? paymentMethod : "CASH");
        record.setTransactionRef(transactionRef);
        record.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now());
        record.setNotes(notes);
        record.setRecordedBy(staffUserId);
        record.setStatus(PaymentRecord.STATUS_SUCCESS);
        PaymentRecord saved = recordRepo.save(record);

        // Cumulative: two partial payments that add up to the amount settle the due
        refreshDueStatus(due);
        publishPaymentReceived(saved, due, profile);
        return saved;
    }

    // ─── Webhook Handler Support ─────────────────────────────────────

    @Transactional
    public PaymentRecord handleRazorpayPaymentCaptured(String razorpayPaymentId,
                                                       String razorpayOrderId,
                                                       long amountPaise) {
        // Idempotency: return the already-recorded payment if present
        var existing = recordRepo.findByRazorpayPaymentId(razorpayPaymentId);
        if (existing.isPresent()) {
            return existing.get();
        }

        PaymentRazorpayOrder rpOrder = orderRepo.findByRazorpayOrderId(razorpayOrderId)
                .orElse(null);
        if (rpOrder == null) return null; // Unknown order — log and skip

        rpOrder.setStatus("PAID");
        orderRepo.save(rpOrder);

        PaymentDue due = dueRepo.findById(rpOrder.getDueId()).orElse(null);
        BigDecimal amount = BigDecimal.valueOf(amountPaise).divide(BigDecimal.valueOf(100));

        PaymentRecord record = new PaymentRecord();
        record.setHostellerId(rpOrder.getHostellerId());
        record.setDueId(rpOrder.getDueId());
        record.setAmount(amount);
        record.setPaymentMethod("RAZORPAY");
        record.setRazorpayPaymentId(razorpayPaymentId);
        record.setRazorpayOrderId(razorpayOrderId);
        record.setPaymentDate(LocalDate.now());
        record.setStatus(PaymentRecord.STATUS_SUCCESS);
        if (due != null) record.setHostelId(due.getHostelId());
        PaymentRecord saved = recordRepo.save(record);
        if (due != null) refreshDueStatus(due);

        HostellerProfile profile = profileRepo.findById(rpOrder.getHostellerId()).orElse(null);
        if (due != null) publishPaymentReceived(saved, due, profile);
        return saved;
    }

    private void publishPaymentReceived(PaymentRecord saved, PaymentDue due, HostellerProfile profile) {
        if (profile == null) return;
        var authUser = authUserRepo.findById(profile.getAuthUserId()).orElse(null);
        String email = authUser != null ? authUser.getEmail() : "";
        eventPublisher.publishEvent(new PaymentReceivedEvent(
                this, saved, email, profile.getFirstName(),
                due.getPeriodMonth(), due.getPeriodYear()));
    }

    // ─── Monthly Due Generation ──────────────────────────────────────

    /** Due date for a month using the hostel's configured due day. */
    public LocalDate rentDueDate(Long hostelId, int month, int year) {
        int day = settingsService.getOrDefault(hostelId).getDueDay();
        return LocalDate.of(year, month, Math.min(day, 28));
    }

    @Transactional
    public int generateMonthlyDues(Long hostelId, int month, int year) {
        List<BookingCheckin> activeBookings = bookingRepo.findByHostelIdAndStatus(hostelId, "ACTIVE");
        LocalDate dueDate = rentDueDate(hostelId, month, year);
        int count = 0;
        for (BookingCheckin booking : activeBookings) {
            // Skip if this resident already has a RENT due for the period (other charges don't count)
            boolean exists = dueRepo.findRentDue(booking.getHostellerId(), year, month).isPresent();
            if (!exists) {
                PaymentDue due = new PaymentDue();
                due.setHostellerId(booking.getHostellerId());
                due.setBookingId(booking.getId());
                due.setHostelId(hostelId);
                due.setPeriodMonth(month);
                due.setPeriodYear(year);
                due.setDueDate(dueDate);
                due.setRentAmount(booking.getRentAmount());
                due.setDueType(PaymentDue.TYPE_RENT);
                due.setStatus("UNPAID");
                dueRepo.save(due);
                count++;
            }
        }
        return count;
    }

    /** Auto-generate this month's rent dues on the 1st at 6 AM IST, for hostels that keep it switched on. */
    @Scheduled(cron = "0 0 6 1 * *", zone = "Asia/Kolkata")
    public void scheduledDueGeneration() {
        LocalDate today = DateUtils.todayKolkata();
        int total = 0;
        for (Hostel h : hostelRepo.findAll()) {
            try {
                if (!settingsService.getOrDefault(h.getId()).isAutoGenerateDues()) continue;
                total += generateMonthlyDues(h.getId(), today.getMonthValue(), today.getYear());
            } catch (Exception e) {
                log.error("[SCHEDULER] Due generation failed for hostel {}: {}", h.getId(), e.getMessage());
            }
        }
        log.info("[SCHEDULER] Auto due generation for {}/{}: {} due(s) created", today.getMonthValue(), today.getYear(), total);
    }

    // ─── Adjust Due ─────────────────────────────────────────────────

    @Transactional
    public PaymentDue adjustDue(Long dueId, BigDecimal adjustmentAmount, String adjustmentType, String notes) {
        PaymentDue due = getDue(dueId);
        if ("DISCOUNT".equalsIgnoreCase(adjustmentType)) {
            BigDecimal newAmount = due.getRentAmount().subtract(adjustmentAmount);
            due.setRentAmount(newAmount.compareTo(BigDecimal.ZERO) > 0 ? newAmount : BigDecimal.ZERO);
        } else if ("LATE_FEE".equalsIgnoreCase(adjustmentType)) {
            due.setRentAmount(due.getRentAmount().add(adjustmentAmount));
        } else {
            throw new AppException("adjustmentType must be DISCOUNT or LATE_FEE", HttpStatus.BAD_REQUEST);
        }
        PaymentDue saved = dueRepo.save(due);
        refreshDueStatus(saved);
        return saved;
    }

    // ─── Receipt (JSON summary; the PDF lives in ReceiptPdfService) ─────────

    public Map<String, Object> generateReceipt(Long dueId) {
        PaymentDue due = getDue(dueId);
        List<PaymentRecord> records = recordRepo.findByDueId(dueId).stream().filter(PaymentRecord::isSuccessful).toList();
        BigDecimal totalPaid = records.stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = due.getRentAmount().subtract(totalPaid);
        return Map.of(
                "due", due,
                "payments", records,
                "totalPaid", totalPaid,
                "outstanding", outstanding.compareTo(BigDecimal.ZERO) > 0 ? outstanding : BigDecimal.ZERO,
                "status", due.getStatus()
        );
    }

    // ─── Legacy / Other ─────────────────────────────────────────────

    public List<PaymentRazorpayOrder> getOrdersByHosteller(Long hostellerId) {
        return orderRepo.findByHostellerIdOrderByCreatedAtDesc(hostellerId);
    }

    @Transactional
    public PaymentRazorpayOrder createOrder(PaymentRazorpayOrder order) {
        return orderRepo.save(order);
    }

    @Transactional
    public PaymentRazorpayOrder updateOrderStatus(Long id, String status, String razorpayPaymentId) {
        PaymentRazorpayOrder order = orderRepo.findById(id)
                .orElseThrow(() -> new AppException("Order not found", HttpStatus.NOT_FOUND));
        order.setStatus(status);
        if (razorpayPaymentId != null) order.setRazorpayResponseJson(razorpayPaymentId);
        return orderRepo.save(order);
    }

    /** The hostel a resident currently belongs to (from the profile, not a possibly stale token). */
    public Long hostelOfResident(Long authUserId) {
        HostellerProfile p = profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        if (p.getHostelId() == null) throw new AppException("You are not allocated to a hostel yet", HttpStatus.NOT_FOUND);
        return p.getHostelId();
    }

    public Long profileIdOf(Long authUserId) {
        return profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND)).getId();
    }

    public Long hostelOfHosteller(Long hostellerId) {
        return profileRepo.findById(hostellerId)
                .orElseThrow(() -> new AppException("Resident not found", HttpStatus.NOT_FOUND)).getHostelId();
    }

    private static String fullName(HostellerProfile p) {
        if (p == null) return "Resident";
        return (p.getFirstName() + " " + (p.getLastName() != null ? p.getLastName() : "")).trim();
    }
}
