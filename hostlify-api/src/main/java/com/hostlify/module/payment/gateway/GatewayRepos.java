package com.hostlify.module.payment.gateway;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public final class GatewayRepos {

    private GatewayRepos() {}

    public interface Gateways extends JpaRepository<OwnerPaymentGateway, Long> {
        List<OwnerPaymentGateway> findByOwnerIdOrderByIdAsc(Long ownerId);
        Optional<OwnerPaymentGateway> findByWebhookToken(String token);

        @Query("select g from OwnerPaymentGateway g where g.ownerId = :ownerId and g.provider = :provider "
                + "and ((:hostelId is null and g.hostelId is null) or g.hostelId = :hostelId)")
        Optional<OwnerPaymentGateway> findScope(@Param("ownerId") Long ownerId, @Param("hostelId") Long hostelId,
                                                @Param("provider") String provider);
    }

    public interface Orders extends JpaRepository<GatewayOrder, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select o from GatewayOrder o where o.id = :id")
        Optional<GatewayOrder> lockById(@Param("id") Long id);

        Optional<GatewayOrder> findFirstByGatewayIdAndProviderOrderId(Long gatewayId, String providerOrderId);

        List<GatewayOrder> findByStatusAndCreatedAtBetween(String status, LocalDateTime from, LocalDateTime to);

        List<GatewayOrder> findByStatusAndCreatedAtBefore(String status, LocalDateTime before);
    }
}
