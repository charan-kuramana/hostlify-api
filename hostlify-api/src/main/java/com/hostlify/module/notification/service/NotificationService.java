package com.hostlify.module.notification.service;

import com.hostlify.module.announcement.event.AnnouncementPublishedEvent;
import com.hostlify.module.booking.event.BookingCheckedOutEvent;
import com.hostlify.module.booking.event.BookingCreatedEvent;
import com.hostlify.module.complaint.event.ComplaintResolvedEvent;
import com.hostlify.module.notification.entity.NotificationLog;
import com.hostlify.module.notification.repository.NotificationLogRepository;
import com.hostlify.module.payment.event.PaymentReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Event-driven notification service.
 * No REST endpoints — listens to Spring ApplicationEvents published by other modules.
 * All listeners are @Async so they don't block the main transaction.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final EmailService emailService;
    private final SmsService smsService;
    private final NotificationLogRepository logRepo;

    public NotificationService(EmailService emailService, SmsService smsService,
                                NotificationLogRepository logRepo) {
        this.emailService = emailService;
        this.smsService = smsService;
        this.logRepo = logRepo;
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

    @Async("notificationExecutor")
    @EventListener
    public void onPaymentReceived(PaymentReceivedEvent event) {
        log.info("Processing PaymentReceived notification for {}", event.getHostellerEmail());
        String subject = "Payment Received — " + event.getPeriodMonth() + "/" + event.getPeriodYear();
        String body = String.format("Dear %s, payment of ₹%s for %d/%d has been received. Thank you!",
                event.getHostellerName(),
                event.getPaymentRecord().getAmount(),
                event.getPeriodMonth(), event.getPeriodYear());

        sendAndLog(event.getHostellerEmail(), null,
                "PAYMENT_RECEIVED", subject, body, event.getPaymentRecord().getHostellerId());
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
        NotificationLog notifLog = new NotificationLog();
        notifLog.setRecipientType("HOSTEL");
        notifLog.setRecipientId(event.getAnnouncement().getHostelId());
        notifLog.setTemplateName("ANNOUNCEMENT_PUBLISHED");
        notifLog.setSubject(subject);
        notifLog.setBodyPreview(body.substring(0, Math.min(body.length(), 500)));
        notifLog.setChannel("LOG");
        notifLog.setStatus("SENT");
        notifLog.setSentAt(LocalDateTime.now());
        logRepo.save(notifLog);
    }

    private void sendAndLog(String email, String mobile, String template,
                             String subject, String body, Long recipientId) {
        NotificationLog notifLog = new NotificationLog();
        notifLog.setRecipientType("HOSTELLER");
        notifLog.setRecipientId(recipientId);
        notifLog.setTemplateName(template);
        notifLog.setSubject(subject);
        notifLog.setBodyPreview(body.substring(0, Math.min(body.length(), 500)));
        notifLog.setToEmail(email);
        notifLog.setSentAt(LocalDateTime.now());

        try {
            if (email != null && !email.isBlank()) {
                notifLog.setChannel("EMAIL");
                emailService.sendEmail(email, subject, body);
                notifLog.setStatus("SENT");
            }
            if (mobile != null && !mobile.isBlank()) {
                smsService.sendSms(mobile, body);
            }
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage());
            notifLog.setStatus("FAILED");
            notifLog.setErrorMessage(e.getMessage());
        }

        logRepo.save(notifLog);
    }
}
