package com.hostlify.module.booking.event;

import com.hostlify.module.booking.entity.BookingCheckin;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class BookingCheckedOutEvent extends ApplicationEvent {

    private final BookingCheckin booking;
    private final String hostellerEmail;
    private final String hostellerName;
    private final BigDecimal advanceRefundAmount;

    public BookingCheckedOutEvent(Object source, BookingCheckin booking,
                                  String hostellerEmail, String hostellerName,
                                  BigDecimal advanceRefundAmount) {
        super(source);
        this.booking = booking;
        this.hostellerEmail = hostellerEmail;
        this.hostellerName = hostellerName;
        this.advanceRefundAmount = advanceRefundAmount;
    }
}
