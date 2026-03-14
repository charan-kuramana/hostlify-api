package com.hostlify.shared.util;

import java.time.LocalDate;
import java.util.UUID;

public class SlugUtils {

    private SlugUtils() {}

    /**
     * Generate a referral code: first 4 chars of name (uppercase) + current year + 4 random chars.
     * e.g. RAVI2026XQ7A
     */
    public static String generateReferralCode(String firstName) {
        String prefix = firstName.toUpperCase().replaceAll("[^A-Z]", "");
        if (prefix.length() > 4) prefix = prefix.substring(0, 4);
        else while (prefix.length() < 4) prefix += "X";

        String year = String.valueOf(LocalDate.now().getYear());
        String random = UUID.randomUUID().toString().toUpperCase().replaceAll("[^A-Z0-9]", "").substring(0, 4);
        return prefix + year + random;
    }

    /**
     * Generate a QR token for hostel registration links.
     */
    public static String generateQrToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
