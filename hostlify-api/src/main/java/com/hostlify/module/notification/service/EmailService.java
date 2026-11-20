package com.hostlify.module.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends email over SMTP. Works with any SMTP provider; the free option we use is
 * Brevo (300 emails/day): host smtp-relay.brevo.com, port 587, with your Brevo SMTP login + key.
 *
 * Email is skipped (logged only) when app.notification.skip-email=true or no SMTP host is configured,
 * so local dev never needs credentials.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.notification.skip-email:true}")
    private boolean skipEmail;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    @Value("${app.mail.from:Hostlify <no-reply@hostlify.in>}")
    private String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender;
    }

    public boolean isEnabled() {
        return !skipEmail && smtpHost != null && !smtpHost.isBlank() && mailSender.getIfAvailable() != null;
    }

    public void sendEmail(String to, String subject, String body) {
        sendEmail(to, subject, body, null, null);
    }

    /**
     * @param attachmentName file name shown to the recipient, e.g. "Rent-Receipt-Oct-2026.pdf" (null = no attachment)
     * @throws RuntimeException when the SMTP server rejects the message, so callers can log FAILED
     */
    public void sendEmail(String to, String subject, String body, String attachmentName, byte[] attachment) {
        if (to == null || to.isBlank()) return;
        if (!isEnabled()) {
            log.info("[EMAIL SKIPPED] to={} subject='{}'{}", to, subject,
                    attachmentName != null ? " attachment=" + attachmentName : "");
            return;
        }
        try {
            JavaMailSender sender = mailSender.getObject();
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, attachment != null, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            if (attachment != null && attachmentName != null) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachment), "application/pdf");
            }
            sender.send(msg);
            log.info("Email sent to {}: {}", to, subject);
        } catch (Exception e) {
            throw new RuntimeException("Email to " + to + " failed: " + e.getMessage(), e);
        }
    }
}
