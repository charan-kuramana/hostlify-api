package com.hostlify.module.listing.event;

import com.hostlify.module.listing.entity.HostelEnquiry;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * CREATED: a new public enquiry (alert staff, confirm to the visitor).
 * VISIT_CONFIRMED: staff scheduled a visit (tell the visitor).
 * WAITLIST_NUDGE: beds are free and people are waiting (remind staff); {@code waitingCount} is set.
 */
@Getter
public class EnquiryEvent extends ApplicationEvent {

    public enum Kind { CREATED, VISIT_CONFIRMED, WAITLIST_NUDGE }

    private final Kind kind;
    private final HostelEnquiry enquiry;
    private final String hostelName;
    private final long waitingCount;
    private final long availableBeds;

    public EnquiryEvent(Object source, Kind kind, HostelEnquiry enquiry, String hostelName, long waitingCount, long availableBeds) {
        super(source);
        this.kind = kind;
        this.enquiry = enquiry;
        this.hostelName = hostelName;
        this.waitingCount = waitingCount;
        this.availableBeds = availableBeds;
    }
}
