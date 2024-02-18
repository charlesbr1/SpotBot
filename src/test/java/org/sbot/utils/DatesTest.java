package org.sbot.utils;

import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

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
    void parseLocal() {
        assertThrows(NullPointerException.class, () -> Dates.parseLocal(null));
        assertEquals(LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER), Dates.parseLocal("01/01/2000-00:00"));

        assertThrows(DateTimeParseException.class, () -> Dates.parseLocal("41/01/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocal("01/20/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocal("01/01/2000-25:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocal("01/01/2000-00:61"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocal("01012000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocal("01/012000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocal("0101/2000-00:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocal("01/01/200000:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocal("01/01/2000-0000"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseLocal("01/01/2000-00.00"));
    }

    @Test
    void parse() {
        assertThrows(NullPointerException.class, () -> Dates.parse(null));
        assertThrows(DateTimeException.class, () -> Dates.parse("now-"));
        assertThrows(DateTimeException.class, () -> Dates.parse("now--"));
        assertThrows(DateTimeException.class, () -> Dates.parse("nowUTC-"));

        ZonedDateTime date = Instant.now().atZone(UTC);
        ZonedDateTime dateBefore = date.minusMinutes(1L).minusSeconds(1L);
        ZonedDateTime dateAfter = date.plusMinutes(1L).plusSeconds(1L);
        assertTrue(Dates.parse("now").isBefore(dateAfter));
        assertTrue(Dates.parse("now").isAfter(dateBefore));
        assertTrue(Dates.parse("now-UTC").isBefore(dateAfter));
        assertTrue(Dates.parse("now-UTC").isAfter(dateBefore));
        date = Instant.now().atZone(ZoneId.of("Asia/Tokyo"));
        dateBefore = date.minusMinutes(1L).minusSeconds(1L);
        dateAfter = date.plusMinutes(1L).plusSeconds(1L);
        assertTrue(Dates.parse("now-JST").isBefore(dateAfter));
        assertTrue(Dates.parse("now-JST").isAfter(dateBefore));
        assertTrue(Dates.parse("now-Asia/Tokyo").isBefore(dateAfter));
        assertTrue(Dates.parse("now-Asia/Tokyo").isAfter(dateBefore));
        date = Instant.now().atZone(ZoneId.of("CET"));
        dateBefore = date.minusMinutes(1L).minusSeconds(1L);
        dateAfter = date.plusMinutes(1L).plusSeconds(1L);
        assertTrue(Dates.parse("now-CET").isBefore(dateAfter));
        assertTrue(Dates.parse("now-CET").isAfter(dateBefore));
        assertTrue(Dates.parse("now-Europe/Paris").isBefore(dateAfter));
        assertTrue(Dates.parse("now-Europe/Paris").isAfter(dateBefore));
        date = Instant.now().atZone(ZoneId.of("+12:33"));
        dateBefore = date.minusMinutes(1L).minusSeconds(1L);
        dateAfter = date.plusMinutes(1L).plusSeconds(1L);
        assertTrue(Dates.parse("now-+12:33").isBefore(dateAfter));
        assertTrue(Dates.parse("now+12:33").isBefore(dateAfter));
        assertTrue(Dates.parse("now+12:33").isAfter(dateBefore));
        date = Instant.now().atZone(ZoneId.of("-08:01"));
        dateBefore = date.minusMinutes(1L).minusSeconds(1L);
        dateAfter = date.plusMinutes(1L).plusSeconds(1L);
        assertTrue(Dates.parse("now--08:01").isBefore(dateAfter));
        assertTrue(Dates.parse("now-08:01").isBefore(dateAfter));
        assertTrue(Dates.parse("now-08:01").isAfter(dateBefore));

        date = LocalDateTime.parse("11/01/2000-10:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        assertEquals(date, Dates.parse("11/01/2000-10:00-JT"));
        assertEquals(date, Dates.parse("11/01/2000-10:00-Asia/Tokyo"));
        date = LocalDateTime.parse("01/11/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Europe/Paris"));
        assertEquals(date, Dates.parse("01/11/2000-00:00-CET"));
        assertEquals(date, Dates.parse("01/11/2000-00:00-Europe/Paris"));
        date = LocalDateTime.parse("21/07/2034-07:38", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals(date, Dates.parse("21/07/2034-07:38-UTC"));
        assertEquals(date, Dates.parse("21/07/2034-07:38-UTC"));
        assertEquals(date, Dates.parse("21/07/2034-07:38"));
        date = LocalDateTime.parse("01/01/1801-00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.ofHours(2));
        assertEquals(date, Dates.parse("01/01/1801-00:00+02:00"));
        date = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.ofHoursMinutes(-2, -10));
        assertEquals(date, Dates.parse("01/01/2000-00:00-02:10"));
    }

    @Test
    void parseUTC() {
        assertThrows(NullPointerException.class, () -> Dates.parseUTC(null));

        ZonedDateTime date = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertTrue(date.compareTo(utcDate) != 0);
        assertTrue(Duration.between(date, utcDate).toHours() != 0);
        assertNotEquals(date, Dates.parseUTC("01/01/2000-00:00"));
        assertEquals(date, Dates.parseUTC("31/12/1999-15:00").withZoneSameInstant(ZoneId.of("Asia/Tokyo")));
        assertEquals(utcDate, Dates.parseUTC("01/01/2000-00:00"));
    }

    @Test
    void format() {
        assertThrows(NullPointerException.class, () -> Dates.format(null));
        ZonedDateTime date = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals("01/01/2000-00:00", Dates.format(date));
        assertEquals("01/01/2000-00:00", Dates.format(utcDate));
    }

    @Test
    void formatUTC() {
        assertThrows(NullPointerException.class, () -> Dates.formatUTC(null));
        ZonedDateTime date = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000-00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertNotEquals("01/01/2000-00:00", Dates.formatUTC(date));
        assertEquals("31/12/1999-15:00", Dates.formatUTC(date));
        assertEquals("01/01/2000-00:00", Dates.formatUTC(utcDate));
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

    @Test
    void nowUtcTest() {
        assertThrows(NullPointerException.class, () -> Dates.nowUtc(null));
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        ZonedDateTime nowUtc = Dates.nowUtc(Clock.systemUTC());
        assertTrue(nowUtc.isAfter(now.minusSeconds(1L)));
        now = ZonedDateTime.now(ZoneId.of("UTC"));
        assertTrue(nowUtc.isBefore(now.plusSeconds(1L)));
    }
}