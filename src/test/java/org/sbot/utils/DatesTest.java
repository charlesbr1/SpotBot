package org.sbot.utils;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.utils.Dates.DATE_TIME_FORMATTER;
import static org.sbot.utils.Dates.UTC;

public class DatesTest {

    public static ZonedDateTime nowUtc() {
        return Instant.now().atZone(Dates.UTC);
    }

    @Test
    void DATE_TIME_FORMATTER() {
        assertNotNull(DATE_TIME_FORMATTER.parse("01/01/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> DATE_TIME_FORMATTER.parse("01012000-00:00"));
    }

    @Test
    void parseLocalDateTime() {
        assertThrows(NullPointerException.class, () -> Dates.parseLocalDateTime(Locale.US, null));
        assertThrows(NullPointerException.class, () -> Dates.parseLocalDateTime(null, "01/01/2000-00:00"));
        assertEquals(LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER), Dates.parseLocalDateTime(Locale.US, "01/01/2000-00:00"));
        assertEquals(LocalDateTime.parse("21/01/2000-00:00", DATE_TIME_FORMATTER), Dates.parseLocalDateTime(Locale.US, "01/21/2000-00:00"));
        assertEquals(LocalDateTime.parse("21/01/2000-00:00", DATE_TIME_FORMATTER), Dates.parseLocalDateTime(Locale.FRANCE, "21/01/2000-00:00"));

        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.US, "01/41/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.FRENCH, "41/01/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.US, "20/01/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.FRENCH, "01/20/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.FRENCH, "01/01/2000-25:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.FRENCH, "01/01/2000-00:61"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.FRENCH, "01012000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.FRENCH, "01/012000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.FRENCH, "0101/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.FRENCH, "01/01/200000:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.FRENCH, "01/01/2000-0000"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocalDateTime(Locale.FRENCH, "01/01/2000-00.00"));
    }

    @Test
    void parse() {
        assertThrows(NullPointerException.class, () -> Dates.parse(Locale.US, Clock.systemUTC(), null));
        assertThrows(NullPointerException.class, () -> Dates.parse(Locale.US, null, "now"));
        assertThrows(NullPointerException.class, () -> Dates.parse(Locale.US, null, "notnow"));
        assertThrows(NullPointerException.class, () -> Dates.parse(null, Clock.systemUTC(), "now"));

        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Clock.systemUTC(), "now-"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Clock.systemUTC(), "now--"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Clock.systemUTC(), "nowUTC-"));
        assertDoesNotThrow(() -> Dates.parse(Locale.US, Clock.systemUTC(), "01/21/2000-10:00-Asia/Tokyo"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Clock.systemUTC(), "-01/21/2000-10:00-Asia/Tokyo"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Clock.systemUTC(), "01/21/200010:00-Asia/Tokyo"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Clock.systemUTC(), "01/21/200010:00-Asia/To-kyo"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Clock.systemUTC(), "01/21/200010:00-Asia/Tokyo-"));

        ZonedDateTime date = Instant.now().atZone(UTC);
        Clock clock = Clock.fixed(date.toInstant(), UTC);
        assertEquals(date, Dates.parse(Locale.US, clock, "now"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "now"));
        assertEquals(date, Dates.parse(Locale.US, clock, "now-UTC"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "now-UTC"));
        date = Instant.now().atZone(ZoneId.of("Asia/Tokyo"));
        clock = Clock.fixed(date.toInstant(), ZoneId.of("Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.US, clock, "now-JST"));
        assertEquals(date, Dates.parse(Locale.US, clock, "now-Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "now-Asia/Tokyo"));
        date = Instant.now().atZone(ZoneId.of("CET"));
        clock = Clock.fixed(date.toInstant(), ZoneId.of("CET"));
        assertEquals(date, Dates.parse(Locale.US, clock, "now-CET"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "now-CET"));
        date = Instant.now().atZone(ZoneId.of("Europe/Paris"));
        clock = Clock.fixed(date.toInstant(), ZoneId.of("CET"));
        assertEquals(date, Dates.parse(Locale.US, clock, "now-Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "now-Europe/Paris"));
        date = Instant.now().atZone(ZoneId.of("+12:33"));
        clock = Clock.fixed(date.toInstant(), ZoneId.of("+12:33"));
        assertEquals(date, Dates.parse(Locale.US, clock, "now-+12:33"));
        assertEquals(date, Dates.parse(Locale.US, clock, "now+12:33"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "now+12:33"));
        date = Instant.now().atZone(ZoneId.of("-08:01"));
        clock = Clock.fixed(date.toInstant(), ZoneId.of("-08:01"));
        assertEquals(date, Dates.parse(Locale.US, clock, "now--08:01"));
        assertEquals(date, Dates.parse(Locale.US, clock, "now-08:01"));
        assertEquals(date, Dates.parse(Locale.US, clock, "now-08:01"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "now-08:01"));

        date = LocalDateTime.parse("21/01/2000-10:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        clock = Clock.fixed(date.toInstant(), ZoneId.of("Asia/Tokyo"));
        Clock[] finalClock = {clock};
        assertEquals(date, Dates.parse(Locale.US, clock, "01/21/2000-10:00-JT"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.US, finalClock[0], "01-21-2000-10:00-JT"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.US, finalClock[0], "01 21 2000-10:00-JT"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.US, finalClock[0], "01.21.2000-10:00-JT"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/21/2000-10:00-JT"));
        assertEquals(date, Dates.parse(Locale.US, clock, "01/21/2000-10:00-Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/21/2000-10:00-Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "21/01/2000-10:00-Asia/Tokyo"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, finalClock[0], "21/1/2000-10:00-Asia/Tokyo"));

        date = LocalDateTime.parse("01/11/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Europe/Paris"));
        finalClock[0] = clock = Clock.fixed(date.toInstant(), ZoneId.of("Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.US, clock, "11/01/2000-00:00-CET"));
        assertEquals(date, Dates.parse(Locale.US, clock, "11/1/2000-00:00-CET"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "01/11/2000-00:00-CET"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, finalClock[0], "1/11/2000-00:00-CET"));
        assertEquals(date, Dates.parse(Locale.US, clock, "11/01/2000-00:00-Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.US, clock, "11/1/2000-00:00-Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "01/11/2000-00:00-Europe/Paris"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, finalClock[0], "1/11/2000-00:00-Europe/Paris"));

        date = LocalDateTime.parse("21/07/2034-07:38", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        finalClock[0] = clock = Clock.fixed(date.toInstant(), UTC);
        assertEquals(date, Dates.parse(Locale.US, clock, "07/21/2034-07:38-UTC"));
        assertEquals(date, Dates.parse(Locale.US, clock, "7/21/2034-07:38-UTC"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "21/07/2034-07:38-UTC"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, finalClock[0], "21/7/2034-07:38-UTC"));
        assertEquals(date, Dates.parse(Locale.US, clock, "07/21/2034-07:38"));
        assertEquals(date, Dates.parse(Locale.US, clock, "7/21/2034-07:38"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "21/07/2034-07:38"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, finalClock[0], "21/7/2034-07:38"));

        date = LocalDateTime.parse("01/01/1801-00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.ofHours(2));
        finalClock[0] = clock = Clock.fixed(date.toInstant(), ZoneOffset.ofHours(2));
        assertEquals(date, Dates.parse(Locale.US, clock, "01/01/1801-00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.US, clock, "01/1/1801-00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/01/1801-00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/1/1801-00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "01/01/1801-00:00+02:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, finalClock[0], "1/01/1801-00:00+02:00"));

        date = LocalDateTime.parse("01/01/0073-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Z"));
        finalClock[0] = clock = Clock.fixed(date.toInstant(), ZoneOffset.ofHours(2));
        assertEquals(date, Dates.parse(Locale.US, clock, "01/01/0073-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, clock, "01/01/73-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, clock, "01/1/0073-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/01/0073-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/1/0073-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/1/73-00:00Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "01/01/0073-00:00Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "01/01/73-00:00Z"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, finalClock[0], "1/01/0073-00:00Z"));

        date = LocalDateTime.parse("13/01/0003-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Z"));
        finalClock[0] = clock = Clock.fixed(date.toInstant(), ZoneOffset.ofHours(2));
        assertEquals(date, Dates.parse(Locale.US, clock, "01/13/0003-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, clock, "01/13/03-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, clock, "01/13/3-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/13/0003-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/13/03-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/13/3-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "13/01/0003-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "13/01/03-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "13/01/3-00:00-Z"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, finalClock[0], "13/1/0003-00:00-Z"));

        date = LocalDateTime.parse("17/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.ofHoursMinutes(-2, -10));
        finalClock[0] = clock = Clock.fixed(date.toInstant(), ZoneOffset.ofHoursMinutes(-2, -10));
        assertEquals(date, Dates.parse(Locale.US, clock, "01/17/2000-00:00-02:10"));
        assertEquals(date, Dates.parse(Locale.US, clock, "1/17/2000-00:00-02:10"));
        assertEquals(date, Dates.parse(Locale.FRENCH, clock, "17/01/2000-00:00-02:10"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, finalClock[0], "17/1/2000-00:00-02:10"));

        // test other locales formatting
        date = LocalDateTime.parse("17/01/2011-00:00", DATE_TIME_FORMATTER).atZone(UTC);
        finalClock[0] = clock = Clock.fixed(date.toInstant(), UTC);
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, finalClock[0], "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), clock, "2011-01-17-00:00-UTC"));
        assertEquals(date, Dates.parse(Locale.CANADA, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Danish"), clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Spanish"), clock, "2011-01-17-00:00"));
    }

    @Test
    void formatUTC() {
        assertThrows(NullPointerException.class, () -> Dates.formatUTC(Locale.FRENCH, null));
        assertThrows(NullPointerException.class, () -> Dates.formatUTC(null, ZonedDateTime.now()));
        ZonedDateTime date = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        assertNotEquals("01/01/2000-00:00", Dates.formatUTC(Locale.US, date));
        assertNotEquals("01/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, date));
        assertEquals("12/31/1999-15:00", Dates.formatUTC(Locale.US, date));
        assertEquals("31/12/1999-15:00", Dates.formatUTC(Locale.FRENCH, date));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals("1/1/2000-00:00", Dates.formatUTC(Locale.US, utcDate));
        assertEquals("01/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, utcDate));
        utcDate = LocalDateTime.parse("28/01/2000-00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals("1/28/2000-00:00", Dates.formatUTC(Locale.US, utcDate));
        assertEquals("28/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, utcDate));
        utcDate = LocalDateTime.parse("09/01/2000-00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals("1/9/2000-00:00", Dates.formatUTC(Locale.US, utcDate));
        assertEquals("09/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, utcDate));
    }

    @Test
    void formatDiscord() {
        ZonedDateTime date = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        long epoch = date.toEpochSecond();
        assertEquals("<t:" + epoch + ":d>-<t:" + epoch + ":t>", Dates.formatDiscord(date));
        date = LocalDateTime.now().atZone(ZoneId.of("Asia/Tokyo"));
        epoch = date.toEpochSecond();
        assertEquals("<t:" + epoch + ":d>-<t:" + epoch + ":t>", Dates.formatDiscord(date));
        date = LocalDateTime.now().atZone(UTC);
        epoch = date.toEpochSecond();
        assertEquals("<t:" + epoch + ":d>-<t:" + epoch + ":t>", Dates.formatDiscord(date));
    }

    @Test
    void formatDiscordRelative() {
        ZonedDateTime date = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        long epoch = date.toEpochSecond();
        assertEquals("<t:" + epoch + ":R>", Dates.formatDiscordRelative(date));
        date = LocalDateTime.now().atZone(ZoneId.of("Asia/Tokyo"));
        epoch = date.toEpochSecond();
        assertEquals("<t:" + epoch + ":R>", Dates.formatDiscordRelative(date));
        date = LocalDateTime.now().atZone(UTC);
        epoch = date.toEpochSecond();
        assertEquals("<t:" + epoch + ":R>", Dates.formatDiscordRelative(date));
    }

    @Test
    void nowUtcTest() {
        assertThrows(NullPointerException.class, () -> Dates.nowUtc(null));
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime nowUtc = Dates.nowUtc(Clock.systemUTC());
        assertTrue(nowUtc.isAfter(now.minusSeconds(1L)));
        now = ZonedDateTime.now(ZoneId.of("UTC"));
        assertTrue(nowUtc.isBefore(now.plusSeconds(1L)));
    }

    @Test
    void parseUtcDateTime() {
        assertTrue(Dates.parseUtcDateTime(null).isEmpty());
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        Timestamp timestamp = new Timestamp(utcDate.toInstant().toEpochMilli());
        assertTrue(Dates.parseUtcDateTime(timestamp).isPresent());
        assertEquals(utcDate, Dates.parseUtcDateTime(timestamp).get());

        utcDate = nowUtc().truncatedTo(ChronoUnit.MILLIS);
        timestamp = new Timestamp(utcDate.toInstant().toEpochMilli());
        assertEquals(Optional.of(utcDate), Dates.parseUtcDateTime(timestamp));
    }

    @Test
    void parseUtcDateTimeOrNull() {
        assertNull(Dates.parseUtcDateTimeOrNull(null));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        Timestamp timestamp = new Timestamp(utcDate.toInstant().toEpochMilli());
        assertEquals(utcDate, Dates.parseUtcDateTimeOrNull(timestamp));

        utcDate = nowUtc().truncatedTo(ChronoUnit.MILLIS);
        timestamp = new Timestamp(utcDate.toInstant().toEpochMilli());
        assertEquals(utcDate, Dates.parseUtcDateTimeOrNull(timestamp));
    }
}