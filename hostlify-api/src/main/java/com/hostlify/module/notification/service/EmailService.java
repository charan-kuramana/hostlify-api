package com.hostlify.module.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${app.notification.skip-email:true}")
    private boolean skipEmail;

    public void sendEmail(String to, String subject, String body) {
        if (skipEmail) {
            log.info("[DEV] SKIPPING email to {}: {}", to, subject);
            return;
        }
        // In production: send via Azure Communication Services EmailClient
        // AzureEmailClient.send(to, subject, body);
        log.info("Sending email to {}: {}", to, subject);
    }
}
