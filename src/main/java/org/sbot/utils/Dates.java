package org.sbot.utils;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;
import static net.dv8tion.jda.api.interactions.DiscordLocale.UNKNOWN;
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

    Map<Locale, String> LocalePatterns = Collections.unmodifiableMap(localePatterns());

    static LocalDateTime parseLocalDateTime(@NotNull Locale locale, @NotNull String dateTime) {
        return LocalDateTime.parse(asDateTimeFormat(locale, dateTime), DATE_TIME_FORMATTER);
    }

    static ZonedDateTime parse(@NotNull Locale locale, @Nullable ZoneId timezone, @NotNull Clock clock, @NotNull String dateTime) {
        requireNonNull(locale);
        requireNonNull(clock);
        dateTime = dateTime.strip();
        timezone = null != timezone ? timezone : UTC;
        if(dateTime.toLowerCase().startsWith(NOW_ARGUMENT)) {
            return parseNow(timezone, clock, dateTime);
        }
        dateTime = asDateTimeFormat(locale, dateTime);
        try { // try to parse at default zone
            return LocalDateTime.parse(dateTime, DATE_TIME_FORMATTER).atZone(timezone);
        } catch (DateTimeException e) {
            try { // try to parse at provided zone
                return ZonedDateTime.parse(dateTime, DASH_ZONED_DATE_TIME_FORMATTER);
            } catch (DateTimeException e2) {
                return ZonedDateTime.parse(dateTime, ZONED_DATE_TIME_FORMATTER);
            }
        }
    }

    private static ZonedDateTime parseNow(@NotNull ZoneId timezone, @NotNull Clock clock, @NotNull String nowTime) {
        long offset = 0L;
        if(nowTime.length() > NOW_ARGUMENT.length()) { // parse offset part
            String offsetHours = nowTime.substring(NOW_ARGUMENT.length()).strip();
            if('-' != offsetHours.charAt(0) && '+' != offsetHours.charAt(0)) {
                throw new DateTimeParseException("Malformed now offset", nowTime, nowTime.length() - offsetHours.length());
            }
            try {
                offset = new BigDecimal(offsetHours.replaceFirst(",", ".")).multiply(new BigDecimal(60)).longValue();
            } catch (NumberFormatException e) {
                throw new DateTimeParseException("Malformed now offset", nowTime, nowTime.length() - offsetHours.length(), e);
            }
            long oneCenturyMinutes = Duration.of(36500L, ChronoUnit.DAYS).toMinutes();
            if(Math.abs(offset) > oneCenturyMinutes) {
                throw new DateTimeParseException("Offset too far, can't be more than one century : " + oneCenturyMinutes / 60 + " hours", nowTime, nowTime.length() - offsetHours.length());
            }
        }
        return clock.instant().atZone(UTC).withZoneSameInstant(timezone).plusMinutes(offset);
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
        var timeAndZone = BLANK_SPACES.matcher(dateTime.substring(separator + 1)).replaceAll(" "); // set any spaces as one space
        return DATE_FORMATTER.format(LOCALIZED_DATE_FORMATTER.withLocale(locale).parse(date)) +
                (timeAndZone.startsWith(" ") ? "" : " ") + timeAndZone;
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

    @NotNull
    private static Map<Locale, String> localePatterns() {
        LocalDate date = LocalDate.parse("03/05/2011", DATE_FORMATTER);
        return Stream.of(DiscordLocale.values()).filter(not(UNKNOWN::equals)).map(DiscordLocale::toLocale)
                .collect(toMap(identity(), locale -> LOCALIZED_DATE_FORMATTER.withLocale(locale).format(date)
                            .replace("2011", "yyyy")
                            .replace("11", "yy")
                            .replace("05", "MM")
                            .replace("5", "M")
                            .replace("03", "dd")
                            .replace("3", "d") +
                            ' ' + TIME_FORMAT + " (optional timezone)"));
    }
}
