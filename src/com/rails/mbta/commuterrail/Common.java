package com.rails.mbta.commuterrail;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.rails.mbta.commuterrail.schedule.Trip;

public class Common {
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormat.forPattern("hh:mma");
    public static final DateTimeFormatter TIME_FORMATTER_W_SECONDS = DateTimeFormat.forPattern("hh:mm:ssa");
    public static final DateTimeFormatter TODAY_FORMATTER = DateTimeFormat.forPattern("e");
    public static Trip [] trips;
}
