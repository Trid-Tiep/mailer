package org.Trid;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class DateFormatter {
    public static String format(Date date) {
        if (date == null) return "";

        LocalDate today = LocalDate.now();
        LocalDate d = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        if (d.equals(today)) {
            return "Today " + new SimpleDateFormat("HH:mm").format(date);
        }
        if (d.equals(today.minusDays(1))) {
            return "Yesterday " + new SimpleDateFormat("HH:mm").format(date);
        }
        if (d.getYear() == today.getYear()) {
            return new SimpleDateFormat("MMM d, HH:mm").format(date);
        }
        return new SimpleDateFormat("MMM d, yyyy").format(date);
    }
}
