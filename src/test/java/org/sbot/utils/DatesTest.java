package org.sbot.utils;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.utils.Dates.DATE_TIME_FORMATTER;

public class DatesTest {

    public static ZonedDateTime nowUtc() {
        return Instant.now().atZone(ZoneOffset.UTC);
    }

    @Test
    void DATE_TIME_FORMATTER() {
        assertNotNull(DATE_TIME_FORMATTER.parse("01/01/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> DATE_TIME_FORMATTER.parse("01012000-00:00"));
    }

    @Test
    void parse() {
        assertEquals(LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER), Dates.parse("01/01/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse("41/01/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse("01/20/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse("01/01/2000-25:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse("01/01/2000-00:61"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse("01012000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse("01/012000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse("0101/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse("01/01/200000:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse("01/01/2000-0000"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse("01/01/2000-00.00"));
    }

    @Test
    void parseUTC() {
        ZonedDateTime date = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.UTC);
        assertTrue(date.compareTo(utcDate) != 0);
        assertTrue(Duration.between(date, utcDate).toHours() != 0);
        assertNotEquals(date, Dates.parseUTC("01/01/2000-00:00"));
        assertEquals(date, Dates.parseUTC("31/12/1999-15:00").withZoneSameInstant(ZoneId.of("Asia/Tokyo")));
        assertEquals(utcDate, Dates.parseUTC("01/01/2000-00:00"));
    }

    @Test
    void format() {
        ZonedDateTime date = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.UTC);
        assertEquals("01/01/2000-00:00", Dates.format(date));
        assertEquals("01/01/2000-00:00", Dates.format(utcDate));
    }

    @Test
    void formatUTC() {
        ZonedDateTime date = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.UTC);
        assertNotEquals("01/01/2000-00:00", Dates.formatUTC(date));
        assertEquals("31/12/1999-15:00", Dates.formatUTC(date));
        assertEquals("01/01/2000-00:00", Dates.formatUTC(utcDate));
    }

    @Test
    void parseUtcDateTimeOrNull() {
        assertNull(Dates.parseUtcDateTimeOrNull(null));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.UTC);
        Timestamp timestamp = new Timestamp(utcDate.toInstant().toEpochMilli());
        assertEquals(utcDate, Dates.parseUtcDateTimeOrNull(timestamp));

        utcDate = nowUtc().truncatedTo(ChronoUnit.MILLIS);
        timestamp = new Timestamp(utcDate.toInstant().toEpochMilli());
        assertEquals(utcDate, Dates.parseUtcDateTimeOrNull(timestamp));
    }

    @Test
    void nowUtcTest() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime nowUtc = Dates.nowUtc(Clock.systemUTC());
        assertTrue(nowUtc.isAfter(now.minusSeconds(1L)));
        now = ZonedDateTime.now(ZoneId.of("UTC"));
        assertTrue(nowUtc.isBefore(now.plusSeconds(1L)));
    }
}