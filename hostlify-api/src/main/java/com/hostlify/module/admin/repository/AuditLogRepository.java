package com.hostlify.module.admin.repository;

import com.hostlify.module.admin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByActorUserIdOrderByCreatedAtDesc(Long actorUserId, Pageable pageable);
    Page<AuditLog> findByHostelIdOrderByCreatedAtDesc(Long hostelId, Pageable pageable);
}
