package com.hostlify.shared.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/** Push alert to every active staff member of a hostel (and its owner); optional email to the owner. */
@Getter
public class StaffNoticeEvent extends ApplicationEvent {

    private final Long hostelId;
    private final String template;
    private final String title;
    private final String body;
    private final String url;
    private final boolean includeSupervisors;
    private final boolean emailOwner;

    public StaffNoticeEvent(Object source, Long hostelId, String template, String title, String body, String url,
                            boolean includeSupervisors, boolean emailOwner) {
        super(source);
        this.hostelId = hostelId;
        this.template = template;
        this.title = title;
        this.body = body;
        this.url = url;
        this.includeSupervisors = includeSupervisors;
        this.emailOwner = emailOwner;
    }
}
