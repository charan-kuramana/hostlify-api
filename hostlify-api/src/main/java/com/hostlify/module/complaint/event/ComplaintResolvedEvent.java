package com.hostlify.module.complaint.event;

import com.hostlify.module.complaint.entity.ComplaintRecord;
import org.springframework.context.ApplicationEvent;

public class ComplaintResolvedEvent extends ApplicationEvent {

    private final ComplaintRecord complaint;
    private final String hostellerEmail;
    private final String hostellerName;

    public ComplaintResolvedEvent(Object source, ComplaintRecord complaint,
                                   String hostellerEmail, String hostellerName) {
        super(source);
        this.complaint = complaint;
        this.hostellerEmail = hostellerEmail;
        this.hostellerName = hostellerName;
    }

    public ComplaintRecord getComplaint() { return complaint; }
    public String getHostellerEmail() { return hostellerEmail; }
    public String getHostellerName() { return hostellerName; }
}
