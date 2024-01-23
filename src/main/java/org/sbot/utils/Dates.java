package org.sbot.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public interface Dates {

    String DATE_TIME_FORMAT = "dd/MM/yyyy-HH:mm";

    DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);


    static LocalDateTime parse(@NotNull String dateTime) {
        return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER);
    }

    static ZonedDateTime parseUTC(@NotNull String dateTime) {
        return parse(dateTime).atZone(ZoneOffset.UTC);
    }

    static String format(@NotNull ZonedDateTime dateTime) {
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    static String formatUTC(@NotNull ZonedDateTime dateTime) {
        return dateTime.withZoneSameInstant(ZoneOffset.UTC).format(DATE_TIME_FORMATTER);
    }

    static ZonedDateTime nowUtc() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    @Nullable
    static ZonedDateTime parseUtcDateTimeOrNull(@Nullable Timestamp timestamp) {
        return Optional.ofNullable(timestamp)
                .map(dateTime -> dateTime.toInstant().atZone(ZoneOffset.UTC)).orElse(null);
    }
}
