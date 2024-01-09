package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public enum Dates {
    ;

    public static final String DATE_TIME_FORMAT = "dd/MM/yyyy-HH:mm";

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);


    public static ZonedDateTime parseUTC(@NotNull String dateTime) {
        return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER).atZone(ZoneOffset.UTC);
    }

    public static String formatUTC(@NotNull ZonedDateTime dateTime) {
        return dateTime.withZoneSameInstant(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
    }
}
