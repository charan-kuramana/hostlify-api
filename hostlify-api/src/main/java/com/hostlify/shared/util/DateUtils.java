package com.hostlify.shared.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateUtils {

    public static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    public static final DateTimeFormatter ISO_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private DateUtils() {}

    public static LocalDateTime nowKolkata() {
        return LocalDateTime.now(KOLKATA);
    }

    public static LocalDate todayKolkata() {
        return LocalDate.now(KOLKATA);
    }

    public static String formatTimestamp(LocalDateTime dt) {
        return dt == null ? null : dt.format(ISO_DT);
    }

    /** Returns the 1st of the given month/year */
    public static LocalDate firstOfMonth(int month, int year) {
        return LocalDate.of(year, month, 1);
    }

    /** Returns the 20th (due date) of the given month/year */
    public static LocalDate dueDate(int month, int year) {
        return LocalDate.of(year, month, 20);
    }
}
