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
import static org.sbot.utils.ArgumentValidator.BLANK_SPACES;

public interface Dates {

    ZoneId UTC = ZoneId.of("UTC");

    String NOW_ARGUMENT = "now";

    String DATE_FORMAT = "dd/MM/yyyy";
    String TIME_FORMAT = "HH:mm";
    String DATE_TIME_FORMAT = DATE_FORMAT + ' ' + TIME_FORMAT;
    String ZONED_FORMAT = "[ ]z";
    String DASH_ZONED_FORMAT =  "-z";

    DateTimeFormatter LOCALIZED_DATE_FORMATTER = DateTimeFormatter.ofLocalizedPattern("yMd");

    DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);
    DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_FORMAT);
    DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
    DateTimeFormatter ZONED_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT + ZONED_FORMAT);
    DateTimeFormatter DASH_ZONED_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT + DASH_ZONED_FORMAT);

    static LocalDateTime parseLocalDateTime(@NotNull Locale locale, @NotNull String dateTime) {
        return LocalDateTime.parse(asDateTimeFormat(locale, dateTime), DATE_TIME_FORMATTER);
    }

    static ZonedDateTime parse(@NotNull Locale locale, @Nullable ZoneId defaultTimezone, @NotNull Clock clock, @NotNull String dateTime) {
        requireNonNull(locale);
        requireNonNull(clock);
        if(dateTime.startsWith(NOW_ARGUMENT)) {
            return parseNow(defaultTimezone, clock, dateTime.replaceFirst(NOW_ARGUMENT, "").strip());
        }
        dateTime = asDateTimeFormat(locale, dateTime);
        try { // try to parse at default zone
            return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER).atZone(defaultTimezone(defaultTimezone));
        } catch (DateTimeException e) {
            try { // try to parse at provided zone
                return ZonedDateTime.parse(dateTime, DASH_ZONED_DATE_TIME_FORMATTER);
            } catch (DateTimeException e2) {
                return ZonedDateTime.parse(dateTime, ZONED_DATE_TIME_FORMATTER);
            }
        }
    }

    private static ZonedDateTime parseNow(@Nullable ZoneId defaultTimezone, @NotNull Clock clock, @NotNull String zoneId) {
        ZoneId zone;
        try {
            zone = zoneId.isEmpty() ? defaultTimezone(defaultTimezone) : ZoneId.of(zoneId, ZoneId.SHORT_IDS);
        } catch (DateTimeException e) {
            if(!zoneId.startsWith("-")) {
                throw e;
            }
            zone = ZoneId.of(zoneId.replaceFirst("-", ""), ZoneId.SHORT_IDS);
        }
        return clock.instant().atZone(zone);
    }

    private static ZoneId defaultTimezone(@Nullable ZoneId defaultTimezone) {
        return null != defaultTimezone ? defaultTimezone : UTC;
    }

    // this extract the localized date part of a date time and rebuild it formatted as DATE_TIME_FORMAT
    private static String asDateTimeFormat(@NotNull Locale locale, @NotNull String dateTime) {
        int maxIndex = dateTime.length() - TIME_FORMAT.length();
        int separator = 0;
        char separatorChar = 0;
        for(int i = 3; i > 0 && separator <= maxIndex; separator++) {
            separatorChar = dateTime.charAt(separator);
            if(!Character.isDigit(separatorChar)) {
                i--; // this skip 3 first groups of digits
            }
        }
        if(--separator == maxIndex || ('-' != separatorChar && !Character.isWhitespace(separatorChar))) {
            throw new DateTimeParseException("Malformed date-time", dateTime, separator);
        }
        String date = dateTime.substring(0, separator);
        var timeZone = BLANK_SPACES.matcher(dateTime.substring(separator + 1)).replaceAll(" "); // set any spaces as one space
        return DATE_FORMATTER.format(LOCALIZED_DATE_FORMATTER.withLocale(locale).parse(date)) +
                (timeZone.startsWith(" ") ? "" : " ") + timeZone;
    }

    static String formatDiscord(@NotNull ZonedDateTime dateTime) {
        return TimeFormat.DATE_SHORT.format(dateTime) + ' ' + TimeFormat.TIME_SHORT.format(dateTime);
    }

    static String formatDiscordRelative(@NotNull ZonedDateTime dateTime) {
        return TimeFormat.RELATIVE.format(dateTime);
    }

    static String formatUTC(@NotNull Locale locale, @NotNull ZonedDateTime dateTime) {
        dateTime = dateTime.withZoneSameInstant(Dates.UTC);
        return dateTime.format(LOCALIZED_DATE_FORMATTER.withLocale(locale)) + '-' + dateTime.format(TIME_FORMATTER);
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
