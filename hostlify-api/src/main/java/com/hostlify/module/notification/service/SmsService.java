package com.hostlify.module.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    @Value("${app.notification.skip-sms:true}")
    private boolean skipSms;

    public void sendSms(String mobile, String message) {
        if (skipSms) {
            log.info("[DEV] SKIPPING SMS to {}: {}", mobile, message);
            return;
        }
        // In production: POST to MSG91 API with DLT template
        // msg91Client.send(mobile, message);
        log.info("Sending SMS to {}: {}", mobile, message);
    }
}
