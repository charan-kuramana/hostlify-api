package com.hostlify.module.notification.service;

import com.hostlify.module.auth.repository.AuthUserRepository;
import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.module.hostel.repository.HostelRepository;
import com.hostlify.module.hosteller.entity.HostellerProfile;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.module.payment.entity.HostelPaymentSetting;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.entity.PaymentRecord;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import com.hostlify.module.payment.repository.PaymentRecordRepository;
import com.hostlify.module.payment.service.PaymentSettingsService;
import com.hostlify.module.payment.service.ReceiptPdfService;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Daily payment automation (9 AM IST):
 *  1. Late fees — only for hostels whose owner switched the late fee ON, only on RENT dues that fell due
 *     after it was switched on, and only once the grace period has passed.
 *  2. Reminders — N days before the due date, on the due date, and every N days while overdue
 *     (all per-hostel settings). One message per resident listing all their open dues.
 * Channels: email + browser push (both free). Staff also get WhatsApp one-tap links in the UI.
 */
@Service
public class ScheduledNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledNotificationService.class);
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final PaymentDueRepository dueRepo;
    private final PaymentRecordRepository recordRepo;
    private final HostellerProfileRepository profileRepo;
    private final AuthUserRepository authUserRepo;
    private final HostelRepository hostelRepo;
    private final PaymentSettingsService settingsService;
    private final NotificationService notificationService;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public ScheduledNotificationService(PaymentDueRepository dueRepo, PaymentRecordRepository recordRepo,
                                        HostellerProfileRepository profileRepo, AuthUserRepository authUserRepo,
                                        HostelRepository hostelRepo, PaymentSettingsService settingsService,
                                        NotificationService notificationService) {
        this.dueRepo = dueRepo;
        this.recordRepo = recordRepo;
        this.profileRepo = profileRepo;
        this.authUserRepo = authUserRepo;
        this.hostelRepo = hostelRepo;
        this.settingsService = settingsService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void dailyPaymentJob() {
        LocalDate today = DateUtils.todayKolkata();
        int fees = applyLateFees(today);
        int reminders = sendDueReminders(today);
        log.info("[SCHEDULER] Daily payment job: {} late fee(s) applied/updated, {} reminder(s) sent", fees, reminders);
    }

    /** Kept for callers of the old API: runs today's reminders immediately. */
    public int triggerDueRemindersNow() {
        return sendDueReminders(DateUtils.todayKolkata());
    }

    // ─── 1. Late fees ──────────────────────────────────────────────────────

    public int applyLateFees(LocalDate today) {
        Map<Long, HostelPaymentSetting> settings = new HashMap<>();
        int changed = 0;
        for (PaymentDue rent : dueRepo.findOpenRentDuesDueBefore(today)) {
            try {
                HostelPaymentSetting s = settings.computeIfAbsent(rent.getHostelId(), settingsService::getOrDefault);
                BigDecimal fee = lateFeeFor(rent, s, today);
                if (fee == null) continue;

                long daysLate = ChronoUnit.DAYS.between(rent.getDueDate(), today);
                String desc = "Late fee: " + ReceiptPdfService.periodLabel(rent.getPeriodMonth(), rent.getPeriodYear())
                        + " rent, " + daysLate + " day" + (daysLate == 1 ? "" : "s") + " late";

                Optional<PaymentDue> existing = dueRepo.findFirstByParentDueIdAndDueType(rent.getId(), PaymentDue.TYPE_LATE_FEE);
                if (existing.isEmpty()) {
                    if (dueRepo.lateFeeEverCharged(rent.getId())) continue; // waived by staff: never re-charge
                    PaymentDue lf = new PaymentDue();
                    lf.setHostellerId(rent.getHostellerId());
                    lf.setBookingId(rent.getBookingId());
                    lf.setHostelId(rent.getHostelId());
                    lf.setPeriodMonth(rent.getPeriodMonth());
                    lf.setPeriodYear(rent.getPeriodYear());
                    lf.setDueDate(today);
                    lf.setRentAmount(fee);
                    lf.setDueType(PaymentDue.TYPE_LATE_FEE);
                    lf.setParentDueId(rent.getId());
                    lf.setDescription(desc);
                    lf.setStatus("UNPAID");
                    dueRepo.save(lf);
                    changed++;
                    notifyLateFee(rent, fee, s);
                } else if ("PER_DAY".equals(s.getLateFeeType())) {
                    PaymentDue lf = existing.get();
                    // Grow the open per-day fee; never shrink it below what was already charged
                    if (lf.isOpen() && fee.compareTo(lf.getRentAmount()) > 0) {
                        lf.setRentAmount(fee);
                        lf.setDescription(desc);
                        lf.setStatus(paidOn(lf).signum() > 0 ? "PARTIAL" : "UNPAID");
                        dueRepo.save(lf);
                        changed++;
                    }
                }
            } catch (Exception e) {
                log.error("Late fee failed for due {}: {}", rent.getId(), e.getMessage());
            }
        }
        return changed;
    }

    /** Fee owed today for this rent due, or null when none applies. */
    static BigDecimal lateFeeFor(PaymentDue rent, HostelPaymentSetting s, LocalDate today) {
        if (!s.isLateFeeEnabled() || s.getLateFeeAmount() == null || s.getLateFeeAmount().signum() <= 0) return null;
        if (s.getLateFeeEnabledOn() != null && rent.getDueDate().isBefore(s.getLateFeeEnabledOn())) return null;
        long daysLate = ChronoUnit.DAYS.between(rent.getDueDate(), today);
        long chargeable = daysLate - s.getLateFeeGraceDays();
        if (chargeable <= 0) return null;
        BigDecimal fee = "PER_DAY".equals(s.getLateFeeType())
                ? s.getLateFeeAmount().multiply(BigDecimal.valueOf(chargeable))
                : s.getLateFeeAmount();
        if (s.getLateFeeMaxAmount() != null && s.getLateFeeMaxAmount().signum() > 0 && fee.compareTo(s.getLateFeeMaxAmount()) > 0) {
            fee = s.getLateFeeMaxAmount();
        }
        return fee.setScale(2, RoundingMode.HALF_UP);
    }

    private void notifyLateFee(PaymentDue rent, BigDecimal fee, HostelPaymentSetting s) {
        HostellerProfile p = profileRepo.findById(rent.getHostellerId()).orElse(null);
        if (p == null) return;
        String email = email(p);
        String period = ReceiptPdfService.periodLabel(rent.getPeriodMonth(), rent.getPeriodYear());
        String subject = "Late fee added: Rs. " + fee + " (" + period + " rent)";
        String body = "Dear " + p.getFirstName() + ",\n\n"
                + "Your rent for " + period + " (due " + rent.getDueDate().format(D) + ") is still unpaid, so a late fee of Rs. "
                + fee + " has been added as per the hostel's policy"
                + ("PER_DAY".equals(s.getLateFeeType()) ? " (it grows each day until the rent is paid" + (s.getLateFeeMaxAmount() != null ? ", up to Rs. " + s.getLateFeeMaxAmount() : "") + ")" : "")
                + ".\n\nPay now: " + frontendUrl + "/app/dues\n\nIf you think this is a mistake, please contact the hostel office.";
        notificationService.notifyUser("HOSTELLER", p.getId(), p.getAuthUserId(), email, "LATE_FEE_APPLIED",
                subject, body, "Late fee added", "Rs. " + fee + " for " + period + " rent. Tap to pay.", "/app/dues", null, null);
    }

    // ─── 2. Reminders ──────────────────────────────────────────────────────

    public int sendDueReminders(LocalDate today) {
        Map<Long, HostelPaymentSetting> settings = new HashMap<>();
        // Group by resident: one message per person per day
        Map<Long, List<PaymentDue>> byResident = new LinkedHashMap<>();
        Map<Long, String> kindByResident = new HashMap<>();
        for (PaymentDue d : dueRepo.findByStatusIn(List.of("UNPAID", "PARTIAL", "OVERDUE"))) {
            if (today.equals(d.getLastReminderOn())) continue;
            HostelPaymentSetting s = settings.computeIfAbsent(d.getHostelId(), settingsService::getOrDefault);
            String kind = reminderKind(d.getDueDate(), today, s);
            if (kind == null) continue;
            if (outstanding(d).signum() <= 0) continue; // fully covered by a UPI payment awaiting confirmation
            byResident.computeIfAbsent(d.getHostellerId(), k -> new ArrayList<>()).add(d);
            // Most urgent wording wins: OVERDUE > DUE_TODAY > UPCOMING
            kindByResident.merge(d.getHostellerId(), kind, (a, b) -> rank(a) >= rank(b) ? a : b);
        }
        int sent = 0;
        for (var e : byResident.entrySet()) {
            try {
                if (remind(e.getKey(), e.getValue(), kindByResident.get(e.getKey()), today, settings)) sent++;
            } catch (Exception ex) {
                log.error("Reminder failed for resident {}: {}", e.getKey(), ex.getMessage());
            }
        }
        return sent;
    }

    /**
     * Staff "Send reminder" button: reminds the resident about all their open dues right now,
     * regardless of schedule. Throttled to once per resident per day.
     */
    public Map<String, Object> sendReminderNow(Long dueId) {
        PaymentDue due = dueRepo.findById(dueId).orElseThrow(() -> new AppException("Due not found", HttpStatus.NOT_FOUND));
        if (!due.isOpen()) throw new AppException("This due is already paid", HttpStatus.CONFLICT);
        LocalDate today = DateUtils.todayKolkata();
        if (today.equals(due.getLastReminderOn())) {
            throw new AppException("A reminder was already sent today for this due", HttpStatus.CONFLICT);
        }
        List<PaymentDue> open = dueRepo.findByHostellerIdOrderByDueDateDesc(due.getHostellerId()).stream()
                .filter(PaymentDue::isOpen).filter(d -> outstanding(d).signum() > 0).toList();
        if (open.isEmpty()) throw new AppException("Nothing outstanding for this resident", HttpStatus.CONFLICT);
        String kind = open.stream().map(d -> reminderKindAny(d.getDueDate(), today)).max(Comparator.comparingInt(ScheduledNotificationService::rank)).orElse("UPCOMING");
        boolean delivered = remind(due.getHostellerId(), open, kind, today, new HashMap<>());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("delivered", delivered);
        res.put("dues", open.size());
        return res;
    }

    static String reminderKind(LocalDate dueDate, LocalDate today, HostelPaymentSetting s) {
        long until = ChronoUnit.DAYS.between(today, dueDate);
        if (until > 0) return s.getReminderDaysBefore() > 0 && until == s.getReminderDaysBefore() ? "UPCOMING" : null;
        if (until == 0) return s.isRemindOnDueDate() ? "DUE_TODAY" : null;
        int every = s.getOverdueReminderIntervalDays();
        return every > 0 && (-until) % every == 0 ? "OVERDUE" : null;
    }

    private static String reminderKindAny(LocalDate dueDate, LocalDate today) {
        long until = ChronoUnit.DAYS.between(today, dueDate);
        return until > 0 ? "UPCOMING" : until == 0 ? "DUE_TODAY" : "OVERDUE";
    }

    private static int rank(String kind) {
        return switch (kind) { case "OVERDUE" -> 3; case "DUE_TODAY" -> 2; default -> 1; };
    }

    private boolean remind(Long hostellerId, List<PaymentDue> dues, String kind, LocalDate today,
                           Map<Long, HostelPaymentSetting> settings) {
        HostellerProfile p = profileRepo.findById(hostellerId).orElse(null);
        if (p == null) return false;
        Long hostelId = dues.get(0).getHostelId();
        HostelPaymentSetting s = settings.computeIfAbsent(hostelId, settingsService::getOrDefault);
        String hostelName = hostelRepo.findById(hostelId).map(Hostel::getName).orElse("your hostel");

        BigDecimal total = BigDecimal.ZERO;
        StringBuilder lines = new StringBuilder();
        for (PaymentDue d : dues) {
            BigDecimal o = outstanding(d);
            total = total.add(o);
            lines.append(" - ").append(ReceiptPdfService.typeLabel(d.getDueType())).append(", ")
                    .append(ReceiptPdfService.periodLabel(d.getPeriodMonth(), d.getPeriodYear()))
                    .append(": Rs. ").append(o).append(" (due ").append(d.getDueDate().format(D)).append(")\n");
        }

        String headline = switch (kind) {
            case "OVERDUE" -> "Payment overdue";
            case "DUE_TODAY" -> "Payment due today";
            default -> "Payment due soon";
        };
        String subject = headline + ": Rs. " + total + " at " + hostelName;
        StringBuilder body = new StringBuilder()
                .append("Dear ").append(p.getFirstName()).append(",\n\n")
                .append("This is a friendly reminder from ").append(hostelName).append(". You have the following dues:\n\n")
                .append(lines).append("\nTotal outstanding: Rs. ").append(total).append("\n\n")
                .append("Pay in the Hostlify app: ").append(frontendUrl).append("/app/dues\n");
        if (s.getUpiId() != null) {
            body.append("Or pay by UPI to ").append(s.getUpiId())
                    .append(" and enter the UPI transaction ID in the app so the hostel can confirm it.\n");
        }
        boolean rentOpen = dues.stream().anyMatch(d -> PaymentDue.TYPE_RENT.equals(d.getDueType()));
        if (s.isLateFeeEnabled() && rentOpen && !"OVERDUE".equals(kind)) {
            body.append("\nNote: a late fee applies if rent is paid more than ").append(s.getLateFeeGraceDays())
                    .append(" day(s) after the due date.\n");
        }
        body.append("\nIf you have already paid, please ignore this message.");

        boolean delivered = notificationService.notifyUser("HOSTELLER", p.getId(), p.getAuthUserId(), email(p),
                "DUE_REMINDER_" + kind, subject, body.toString(), headline,
                "Rs. " + total + " outstanding at " + hostelName + ". Tap to pay.", "/app/dues", null, null);

        for (PaymentDue d : dues) {
            d.setLastReminderOn(today);
            d.setReminderCount(d.getReminderCount() + 1);
            dueRepo.save(d);
        }
        return delivered;
    }

    private BigDecimal paidOn(PaymentDue d) {
        return recordRepo.findByDueId(d.getId()).stream().filter(PaymentRecord::isSuccessful)
                .map(PaymentRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Amount still owed, also excluding UPI payments awaiting staff confirmation. */
    private BigDecimal outstanding(PaymentDue d) {
        BigDecimal pending = recordRepo.findByDueIdAndStatus(d.getId(), PaymentRecord.STATUS_PENDING_VERIFICATION).stream()
                .map(PaymentRecord::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return d.getRentAmount().subtract(paidOn(d)).subtract(pending).max(BigDecimal.ZERO);
    }

    private String email(HostellerProfile p) {
        String e = authUserRepo.findById(p.getAuthUserId()).map(u -> u.getEmail()).orElse(null);
        return e != null ? e : p.getEmail();
    }
}
