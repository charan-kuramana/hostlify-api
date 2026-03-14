package com.hostlify.module.notification.entity;

import com.hostlify.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(
    name = "notification_log",
    indexes = {
        @Index(name = "idx_nl_recipient", columnList = "recipient_type,recipient_id"),
        @Index(name = "idx_nl_status",    columnList = "status")
    }
)
public class NotificationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_type", nullable = false, length = 20)
    private String recipientType;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(nullable = false, length = 10)
    private String channel;

    @Column(name = "template_name", nullable = false, length = 80)
    private String templateName;

    @Column(length = 300)
    private String subject;

    @Column(name = "body_preview", length = 500)
    private String bodyPreview;

    @Column(name = "to_email", length = 150)
    private String toEmail;

    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;
}
