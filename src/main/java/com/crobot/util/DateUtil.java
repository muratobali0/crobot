package com.crobot.util;

import java.time.LocalTime;

public final class DateUtil {


    /**
     * Returns true if the time between 21:00 and 07:00.
     *
     * @return
     */
    public static boolean isNight() {
        LocalTime nightStart = LocalTime.of(21, 0);
        LocalTime nightEnd = LocalTime.of(7, 0);
        LocalTime now = LocalTime.now();
        return now.isAfter(nightStart) && now.isBefore(nightEnd);
    }

}
