package com.hostlify.module.compliance.settlement;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.compliance.ResidentNoticeEvent;
import com.hostlify.module.hosteller.repository.HostellerProfileRepository;
import com.hostlify.shared.event.StaffNoticeEvent;
import com.hostlify.shared.util.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Keeps deposit refunds on time: staff are reminded two days before the hostel's own refund deadline,
 * and when it passes the owner is emailed and the resident is told they can raise it.
 */
@Slf4j
@Component
public class DepositRefundJobs {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final BookingCheckinRepository bookingRepo;
    private final DepositSettlementRepository settlementRepo;
    private final SettlementService settlements;
    private final HostellerProfileRepository profileRepo;
    private final ApplicationEventPublisher events;

    public DepositRefundJobs(BookingCheckinRepository bookingRepo, DepositSettlementRepository settlementRepo,
                             SettlementService settlements, HostellerProfileRepository profileRepo, ApplicationEventPublisher events) {
        this.bookingRepo = bookingRepo;
        this.settlementRepo = settlementRepo;
        this.settlements = settlements;
        this.profileRepo = profileRepo;
        this.events = events;
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void run() {
        LocalDate today = DateUtils.todayKolkata();
        for (BookingCheckin b : bookingRepo.findByStatusAndCheckoutDateGreaterThanEqual("CHECKED_OUT", today.minusDays(120))) {
            try {
                check(b, today);
            } catch (Exception e) {
                log.warn("Deposit reminder for booking {} failed: {}", b.getId(), e.getMessage());
            }
        }
    }

    void check(BookingCheckin b, LocalDate today) {
        if (b.getDepositAlerts() >= 2) return;
        DepositSettlement s = settlementRepo.findByBookingId(b.getId()).orElse(null);
        if (SettlementService.isSettled(s)) return;
        if (s == null && settlements.depositHeld(b).signum() <= 0) return; // nothing was held
        LocalDate due = settlements.refundDueBy(b);
        if (due == null) return;
        String name = profileRepo.findById(b.getHostellerId()).map(p -> p.getFirstName() + (p.getLastName() != null ? " " + p.getLastName() : "")).orElse("A resident");
        String url = "/admin/members/" + b.getHostellerId();

        if (today.isAfter(due)) {
            events.publishEvent(new StaffNoticeEvent(this, b.getHostelId(), "DEPOSIT_REFUND_OVERDUE",
                    "Deposit refund overdue: " + name,
                    name + "'s deposit refund was due by " + due.format(D) + ". Finalize the settlement and pay the refund.",
                    url, false, true));
            events.publishEvent(new ResidentNoticeEvent(this, b.getHostellerId(), "DEPOSIT_REFUND_OVERDUE",
                    "Your deposit refund is late",
                    "The hostel promised to settle your deposit by " + due.format(D) + ". We've reminded them. "
                            + "If you don't hear back, you can raise it from My Documents once the statement is ready.",
                    "/app/documents"));
            b.setDepositAlerts((short) 2);
            bookingRepo.save(b);
        } else if (b.getDepositAlerts() < 1 && !today.isBefore(due.minusDays(2))) {
            events.publishEvent(new StaffNoticeEvent(this, b.getHostelId(), "DEPOSIT_REFUND_DUE",
                    "Deposit refund due " + due.format(D) + ": " + name,
                    "Settle " + name + "'s deposit by " + due.format(D) + " (your stated refund time).", url, false, false));
            b.setDepositAlerts((short) 1);
            bookingRepo.save(b);
        }
    }
}
