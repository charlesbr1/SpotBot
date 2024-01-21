package org.sbot.utils;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.utils.Dates.DATE_TIME_FORMATTER;

class DatesTest {

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
        Timestamp timestamp = new Timestamp(utcDate.toEpochSecond() * 1000L);
        assertEquals(utcDate, Dates.parseUtcDateTimeOrNull(timestamp));

        utcDate = ZonedDateTime.now().withZoneSameInstant(ZoneOffset.UTC).withSecond(0).withNano(0);
        timestamp = new Timestamp(utcDate.toEpochSecond() * 1000L);
        assertEquals(utcDate, Dates.parseUtcDateTimeOrNull(timestamp));
    }

    @Test
    void daysHoursMinutesSince() {
        //TODO refactor
    }
}