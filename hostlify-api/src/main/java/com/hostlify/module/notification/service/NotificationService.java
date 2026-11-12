package com.hostlify.module.notification.service;

import com.hostlify.module.announcement.event.AnnouncementPublishedEvent;
import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.booking.event.BookingCheckedOutEvent;
import com.hostlify.module.booking.event.BookingCreatedEvent;
import com.hostlify.module.complaint.event.ComplaintResolvedEvent;
import com.hostlify.module.hostel.repository.HostelOwnerRepository;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hostel.repository.StaffAssignmentRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.notification.entity.NotificationLog;
import com.hostlify.module.notification.repository.NotificationLogRepository;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.event.PaymentReceivedEvent;
import com.hostlify.module.payment.event.UpiPaymentClaimEvent;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import com.hostlify.module.payment.service.ReceiptPdfService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

import com.hostlify.module.listing.entity.HostelEnquiry;
import com.hostlify.module.listing.event.EnquiryEvent;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Event-driven notification service.
 * No REST endpoints — listens to Spring ApplicationEvents published by other modules.
 * All listeners are @Async so they don't block the main transaction. Payment listeners run
 * after commit, so the receipt PDF sees the saved payment.
 *
 * Channels (all free): email over SMTP, browser push. SMS is a paid add-on (stubbed in SmsService).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private final EmailService emailService;
    private final SmsService smsService;
    private final PushService pushService;
    private final NotificationLogRepository logRepo;
    private final HostellerProfileRepository profileRepo;
    private final AuthUserRepository authUserRepo;
    private final PaymentDueRepository dueRepo;
    private final ReceiptPdfService receiptPdfService;
    private final StaffAssignmentRepository staffAssignmentRepo;
    private final HostelRepository hostelRepo;
    private final HostelOwnerRepository ownerRepo;

    public NotificationService(EmailService emailService, SmsService smsService, PushService pushService,
                               NotificationLogRepository logRepo, HostellerProfileRepository profileRepo,
                               AuthUserRepository authUserRepo, PaymentDueRepository dueRepo,
                               ReceiptPdfService receiptPdfService, StaffAssignmentRepository staffAssignmentRepo,
                               HostelRepository hostelRepo, HostelOwnerRepository ownerRepo) {
        this.emailService = emailService;
        this.smsService = smsService;
        this.pushService = pushService;
        this.logRepo = logRepo;
        this.profileRepo = profileRepo;
        this.authUserRepo = authUserRepo;
        this.dueRepo = dueRepo;
        this.receiptPdfService = receiptPdfService;
        this.staffAssignmentRepo = staffAssignmentRepo;
        this.hostelRepo = hostelRepo;
        this.ownerRepo = ownerRepo;
    }

    @Async("notificationExecutor")
    @EventListener
    public void onBookingCreated(BookingCreatedEvent event) {
        log.info("Processing BookingCreated notification for {}", event.getHostellerEmail());
        String subject = "Welcome to " + event.getHostelName() + "!";
        String body = String.format("Dear %s, your bed %s in room %s at %s has been allocated. Welcome!",
                event.getHostellerName(), event.getBedName(), event.getRoomName(), event.getHostelName());

        sendAndLog(event.getHostellerEmail(), event.getHostellerMobile(),
                "BOOKING_CREATED", subject, body, event.getBooking().getHostellerId());
    }

    /** Payment confirmed (cash, Razorpay, or verified UPI): email with the PDF receipt attached + push. */
    @Async("notificationExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onPaymentReceived(PaymentReceivedEvent event) {
        PaymentRecord rec = event.getPaymentRecord();
        log.info("Processing PaymentReceived notification for record {}", rec.getId());
        PaymentDue due = rec.getDueId() != null ? dueRepo.findById(rec.getDueId()).orElse(null) : null;
        String what = due != null
                ? ReceiptPdfService.typeLabel(due.getDueType()) + " for " + ReceiptPdfService.periodLabel(due.getPeriodMonth(), due.getPeriodYear())
                : "your dues";
        String subject = "Payment received: Rs. " + rec.getAmount() + " (" + what + ")";
        String body = String.format("Dear %s,%n%nWe have received your payment of Rs. %s towards %s. Thank you!%n%n"
                        + "Receipt no: %s%nYour receipt is attached. You can also download it any time from My Payments in the Hostlify app.",
                event.getHostellerName(), rec.getAmount(), what, ReceiptPdfService.receiptNumber(rec));

        byte[] pdf = null;
        try {
            pdf = receiptPdfService.paymentReceipt(rec.getId());
        } catch (Exception e) {
            log.warn("Could not build receipt PDF for record {}: {}", rec.getId(), e.getMessage());
        }
        Long authUserId = profileRepo.findById(rec.getHostellerId()).map(p -> p.getAuthUserId()).orElse(null);
        notifyUser("HOSTELLER", rec.getHostellerId(), authUserId, event.getHostellerEmail(), "PAYMENT_RECEIVED",
                subject, body, "Payment received", "Rs. " + rec.getAmount() + " for " + what + ". Receipt ready.",
                "/app/dues", pdf != null ? "Receipt-" + ReceiptPdfService.receiptNumber(rec) + ".pdf" : null, pdf);
    }

    /** UPI claim submitted → tell finance staff to verify; rejected → tell the resident. */
    @Async("notificationExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onUpiClaim(UpiPaymentClaimEvent event) {
        PaymentRecord rec = event.getRecord();
        if (event.getKind() == UpiPaymentClaimEvent.Kind.SUBMITTED) {
            String title = "UPI payment to verify";
            String body = event.getHostellerName() + " says they paid Rs. " + rec.getAmount()
                    + " by UPI (UTR " + rec.getTransactionRef() + "). Check your UPI app and confirm.";
            for (Long staffUserId : financeStaff(rec.getHostelId())) {
                pushService.sendToUser(staffUserId, title, body, "/admin/payments");
            }
            logOnly("HOSTEL", rec.getHostelId(), "UPI_CLAIM_SUBMITTED", title, body);
        } else {
            var profile = profileRepo.findById(rec.getHostellerId()).orElse(null);
            if (profile == null) return;
            String email = authUserRepo.findById(profile.getAuthUserId()).map(u -> u.getEmail()).orElse(null);
            String subject = "We couldn't confirm your UPI payment of Rs. " + rec.getAmount();
            String body = String.format("Dear %s,%n%nThe hostel could not find your UPI payment of Rs. %s (UTR %s)%s.%n"
                            + "Please check the UTR in your UPI app and submit it again, or contact the hostel office.",
                    profile.getFirstName(), rec.getAmount(), rec.getTransactionRef(),
                    event.getReason() != null && !event.getReason().isBlank() ? " - reason: " + event.getReason() : "");
            notifyUser("HOSTELLER", profile.getId(), profile.getAuthUserId(), email, "UPI_CLAIM_REJECTED",
                    subject, body, "UPI payment not confirmed", "Rs. " + rec.getAmount() + " (UTR " + rec.getTransactionRef() + ")",
                    "/app/dues", null, null);
        }
    }

    /** Public enquiries: alert staff instantly, confirm to the visitor, nudge staff about the waitlist. */
    @Async("notificationExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onEnquiry(EnquiryEvent event) {
        HostelEnquiry e = event.getEnquiry();
        String slot = e.getVisitSlot() == null ? "" : " (" + e.getVisitSlot().toLowerCase() + ")";
        switch (event.getKind()) {
            case CREATED -> {
                String what = "WAITLIST".equals(e.getStatus()) ? "Waitlist request"
                        : e.getVisitDate() != null ? "Visit request for " + e.getVisitDate().format(DMY) + slot
                        : "New enquiry";
                String title = what + " - " + e.getName();
                String body = e.getName() + " (" + e.getMobile() + ")"
                        + (e.getRoomType() != null ? ", wants " + e.getRoomType() : "")
                        + (e.getMoveInDate() != null ? ", moving in " + e.getMoveInDate().format(DMY) : "")
                        + (e.getMessage() != null ? ": \"" + e.getMessage() + "\"" : "");
                for (Long staffUserId : hostelStaff(e.getHostelId(), true)) {
                    pushService.sendToUser(staffUserId, title, body, "/admin/enquiries");
                }
                ownerEmail(e.getHostelId()).ifPresent(email -> safeEmail(email, title + " at " + event.getHostelName(),
                        "You have a new lead from your Hostlify listing.\n\n" + body
                                + "\n\nCall or WhatsApp them soon. Quick replies win most bookings.\nOpen the Enquiries inbox: " + frontendUrl + "/admin/enquiries"));
                logOnly("HOSTEL", e.getHostelId(), "ENQUIRY_CREATED", title, body);
                if (e.getEmail() != null) {
                    safeEmail(e.getEmail(), "We received your request - " + event.getHostelName(),
                            "Hi " + e.getName() + ",\n\nThanks for your interest in " + event.getHostelName() + ". "
                                    + ("WAITLIST".equals(e.getStatus()) ? "You're on the waitlist; we'll contact you as soon as a bed is free."
                                    : e.getVisitDate() != null ? "We'll call you on " + e.getMobile() + " to confirm your visit on " + e.getVisitDate().format(DMY) + slot + "."
                                    : "We'll contact you on " + e.getMobile() + " shortly.")
                                    + "\n\n- " + event.getHostelName());
                }
            }
            case VISIT_CONFIRMED -> {
                if (e.getEmail() != null && e.getVisitDate() != null) {
                    safeEmail(e.getEmail(), "Your visit to " + event.getHostelName() + " is confirmed",
                            "Hi " + e.getName() + ",\n\nYour visit to " + event.getHostelName() + " is confirmed for "
                                    + e.getVisitDate().format(DMY) + slot + ".\n\nSee you there!\n- " + event.getHostelName());
                }
            }
            case WAITLIST_NUDGE -> {
                String title = event.getWaitingCount() + " on the waitlist, " + event.getAvailableBeds() + " bed(s) free";
                String body = "People are waiting for a bed at " + event.getHostelName() + ". Contact them before the beds go.";
                for (Long staffUserId : hostelStaff(e.getHostelId(), true)) {
                    pushService.sendToUser(staffUserId, title, body, "/admin/enquiries?status=WAITLIST");
                }
                ownerEmail(e.getHostelId()).ifPresent(email -> safeEmail(email, title, body + "\n\n" + frontendUrl + "/admin/enquiries?status=WAITLIST"));
                logOnly("HOSTEL", e.getHostelId(), "WAITLIST_NUDGE", title, body);
            }
        }
    }

    /** Agreement to sign, checklist to confirm, deposit settlement ready, … */
    @Async("notificationExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void onResidentNotice(com.hostlify.module.compliance.ResidentNoticeEvent event) {
        var profile = profileRepo.findById(event.getHostellerId()).orElse(null);
        if (profile == null) return;
        String email = authUserRepo.findById(profile.getAuthUserId()).map(u -> u.getEmail()).orElse(profile.getEmail());
        String body = "Dear " + profile.getFirstName() + ",\n\n" + event.getBody() + "\n\nOpen Hostlify: " + frontendUrl + event.getUrl();
        notifyUser("HOSTELLER", profile.getId(), profile.getAuthUserId(), email, event.getTemplate(),
                event.getSubject(), body, event.getSubject(), event.getBody(), event.getUrl(), null, null);
    }

    @Async("notificationExecutor")
    @EventListener
    public void onBookingCheckedOut(BookingCheckedOutEvent event) {
        log.info("Processing CheckedOut notification for {}", event.getHostellerEmail());
        String subject = "Checkout Confirmed";
        String body = String.format("Dear %s, your checkout has been confirmed. " +
                        "Advance refund of ₹%s will be processed shortly.",
                event.getHostellerName(), event.getAdvanceRefundAmount());

        sendAndLog(event.getHostellerEmail(), null,
                "BOOKING_CHECKOUT", subject, body, event.getBooking().getHostellerId());
    }

    @Async("notificationExecutor")
    @EventListener
    public void onComplaintResolved(ComplaintResolvedEvent event) {
        log.info("Processing ComplaintResolved notification for {}", event.getHostellerEmail());
        String subject = "Your complaint has been resolved";
        String body = String.format("Dear %s, your complaint (ID: %d) has been resolved. Thank you for your patience.",
                event.getHostellerName(), event.getComplaint().getId());
        sendAndLog(event.getHostellerEmail(), null,
                "COMPLAINT_RESOLVED", subject, body, event.getComplaint().getHostellerId());
    }

    @Async("notificationExecutor")
    @EventListener
    public void onAnnouncementPublished(AnnouncementPublishedEvent event) {
        log.info("Processing AnnouncementPublished for hostel {}", event.getAnnouncement().getHostelId());
        String subject = "New Announcement: " + event.getAnnouncement().getTitle();
        String body = event.getAnnouncement().getBody() != null
                ? event.getAnnouncement().getBody() : subject;
        // Log a single HOSTEL-level notification record (fan-out per hosteller is a separate job)
        logOnly("HOSTEL", event.getAnnouncement().getHostelId(), "ANNOUNCEMENT_PUBLISHED", subject, body);
    }

    // ─── shared helpers ────────────────────────────────────────────────────

    /**
     * Email (optionally with a PDF) + push to every device of the user, one log row per channel used.
     * Never throws: a failed channel is logged as FAILED.
     * @return true when at least one channel delivered (or would deliver, when email is skipped in dev)
     */
    public boolean notifyUser(String recipientType, Long recipientId, Long authUserId, String email, String template,
                              String subject, String emailBody, String pushTitle, String pushBody, String url,
                              String attachmentName, byte[] attachment) {
        boolean any = false;
        if (email != null && !email.isBlank()) {
            NotificationLog l = baseLog(recipientType, recipientId, template, subject, emailBody);
            l.setChannel("EMAIL");
            l.setToEmail(email);
            try {
                emailService.sendEmail(email, subject, emailBody, attachmentName, attachment);
                l.setStatus(emailService.isEnabled() ? "SENT" : "SKIPPED");
                any = true;
            } catch (Exception e) {
                log.error("Email notification {} failed: {}", template, e.getMessage());
                l.setStatus("FAILED");
                l.setErrorMessage(e.getMessage());
            }
            logRepo.save(l);
        }
        if (authUserId != null && pushService.isEnabled()) {
            int n = pushService.sendToUser(authUserId, pushTitle, pushBody, url);
            if (n > 0) {
                NotificationLog l = baseLog(recipientType, recipientId, template, pushTitle, pushBody);
                l.setChannel("PUSH");
                l.setStatus("SENT");
                logRepo.save(l);
                any = true;
            }
        }
        return any;
    }

    /** Active staff of the hostel who handle money (no supervisors), plus the hostel owner. */
    private Set<Long> financeStaff(Long hostelId) {
        return hostelStaff(hostelId, false);
    }

    /** Active staff of the hostel plus the owner; supervisors only when {@code includeSupervisors}. */
    private Set<Long> hostelStaff(Long hostelId, boolean includeSupervisors) {
        Set<Long> ids = new LinkedHashSet<>();
        staffAssignmentRepo.findByHostelIdAndIsActiveTrue(hostelId).forEach(a -> ids.add(a.getStaffUserId()));
        hostelRepo.findById(hostelId)
                .flatMap(h -> ownerRepo.findById(h.getOwnerId()))
                .ifPresent(o -> { if (o.getAuthUserId() != null) ids.add(o.getAuthUserId()); });
        ids.removeIf(id -> authUserRepo.findById(id)
                .map(u -> !includeSupervisors && "SUPERVISOR".equals(u.getSubRole()))
                .orElse(true));
        return ids;
    }

    private java.util.Optional<String> ownerEmail(Long hostelId) {
        return hostelRepo.findById(hostelId)
                .flatMap(h -> ownerRepo.findById(h.getOwnerId()))
                .flatMap(o -> o.getAuthUserId() == null ? java.util.Optional.empty() : authUserRepo.findById(o.getAuthUserId()))
                .map(u -> u.getEmail())
                .filter(em -> em != null && !em.isBlank());
    }

    private void safeEmail(String to, String subject, String body) {
        try {
            emailService.sendEmail(to, subject, body);
        } catch (Exception ex) {
            log.error("Email '{}' to {} failed: {}", subject, to, ex.getMessage());
        }
    }

    private NotificationLog baseLog(String recipientType, Long recipientId, String template, String subject, String body) {
        NotificationLog l = new NotificationLog();
        l.setRecipientType(recipientType);
        l.setRecipientId(recipientId);
        l.setTemplateName(template);
        l.setSubject(subject != null && subject.length() > 300 ? subject.substring(0, 300) : subject);
        l.setBodyPreview(body == null ? null : body.substring(0, Math.min(body.length(), 500)));
        l.setSentAt(LocalDateTime.now());
        return l;
    }

    private void logOnly(String recipientType, Long recipientId, String template, String subject, String body) {
        NotificationLog l = baseLog(recipientType, recipientId, template, subject, body);
        l.setChannel("LOG");
        l.setStatus("SENT");
        logRepo.save(l);
    }

    private void sendAndLog(String email, String mobile, String template,
                            String subject, String body, Long recipientId) {
        Long authUserId = recipientId == null ? null
                : profileRepo.findById(recipientId).map(p -> p.getAuthUserId()).orElse(null);
        notifyUser("HOSTELLER", recipientId, authUserId, email, template, subject, body, subject, body, "/app", null, null);
        if (mobile != null && !mobile.isBlank()) {
            try {
                smsService.sendSms(mobile, body);
            } catch (Exception e) {
                log.error("SMS failed: {}", e.getMessage());
            }
        }
    }
}
