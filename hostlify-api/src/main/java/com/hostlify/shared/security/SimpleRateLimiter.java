package com.hostlify.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window limiter for public (no-login) endpoints, e.g. the enquiry form.
 * Good enough for one API instance; swap for Redis/Bucket4j if the API is scaled out.
 */
@Component
public class SimpleRateLimiter {

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** Records a hit and returns false when the key exceeded {@code max} hits within {@code windowSeconds}. */
    public boolean allow(String key, int max, long windowSeconds) {
        long now = System.currentTimeMillis();
        long cutoff = now - windowSeconds * 1000;
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
            if (q.size() >= max) return false;
            q.addLast(now);
        }
        if (hits.size() > 50_000) hits.entrySet().removeIf(e -> e.getValue().isEmpty()); // bound memory
        return true;
    }

    /**
     * Client IP. X-Forwarded-For is only trusted when the direct peer is a private/loopback address
     * (our own proxy / load balancer); a client on the internet can't spoof it to dodge the limit.
     */
    public static String clientIp(HttpServletRequest req) {
        String peer = req.getRemoteAddr();
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank() && isPrivate(peer)) return xff.split(",")[0].trim();
        return peer;
    }

    private static boolean isPrivate(String ip) {
        try {
            java.net.InetAddress a = java.net.InetAddress.getByName(ip);
            return a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress()
                    || (ip != null && ip.toLowerCase().startsWith("fd")); // IPv6 unique-local
        } catch (Exception e) {
            return false;
        }
    }
}
