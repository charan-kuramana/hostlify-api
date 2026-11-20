package com.hostlify.module.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hostlify.module.notification.entity.PushSubscription;
import com.hostlify.module.notification.repository.PushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushAsyncService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Security;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Free browser push notifications (Web Push + VAPID). No third-party account needed:
 * the browser vendor's push service delivers the message.
 *
 * Disabled (no-op) when VAPID keys aren't configured. Generate a pair once with
 * `npx web-push generate-vapid-keys` and set APP_PUSH_VAPID_PUBLIC_KEY / APP_PUSH_VAPID_PRIVATE_KEY.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushSubscriptionRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();
    private PushAsyncService pushService;

    @Value("${app.push.vapid-public-key:}")
    private String publicKey;

    @Value("${app.push.vapid-private-key:}")
    private String privateKey;

    @Value("${app.push.subject:mailto:support@hostlify.in}")
    private String subject;

    public PushService(PushSubscriptionRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    void init() {
        if (publicKey.isBlank() || privateKey.isBlank()) {
            log.info("Web push disabled: VAPID keys not configured");
            return;
        }
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            pushService = new PushAsyncService(publicKey, privateKey, subject);
            log.info("Web push enabled");
        } catch (Exception e) {
            log.error("Web push disabled: invalid VAPID keys ({})", e.getMessage());
            pushService = null;
        }
    }

    public boolean isEnabled() {
        return pushService != null;
    }

    public String getPublicKey() {
        return isEnabled() ? publicKey : null;
    }

    @Transactional
    public void subscribe(Long authUserId, String endpoint, String p256dh, String auth, String userAgent) {
        // An endpoint belongs to one browser; if another user logs in on it, move it to them.
        PushSubscription sub = repo.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        sub.setAuthUserId(authUserId);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        sub.setUserAgent(userAgent != null && userAgent.length() > 300 ? userAgent.substring(0, 300) : userAgent);
        repo.save(sub);
    }

    @Transactional
    public void unsubscribe(Long authUserId, String endpoint) {
        repo.findByEndpoint(endpoint)
                .filter(s -> s.getAuthUserId().equals(authUserId))
                .ifPresent(repo::delete);
    }

    public boolean hasSubscription(Long authUserId) {
        return !repo.findByAuthUserId(authUserId).isEmpty();
    }

    /**
     * Sends to every device of the user. Returns how many devices accepted it.
     * Expired subscriptions (404/410 from the push service) are deleted.
     */
    public int sendToUser(Long authUserId, String title, String body, String url) {
        if (!isEnabled() || authUserId == null) return 0;
        String payload;
        try {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("title", title);
            p.put("body", body);
            p.put("url", url != null ? url : "/");
            payload = mapper.writeValueAsString(p);
        } catch (Exception e) {
            return 0;
        }
        int delivered = 0;
        for (PushSubscription sub : repo.findByAuthUserId(authUserId)) {
            try {
                Notification n = new Notification(sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload);
                int status = pushService.send(n).get(10, TimeUnit.SECONDS).getStatusCode();
                if (status == 404 || status == 410) {
                    repo.delete(sub);
                } else if (status >= 200 && status < 300) {
                    delivered++;
                } else {
                    log.warn("Push to user {} returned HTTP {}", authUserId, status);
                }
            } catch (Exception e) {
                log.warn("Push to user {} failed: {}", authUserId, e.getMessage());
            }
        }
        return delivered;
    }
}
