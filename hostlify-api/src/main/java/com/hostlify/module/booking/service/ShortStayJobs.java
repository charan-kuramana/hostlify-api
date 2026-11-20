package com.hostlify.module.booking.service;

import com.hostlify.module.booking.entity.BookingCheckin;
import com.hostlify.module.booking.repository.BookingCheckinRepository;
import com.hostlify.module.compliance.ResidentNoticeEvent;
import com.hostlify.module.payment.entity.PaymentDue;
import com.hostlify.module.payment.repository.PaymentDueRepository;
import com.hostlify.shared.event.StaffNoticeEvent;
import com.hostlify.shared.exception.AppException;
import com.hostlify.shared.util.DateUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Short stays: end-of-stay reminders and extending a stay (bills only the extra days). */
@Service
public class ShortStayJobs {

    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("d MMM");

    private final BookingCheckinRepository bookingRepo;
    private final PaymentDueRepository dueRepo;
    private final ShortStayService shortStays;
    private final ApplicationEventPublisher events;

    public ShortStayJobs(BookingCheckinRepository bookingRepo, PaymentDueRepository dueRepo, ShortStayService shortStays,
                         ApplicationEventPublisher events) {
        this.bookingRepo = bookingRepo;
        this.dueRepo = dueRepo;
        this.shortStays = shortStays;
        this.events = events;
    }

    /** 9 AM IST: tell residents whose short stay ends tomorrow / today, and staff about today's check-outs. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    public void remind() {
        LocalDate today = DateUtils.todayKolkata();
        for (BookingCheckin b : bookingRepo.findAll()) {
            if (!"ACTIVE".equals(b.getStatus()) || !"SHORT".equals(b.getRentFrequency()) || b.getExpectedCheckoutDate() == null) continue;
            LocalDate end = b.getExpectedCheckoutDate();
            if (end.equals(today.plusDays(1))) {
                events.publishEvent(new ResidentNoticeEvent(this, b.getHostellerId(), "SHORT_STAY_ENDING",
                        "Your stay ends tomorrow", "Your stay ends tomorrow (" + end.format(D) + "). Want to stay longer? Ask the hostel to extend it. "
                        + "Leaving? Request check-out in the app so the room can be checked.", "/app/checkout"));
            } else if (end.equals(today)) {
                events.publishEvent(new ResidentNoticeEvent(this, b.getHostellerId(), "SHORT_STAY_ENDS_TODAY",
                        "Check-out today", "Your stay ends today. Please request check-out in the app before you leave.", "/app/checkout"));
                events.publishEvent(new StaffNoticeEvent(this, b.getHostelId(), "SHORT_STAY_ENDS_TODAY",
                        "Short stay ends today", "A short-stay resident is due to check out today. Check the room or extend the stay.",
                        "/admin/members", true, false));
            }
        }
    }

    /** Staff: move a short stay's end date later; the extra days are billed as a new charge. */
    @Transactional
    public BookingCheckin extend(Long bookingId, LocalDate newEnd) {
        BookingCheckin b = bookingRepo.findById(bookingId).orElseThrow(() -> new AppException("Stay not found", HttpStatus.NOT_FOUND));
        if (!"ACTIVE".equals(b.getStatus()) || !"SHORT".equals(b.getRentFrequency()) || b.getExpectedCheckoutDate() == null) {
            throw new AppException("Only an active short stay can be extended", HttpStatus.BAD_REQUEST);
        }
        LocalDate oldEnd = b.getExpectedCheckoutDate();
        if (newEnd == null || !newEnd.isAfter(oldEnd)) throw new AppException("The new date must be after " + oldEnd, HttpStatus.BAD_REQUEST);
        ShortStayService.Quote q = shortStays.quote(b.getHostelId(), b.getBedId(), oldEnd, newEnd);
        PaymentDue due = new PaymentDue();
        due.setHostellerId(b.getHostellerId());
        due.setBookingId(b.getId());
        due.setHostelId(b.getHostelId());
        due.setPeriodMonth(oldEnd.getMonthValue());
        due.setPeriodYear(oldEnd.getYear());
        due.setDueDate(DateUtils.todayKolkata());
        due.setRentAmount(q.amount());
        due.setDescription("Stay extended: " + q.description().replace("Short stay: ", ""));
        due.setStatus("UNPAID");
        dueRepo.save(due);
        b.setExpectedCheckoutDate(newEnd);
        b.setCustomFrequencyDays((b.getCustomFrequencyDays() == null ? 0 : b.getCustomFrequencyDays()) + q.days());
        b.setRentAmount((b.getRentAmount() == null ? BigDecimal.ZERO : b.getRentAmount()).add(q.amount()));
        BookingCheckin saved = bookingRepo.save(b);
        events.publishEvent(new ResidentNoticeEvent(this, b.getHostellerId(), "SHORT_STAY_EXTENDED", "Stay extended",
                "Your stay now ends on " + newEnd.format(D) + ". Rs. " + q.amount().stripTrailingZeros().toPlainString()
                        + " for the extra " + q.days() + " day(s) has been added to My Payments.", "/app/dues"));
        return saved;
    }
}
