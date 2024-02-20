package org.sbot.utils;

import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public interface Dates {

    ZoneId UTC = ZoneId.of("UTC");

    String NOW_ARGUMENT = "now";

    String DATE_FORMAT = "dd/MM/yyyy";
    String TIME_FORMAT = "HH:mm";
    String DATE_TIME_FORMAT = DATE_FORMAT + '-' + TIME_FORMAT;
    String ZONED_DATE_TIME_FORMAT = DATE_TIME_FORMAT + "z";
    String DASH_ZONED_DATE_TIME_FORMAT = DATE_TIME_FORMAT + "-z";

    DateTimeFormatter LOCALIZED_DATE_FORMATTER = DateTimeFormatter.ofLocalizedPattern("yMd");

    DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);
    DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_FORMAT);
    DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
    DateTimeFormatter ZONED_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(ZONED_DATE_TIME_FORMAT);
    DateTimeFormatter DASH_ZONED_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DASH_ZONED_DATE_TIME_FORMAT);


    static LocalDateTime parseLocalDateTime(@NotNull Locale locale, @NotNull String dateTime) {
        return LocalDateTime.parse(asDateTimeFormat(locale, dateTime), DATE_TIME_FORMATTER);
    }

    static ZonedDateTime parse(@NotNull Locale locale, @NotNull Clock clock, @NotNull String dateTime) {
        requireNonNull(locale);
        requireNonNull(clock);
        if(dateTime.startsWith(NOW_ARGUMENT)) {
            return parseNow(clock, dateTime.replaceFirst(NOW_ARGUMENT, ""));
        }
        dateTime = asDateTimeFormat(locale, dateTime);
        try {
            return ZonedDateTime.parse(dateTime, DASH_ZONED_DATE_TIME_FORMATTER);
        } catch (DateTimeException e) {
            try {
                return ZonedDateTime.parse(dateTime, ZONED_DATE_TIME_FORMATTER);
            } catch (DateTimeException e2) { // parse as UTC
                return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER).atZone(UTC);
            }
        }
    }

    private static ZonedDateTime parseNow(@NotNull Clock clock, @Nullable String zoneId) {
        ZoneId zone;
        try {
            zone = null == zoneId || zoneId.isEmpty() ? UTC : ZoneId.of(zoneId, ZoneId.SHORT_IDS);
        } catch (DateTimeException e) {
            if(!zoneId.startsWith("-")) {
                throw e;
            }
            zone = ZoneId.of(zoneId.replaceFirst("-", ""), ZoneId.SHORT_IDS);
        }
        return clock.instant().atZone(zone);
    }

    static String formatUTC(@NotNull Locale locale, @NotNull ZonedDateTime dateTime) {
        dateTime = dateTime.withZoneSameInstant(Dates.UTC);
        return dateTime.format(LOCALIZED_DATE_FORMATTER.withLocale(locale)) + '-' + dateTime.format(TIME_FORMATTER);
    }

    private static String asDateTimeFormat(@NotNull Locale locale, @NotNull String dateTime) {
        int separator = dateTime.indexOf('-');
        if(separator <= 0 || separator >= dateTime.length() - TIME_FORMAT.length()) {
            throw new DateTimeParseException("Malformed date-time", dateTime, separator);
        }
        String date = dateTime.substring(0, separator);
        return DATE_FORMATTER.format(LOCALIZED_DATE_FORMATTER.withLocale(locale).parse(date)) +
                dateTime.substring(separator);
    }

    //TODO test
    static String formatDiscord(@NotNull ZonedDateTime dateTime) {
        return TimeFormat.DATE_SHORT.format(dateTime) + '-' + TimeFormat.TIME_SHORT.format(dateTime);
    }

    //TODO test
    static String formatDiscordRelative(@NotNull ZonedDateTime dateTime) {
        return TimeFormat.RELATIVE.format(dateTime);
    }

    static ZonedDateTime nowUtc(@NotNull Clock clock) {
        return ZonedDateTime.ofInstant(clock.instant(), UTC);
    }

    // sql mapper

    static Optional<ZonedDateTime> parseUtcDateTime(@Nullable Timestamp timestamp) {
        return Optional.ofNullable(timestamp)
                .map(dateTime -> dateTime.toInstant().atZone(UTC));
    }

    @Nullable
    static ZonedDateTime parseUtcDateTimeOrNull(@Nullable Timestamp timestamp) {
        return parseUtcDateTime(timestamp).orElse(null);
    }
}
