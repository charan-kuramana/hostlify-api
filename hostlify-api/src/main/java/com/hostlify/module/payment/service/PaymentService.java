package com.hostlify.module.payment.service;

import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRazorpayOrder;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.event.PaymentReceivedEvent;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import com.hostlify.module.payment.repository.PaymentRazorpayOrderRepository;
import com.hostlify.module.payment.repository.PaymentRecordRepository;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class PaymentService {

    private final PaymentDueRepository dueRepo;
    private final PaymentRazorpayOrderRepository orderRepo;
    private final PaymentRecordRepository recordRepo;
    private final BookingCheckinRepository bookingRepo;
    private final HostellerProfileRepository profileRepo;
    private final AuthUserRepository authUserRepo;
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
                          ApplicationEventPublisher eventPublisher) {
        this.dueRepo = dueRepo;
        this.orderRepo = orderRepo;
        this.recordRepo = recordRepo;
        this.bookingRepo = bookingRepo;
        this.profileRepo = profileRepo;
        this.authUserRepo = authUserRepo;
        this.eventPublisher = eventPublisher;
    }

    // ─── Due Queries ───────────────────────────────────────────────

    public List<PaymentDue> getDuesByHosteller(Long hostellerId) {
        return dueRepo.findByHostellerIdOrderByDueDateDesc(hostellerId);
    }

    public PaymentDue getCurrentDue(Long authUserId) {
        HostellerProfile profile = profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        LocalDate today = LocalDate.now();
        return dueRepo.findByHostellerIdAndPeriodYearAndPeriodMonth(
                        profile.getId(), today.getYear(), today.getMonthValue())
                .orElseThrow(() -> new AppException("No due for current month", HttpStatus.NOT_FOUND));
    }

    public List<PaymentDue> getPendingDuesByHostel(Long hostelId) {
        return dueRepo.findByHostelIdAndStatus(hostelId, "UNPAID");
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

    // ─── Razorpay Order ───────────────────────────────────────────

    @Transactional
    public PaymentRazorpayOrder initiateRazorpayPayment(Long authUserId, Long dueId) {
        HostellerProfile profile = profileRepo.findByAuthUserId(authUserId)
                .orElseThrow(() -> new AppException("Profile not found", HttpStatus.NOT_FOUND));
        PaymentDue due = getDue(dueId);

        if ("PAID".equals(due.getStatus())) {
            throw new AppException("Due is already paid", HttpStatus.CONFLICT);
        }

        long amountPaise = due.getRentAmount().multiply(BigDecimal.valueOf(100)).longValue();

        String razorpayOrderId;
        try {
            com.razorpay.RazorpayClient client = new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);
            org.json.JSONObject params = new org.json.JSONObject();
            params.put("amount", amountPaise);
            params.put("currency", "INR");
            params.put("receipt", "HOST-" + dueId);
            com.razorpay.Order order = client.orders.create(params);
            razorpayOrderId = order.get("id");
        } catch (Exception e) {
            throw new AppException("Failed to create Razorpay order: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        PaymentRazorpayOrder order = new PaymentRazorpayOrder();
        order.setHostellerId(profile.getId());
        order.setDueId(dueId);
        order.setRazorpayOrderId(razorpayOrderId);
        order.setAmountPaise(amountPaise);
        order.setStatus("CREATED");
        order.setExpiresAt(java.time.LocalDateTime.now().plusHours(1));
        return orderRepo.save(order);
    }

    // ─── Cash Payment ──────────────────────────────────────────────

    @Transactional
    public PaymentRecord recordCashPayment(Long staffUserId, Long hostellerId,
                                           Long dueId, BigDecimal amount,
                                           String paymentMethod, String transactionRef,
                                           LocalDate paymentDate, String notes) {
        PaymentDue due = getDue(dueId);
        HostellerProfile profile = profileRepo.findById(hostellerId)
                .orElseThrow(() -> new AppException("Hosteller not found", HttpStatus.NOT_FOUND));

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
        record.setStatus("SUCCESS");
        PaymentRecord saved = recordRepo.save(record);

        // Update due status
        if (amount.compareTo(due.getRentAmount()) >= 0) {
            due.setStatus("PAID");
        } else {
            due.setStatus("PARTIAL");
        }
        dueRepo.save(due);

        // Publish event for notification
        var authUser = authUserRepo.findById(profile.getAuthUserId()).orElse(null);
        String email = authUser != null ? authUser.getEmail() : "";
        eventPublisher.publishEvent(new PaymentReceivedEvent(
                this, saved, email,
                profile.getFirstName(),
                due.getPeriodMonth(),
                due.getPeriodYear()
        ));

        return saved;
    }

    // ─── Webhook Handler Support ────────────────────────────────────

    @Transactional
    public void handleRazorpayPaymentCaptured(String razorpayPaymentId,
                                               String razorpayOrderId,
                                               long amountPaise) {
        // Idempotency: skip if already recorded
        if (recordRepo.findByRazorpayPaymentId(razorpayPaymentId).isPresent()) {
            return;
        }

        PaymentRazorpayOrder rpOrder = orderRepo.findByRazorpayOrderId(razorpayOrderId)
                .orElse(null);
        if (rpOrder == null) return; // Unknown order — log and skip

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
        record.setStatus("SUCCESS");
        if (due != null) {
            record.setHostelId(due.getHostelId());
            due.setStatus("PAID");
            dueRepo.save(due);
        }
        recordRepo.save(record);

        // Publish event
        HostellerProfile profile = profileRepo.findById(rpOrder.getHostellerId()).orElse(null);
        if (profile != null && due != null) {
            var authUser = authUserRepo.findById(profile.getAuthUserId()).orElse(null);
            String email = authUser != null ? authUser.getEmail() : "";
            eventPublisher.publishEvent(new PaymentReceivedEvent(
                    this, record, email, profile.getFirstName(),
                    due.getPeriodMonth(), due.getPeriodYear()
            ));
        }
    }

    // ─── Monthly Due Generation ─────────────────────────────────────

    @Transactional
    public int generateMonthlyDues(Long hostelId, int month, int year) {
        List<BookingCheckin> activeBookings = bookingRepo.findByHostelIdAndStatus(hostelId, "ACTIVE");
        int count = 0;
        for (BookingCheckin booking : activeBookings) {
            // Skip if due already exists for this period
            boolean alreadyExists = !dueRepo.findByHostelIdAndPeriodYearAndPeriodMonth(
                    hostelId, year, month).isEmpty();
            // More precise: check per hosteller
            boolean exists = dueRepo.findByHostellerIdAndPeriodYearAndPeriodMonth(
                    booking.getHostellerId(), year, month).isPresent();
            if (!exists) {
                PaymentDue due = new PaymentDue();
                due.setHostellerId(booking.getHostellerId());
                due.setBookingId(booking.getId());
                due.setHostelId(hostelId);
                due.setPeriodMonth(month);
                due.setPeriodYear(year);
                due.setDueDate(DateUtils.dueDate(month, year));
                due.setRentAmount(booking.getRentAmount());
                due.setStatus("UNPAID");
                dueRepo.save(due);
                count++;
            }
        }
        return count;
    }

    /** Auto-generate dues on 1st of each month at 9am IST */
    @Scheduled(cron = "0 0 9 1 * *", zone = "Asia/Kolkata")
    public void scheduledDueGeneration() {
        LocalDate today = LocalDate.now();
        // This would normally iterate all active hostels — simplified here
        System.out.println("[SCHEDULER] Auto due generation triggered for " +
                today.getMonthValue() + "/" + today.getYear());
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
        return dueRepo.save(due);
    }

    // ─── Receipt ─────────────────────────────────────────────────────

    public java.util.Map<String, Object> generateReceipt(Long dueId) {
        PaymentDue due = getDue(dueId);
        List<PaymentRecord> records = recordRepo.findByDueId(dueId);
        BigDecimal totalPaid = records.stream()
                .map(PaymentRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = due.getRentAmount().subtract(totalPaid);
        return java.util.Map.of(
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
}
