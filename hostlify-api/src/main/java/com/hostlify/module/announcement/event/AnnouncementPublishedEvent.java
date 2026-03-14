package com.hostlify.module.announcement.event;

import com.hostlify.module.announcement.entity.Announcement;
import org.springframework.context.ApplicationEvent;

public class AnnouncementPublishedEvent extends ApplicationEvent {

    private final Announcement announcement;

    public AnnouncementPublishedEvent(Object source, Announcement announcement) {
        super(source);
        this.announcement = announcement;
    }

    public Announcement getAnnouncement() { return announcement; }
}
