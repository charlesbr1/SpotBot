package org.sbot.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public interface Dates {

    ZoneId UTC = ZoneId.of("UTC");

    String NOW_ARGUMENT = "now";

    String DATE_TIME_FORMAT = "dd/MM/yyyy-HH:mm";
    String ZONED_DATE_TIME_FORMAT = "dd/MM/yyyy-HH:mmz";
    String ZONED_DATE_TIME_FORMAT_DASH = "dd/MM/yyyy-HH:mm-z";
    DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT, Locale.ENGLISH);
    DateTimeFormatter ZONED_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(ZONED_DATE_TIME_FORMAT, Locale.ENGLISH);
    DateTimeFormatter ZONED_DATE_TIME_FORMATTER_DASH = DateTimeFormatter.ofPattern(ZONED_DATE_TIME_FORMAT_DASH, Locale.ENGLISH);


    static LocalDateTime parseLocal(@NotNull String dateTime) {
        return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER);
    }

    static ZonedDateTime parse(@NotNull String dateTime) {
        if(dateTime.startsWith(NOW_ARGUMENT)) {
            return parseNow(dateTime.replaceFirst(NOW_ARGUMENT, ""));
        }
        Function<DateTimeFormatter, Optional<ZonedDateTime>> parser = format -> {
            try {
                return Optional.of(ZonedDateTime.parse(dateTime, format));
            } catch (DateTimeException e) {
                return Optional.empty();
            }
        };
        return parser.apply(ZONED_DATE_TIME_FORMATTER_DASH)
                        .or(() -> parser.apply(ZONED_DATE_TIME_FORMATTER))
                        .orElseGet(() -> parseUTC(dateTime));
    }

    private static ZonedDateTime parseNow(@Nullable String zoneId) {
        ZoneId zone;
        try {
            zone = null == zoneId || zoneId.isEmpty() ? UTC : ZoneId.of(zoneId, ZoneId.SHORT_IDS);
        } catch (DateTimeException e) {
            zone = ZoneId.of(zoneId.replaceFirst("-", ""), ZoneId.SHORT_IDS);
        }
        return Instant.now().atZone(zone);
    }

    static ZonedDateTime parseUTC(@NotNull String dateTime) {
        return parseLocal(dateTime).atZone(UTC);
    }

    static String format(@NotNull ZonedDateTime dateTime) {
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    static String formatUTC(@NotNull ZonedDateTime dateTime) {
        return dateTime.withZoneSameInstant(Dates.UTC).format(DATE_TIME_FORMATTER);
    }

    static ZonedDateTime nowUtc(@NotNull Clock clock) {
        return ZonedDateTime.ofInstant(clock.instant(), UTC);
    }

    static Optional<ZonedDateTime> parseUtcDateTime(@Nullable Timestamp timestamp) {
        return Optional.ofNullable(timestamp)
                .map(dateTime -> dateTime.toInstant().atZone(UTC));
    }

    @Nullable
    static ZonedDateTime parseUtcDateTimeOrNull(@Nullable Timestamp timestamp) {
        return parseUtcDateTime(timestamp).orElse(null);
    }
}
