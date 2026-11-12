package com.hostlify.module.notification.service;

import com.hostlify.module.payment.entity.HostelPaymentSetting;
import com.hostlify.module.payment.entity.PaymentDue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** Pure rules behind the daily job: when to remind, and how much late fee to charge. */
class PaymentAutomationRulesTest {

    private static final LocalDate DUE = LocalDate.of(2026, 11, 20);

    private static HostelPaymentSetting settings() {
        HostelPaymentSetting s = new HostelPaymentSetting();
        s.setReminderDaysBefore(3);
        s.setRemindOnDueDate(true);
        s.setOverdueReminderIntervalDays(3);
        return s;
    }

    private static PaymentDue rent() {
        PaymentDue d = new PaymentDue();
        d.setDueDate(DUE);
        d.setRentAmount(new BigDecimal("6500"));
        return d;
    }

    // ─── reminders ───

    @Test
    void remindsBeforeOnAndAfterDueDate() {
        HostelPaymentSetting s = settings();
        assertEquals("UPCOMING", ScheduledNotificationService.reminderKind(DUE, DUE.minusDays(3), s));
        assertNull(ScheduledNotificationService.reminderKind(DUE, DUE.minusDays(2), s));
        assertEquals("DUE_TODAY", ScheduledNotificationService.reminderKind(DUE, DUE, s));
        assertNull(ScheduledNotificationService.reminderKind(DUE, DUE.plusDays(1), s));
        assertEquals("OVERDUE", ScheduledNotificationService.reminderKind(DUE, DUE.plusDays(3), s));
        assertEquals("OVERDUE", ScheduledNotificationService.reminderKind(DUE, DUE.plusDays(6), s));
    }

    @Test
    void zeroDisablesAReminder() {
        HostelPaymentSetting s = settings();
        s.setReminderDaysBefore(0);
        s.setRemindOnDueDate(false);
        s.setOverdueReminderIntervalDays(0);
        for (int d = -5; d <= 10; d++) {
            assertNull(ScheduledNotificationService.reminderKind(DUE, DUE.plusDays(d), s), "day " + d);
        }
    }

    // ─── late fee ───

    @Test
    void noLateFeeWhenOwnerHasNotEnabledIt() {
        HostelPaymentSetting s = settings();
        s.setLateFeeAmount(new BigDecimal("200"));
        assertNull(ScheduledNotificationService.lateFeeFor(rent(), s, DUE.plusDays(30)));
    }

    @Test
    void flatFeeOnlyAfterGracePeriod() {
        HostelPaymentSetting s = settings();
        s.setLateFeeEnabled(true);
        s.setLateFeeType("FLAT");
        s.setLateFeeAmount(new BigDecimal("200"));
        s.setLateFeeGraceDays(5);
        assertNull(ScheduledNotificationService.lateFeeFor(rent(), s, DUE.plusDays(5)));
        assertEquals(new BigDecimal("200.00"), ScheduledNotificationService.lateFeeFor(rent(), s, DUE.plusDays(6)));
        assertEquals(new BigDecimal("200.00"), ScheduledNotificationService.lateFeeFor(rent(), s, DUE.plusDays(40)));
    }

    @Test
    void perDayFeeGrowsAndIsCapped() {
        HostelPaymentSetting s = settings();
        s.setLateFeeEnabled(true);
        s.setLateFeeType("PER_DAY");
        s.setLateFeeAmount(new BigDecimal("50"));
        s.setLateFeeGraceDays(2);
        s.setLateFeeMaxAmount(new BigDecimal("500"));
        assertEquals(new BigDecimal("50.00"), ScheduledNotificationService.lateFeeFor(rent(), s, DUE.plusDays(3)));
        assertEquals(new BigDecimal("250.00"), ScheduledNotificationService.lateFeeFor(rent(), s, DUE.plusDays(7)));
        assertEquals(new BigDecimal("500.00"), ScheduledNotificationService.lateFeeFor(rent(), s, DUE.plusDays(60)));
    }

    @Test
    void neverBackChargesRentThatFellDueBeforeTheFeeWasEnabled() {
        HostelPaymentSetting s = settings();
        s.setLateFeeEnabled(true);
        s.setLateFeeAmount(new BigDecimal("200"));
        s.setLateFeeGraceDays(0);
        s.setLateFeeEnabledOn(DUE.plusDays(10));
        assertNull(ScheduledNotificationService.lateFeeFor(rent(), s, DUE.plusDays(30)));
        s.setLateFeeEnabledOn(DUE);
        assertNotNull(ScheduledNotificationService.lateFeeFor(rent(), s, DUE.plusDays(1)));
    }
}
