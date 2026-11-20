package com.hostlify.module.platform;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public final class PlatformRepos {

    private PlatformRepos() {}

    public interface Settings extends JpaRepository<PlatformSettings, Integer> {}

    public interface Payments extends JpaRepository<PlatformPayment, Long> {
        List<PlatformPayment> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
        List<PlatformPayment> findTop100ByStatusOrderByCreatedAtDesc(String status);
        Optional<PlatformPayment> findByRazorpayOrderId(String orderId);
    }
}
