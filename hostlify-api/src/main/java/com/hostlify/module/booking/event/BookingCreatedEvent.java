package com.hostlify.module.booking.event;

import com.hostlify.module.booking.entity.BookingCheckin;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class BookingCreatedEvent extends ApplicationEvent {

    private final BookingCheckin booking;
    private final String hostellerEmail;
    private final String hostellerMobile;
    private final String hostellerName;
    private final String hostelName;
    private final String roomName;
    private final String bedName;

    public BookingCreatedEvent(Object source, BookingCheckin booking,
                               String hostellerEmail, String hostellerMobile,
                               String hostellerName, String hostelName,
                               String roomName, String bedName) {
        super(source);
        this.booking = booking;
        this.hostellerEmail = hostellerEmail;
        this.hostellerMobile = hostellerMobile;
        this.hostellerName = hostellerName;
        this.hostelName = hostelName;
        this.roomName = roomName;
        this.bedName = bedName;
    }
}
