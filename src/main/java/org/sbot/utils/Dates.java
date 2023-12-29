package org.sbot.utils;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public enum Dates {
    ;

    public static final String DATE_TIME_FORMAT = "dd/MM/yyyy-HH:mm";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);


    public static ZonedDateTime parseUTC(@NotNull String dateTime) {
        return LocalDateTime.parse(dateTime, FORMATTER).atZone(ZoneOffset.UTC);
    }

    public static String formatUTC(@NotNull ZonedDateTime dateTime) {
        return dateTime.withZoneSameInstant(ZoneOffset.UTC).format(FORMATTER);
    }
}
