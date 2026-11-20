package com.hostlify.module.compliance;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/** "Something needs your attention" for one resident (agreement to sign, checklist to confirm, settlement ready). */
@Getter
public class ResidentNoticeEvent extends ApplicationEvent {

    private final Long hostellerId;
    private final String template;
    private final String subject;
    private final String body;
    private final String url;

    public ResidentNoticeEvent(Object source, Long hostellerId, String template, String subject, String body, String url) {
        super(source);
        this.hostellerId = hostellerId;
        this.template = template;
        this.subject = subject;
        this.body = body;
        this.url = url;
    }
}
