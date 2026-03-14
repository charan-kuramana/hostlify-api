package com.hostlify.module.notification.service;

import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Scheduled notification jobs.
 * Sends monthly due reminders on the 15th of each month at 9 AM IST.
 */
@Service
public class ScheduledNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledNotificationService.class);

    private final PaymentDueRepository dueRepo;
    private final HostellerProfileRepository profileRepo;
    private final AuthUserRepository authUserRepo;
    private final EmailService emailService;
    private final SmsService smsService;

    public ScheduledNotificationService(PaymentDueRepository dueRepo,
                                         HostellerProfileRepository profileRepo,
                                         AuthUserRepository authUserRepo,
                                         EmailService emailService,
                                         SmsService smsService) {
        this.dueRepo = dueRepo;
        this.profileRepo = profileRepo;
        this.authUserRepo = authUserRepo;
        this.emailService = emailService;
        this.smsService = smsService;
    }

    /**
     * Send due reminders on the 15th of every month at 9 AM IST.
     * Targets all UNPAID dues with dueDate before today.
     */
    @Scheduled(cron = "0 0 9 15 * *", zone = "Asia/Kolkata")
    public void sendMonthlyDueReminders() {
        int sent = triggerDueRemindersNow();
        log.info("[SCHEDULER] Completed monthly due reminders — {} sent", sent);
    }

    /**
     * Manual trigger: same logic as scheduled. Returns count of dues processed.
     */
    public int triggerDueRemindersNow() {
        log.info("[NOTIFICATION] Sending due reminders...");
        LocalDate today = LocalDate.now();
        var overdueDues = dueRepo.findByStatusAndDueDateBefore("UNPAID", today);
        int sent = 0;
        for (var due : overdueDues) {
            final var currentDue = due;
            try {
                profileRepo.findById(currentDue.getHostellerId()).ifPresent(profile -> {
                    authUserRepo.findById(profile.getAuthUserId()).ifPresent(authUser -> {
                        String subject = "Rent Due Reminder — "
                                + currentDue.getPeriodMonth() + "/" + currentDue.getPeriodYear();
                        String body = String.format(
                                "Dear %s, your rent of ₹%s for %d/%d is overdue. Please pay at the earliest to avoid penalties.",
                                profile.getFirstName(), currentDue.getRentAmount(),
                                currentDue.getPeriodMonth(), currentDue.getPeriodYear());
                        try {
                            emailService.sendEmail(authUser.getEmail(), subject, body);
                            if (authUser.getMobile() != null && !authUser.getMobile().isBlank()) {
                                smsService.sendSms(authUser.getMobile(), body);
                            }
                        } catch (Exception e) {
                            log.error("Failed to send reminder for due {}: {}", currentDue.getId(), e.getMessage());
                        }
                    });
                });
                sent++;
            } catch (Exception e) {
                log.error("Error processing due reminder for due {}: {}", currentDue.getId(), e.getMessage());
            }
        }
        return sent;
    }
}
