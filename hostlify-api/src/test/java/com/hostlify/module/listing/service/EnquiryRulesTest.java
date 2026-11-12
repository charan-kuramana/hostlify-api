package com.hostlify.module.listing.service;

import com.hostlify.module.hostel.entity.Hostel;
import com.hostlify.shared.security.SimpleRateLimiter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnquiryRulesTest {

    @Test
    void normalisesIndianMobiles() {
        assertEquals("9876543210", EnquiryService.normaliseMobile("98765 43210"));
        assertEquals("9876543210", EnquiryService.normaliseMobile("+91 98765-43210"));
        assertEquals("9876543210", EnquiryService.normaliseMobile("09876543210"));
        assertNull(EnquiryService.normaliseMobile("12345"));
        assertNull(EnquiryService.normaliseMobile("5876543210")); // Indian mobiles start 6-9
        assertNull(EnquiryService.normaliseMobile(null));
    }

    @Test
    void slugify() {
        assertEquals("bliss-pg-vijayawada", Hostel.slugify("Bliss PG  - Vijayawada!"));
        assertEquals("sri-medha-hostel", Hostel.slugify("  Sri Medha Hostel "));
    }

    @Test
    void rateLimiterBlocksAfterMax() {
        SimpleRateLimiter l = new SimpleRateLimiter();
        for (int i = 0; i < 5; i++) assertTrue(l.allow("ip", 5, 3600));
        assertFalse(l.allow("ip", 5, 3600));
        assertTrue(l.allow("other-ip", 5, 3600));
    }
}
