package com.hostlify.module.notification.repository;

import com.hostlify.module.notification.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByAuthUserId(Long authUserId);
    Optional<PushSubscription> findByEndpoint(String endpoint);
}
