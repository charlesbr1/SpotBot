package org.sbot.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

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

    @Nullable
    public static ZonedDateTime parseDateTime(@Nullable Timestamp timestamp) {
        return Optional.ofNullable(timestamp)
                .map(dateTime -> dateTime.toLocalDateTime().atZone(ZoneOffset.UTC)).orElse(null);
    }

    public record DaysHours(int days, int hours) {}

    @NotNull
    public static DaysHours daysHoursSince(@NotNull ZonedDateTime lastTime) {
        Duration duration = Duration.between(lastTime, ZonedDateTime.now());
        return new DaysHours(duration.toHoursPart(), duration.toHoursPart());
    }
}
