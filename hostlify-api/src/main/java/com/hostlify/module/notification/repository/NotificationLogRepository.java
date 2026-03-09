package com.hostlify.module.notification.repository;

import com.hostlify.module.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByRecipientTypeAndRecipientId(String recipientType, Long recipientId);
    List<NotificationLog> findByStatusAndRetryCountLessThan(String status, int maxRetries);
}
