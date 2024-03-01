package org.sbot.utils;

import net.dv8tion.jda.api.interactions.DiscordLocale;
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
        assertNotNull(DATE_TIME_FORMATTER.parse("01/01/2000 00:00"));
        assertThrows(DateTimeParseException.class, () -> DATE_TIME_FORMATTER.parse("01012000 00:00"));
    }

    @Test
    void parseLocalDateTime() {
        assertThrows(NullPointerException.class, () -> Dates.parseLocalDateTime(Locale.US, null));
        assertThrows(NullPointerException.class, () -> Dates.parseLocalDateTime(null, "01/01/2000-00:00"));
        assertEquals(LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER), Dates.parseLocalDateTime(Locale.US, "01/01/2000-00:00"));
        assertEquals(LocalDateTime.parse("21/01/2000 00:00", DATE_TIME_FORMATTER), Dates.parseLocalDateTime(Locale.US, "01/21/2000-00:00"));
        assertEquals(LocalDateTime.parse("21/01/2000 00:00", DATE_TIME_FORMATTER), Dates.parseLocalDateTime(Locale.FRANCE, "21/01/2000-00:00"));

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
        assertThrows(NullPointerException.class, () -> Dates.parse(Locale.US, Dates.UTC, Clock.systemUTC(), null));
        assertThrows(NullPointerException.class, () -> Dates.parse(Locale.US, Dates.UTC, null, "now"));
        assertThrows(NullPointerException.class, () -> Dates.parse(Locale.US, Dates.UTC, null, "notnow"));
        assertThrows(NullPointerException.class, () -> Dates.parse(null, Dates.UTC, Clock.systemUTC(), "now"));

        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Dates.UTC, Clock.systemUTC(), "now-"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Dates.UTC, Clock.systemUTC(), "now+"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Dates.UTC, Clock.systemUTC(), "now--"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Dates.UTC, Clock.systemUTC(), "nowUTC-"));
        assertDoesNotThrow(() -> Dates.parse(Locale.US, Dates.UTC, Clock.systemUTC(), "01/21/2000-10:00-Asia/Tokyo"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Dates.UTC, Clock.systemUTC(), "-01/21/2000-10:00-Asia/Tokyo"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Dates.UTC, Clock.systemUTC(), "01/21/200010:00-Asia/Tokyo"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Dates.UTC, Clock.systemUTC(), "01/21/200010:00-Asia/To-kyo"));
        assertThrows(DateTimeException.class, () -> Dates.parse(Locale.US, Dates.UTC, Clock.systemUTC(), "01/21/200010:00-Asia/Tokyo-"));

        // test now
        LocalDateTime now = LocalDateTime.now();
        ZonedDateTime date = now.atZone(UTC);
        Clock clock = Clock.fixed(date.toInstant(), UTC);
        assertEquals(date, Dates.parse(Locale.US, null, clock, "now"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "now"));
        assertEquals(date, Dates.parse(Locale.US, UTC, clock, "now"));
        assertNotEquals(date, Dates.parse(Locale.US, ZoneId.of("GMT"), clock, "now"));
        assertEquals(date.withZoneSameInstant(ZoneId.of("GMT")), Dates.parse(Locale.US, ZoneId.of("GMT"), clock, "now"));
        assertEquals(date.withZoneSameInstant(ZoneId.of("Asia/Tokyo")), Dates.parse(Locale.US, ZoneId.of("Asia/Tokyo"), clock, "now"));
        assertEquals(date, Dates.parse(Locale.US, UTC, clock, "NOW"));
        assertEquals(date, Dates.parse(Locale.US, UTC, clock, "Now"));
        assertEquals(date, Dates.parse(Locale.FRENCH, UTC, clock, "noW"));
        assertEquals(date, Dates.parse(Locale.FRENCH, UTC, clock, "nOw"));
        assertEquals(date, Dates.parse(Locale.FRENCH, UTC, clock, "nOW    "));
        assertEquals(date, Dates.parse(Locale.FRENCH, UTC, clock, "NOw \t  "));
        assertEquals(date.withZoneSameInstant(ZoneId.of("GMT")), Dates.parse(Locale.US, ZoneId.of("GMT"), clock, "now"));
        date = date.withZoneSameInstant(ZoneId.of("Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.US, ZoneId.of("JST", ZoneId.SHORT_IDS), clock, "now"));
        assertEquals(date, Dates.parse(Locale.US, ZoneId.of("Asia/Tokyo"), clock, "now"));
        date = date.withZoneSameInstant(ZoneId.of("CET"));
        assertEquals(date, Dates.parse(Locale.US, ZoneId.of("CET"), clock, "now"));
        assertEquals(date, Dates.parse(Locale.FRENCH, ZoneId.of("CET"), clock, "now"));
        date = date.withZoneSameInstant(ZoneId.of("Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.US, ZoneId.of("Europe/Paris"), clock, "now"));
        assertEquals(date, Dates.parse(Locale.FRENCH, ZoneId.of("Europe/Paris"), clock, "now"));
        date = date.withZoneSameInstant(ZoneId.of("+12:33"));
        assertEquals(date, Dates.parse(Locale.US, ZoneId.of("+12:33"), clock, "  now"));
        assertEquals(date, Dates.parse(Locale.FRENCH, ZoneId.of("+12:33"), clock, "now  "));
        date = date.withZoneSameInstant(ZoneId.of("-08:01"));
        assertEquals(date, Dates.parse(Locale.US, ZoneId.of("-08:01"), clock, " now "));
        assertEquals(date, Dates.parse(Locale.US, ZoneId.of("-08:01"), clock, "\tnow\t"));

        // test now with delta
        date = now.atZone(UTC);
        assertEquals(date.plusHours(1L), Dates.parse(Locale.US, null, clock, "now+1"));
        assertEquals(date.minusHours(1L), Dates.parse(Locale.US, null, clock, "now-1"));
        assertEquals(date.withZoneSameInstant(ZoneId.of("Asia/Tokyo")).plusMinutes(30L), Dates.parse(Locale.US, ZoneId.of("Asia/Tokyo"), clock, "now+0.5"));
        assertEquals(date.minusMinutes(15L), Dates.parse(Locale.US, null, clock, "now-0,25"));
        assertEquals(date.minusMinutes((1234*60) + 45), Dates.parse(Locale.US, null, clock, "now-1234.75"));
        assertEquals(date.minusMinutes((1234*60) + 45), Dates.parse(Locale.US, null, clock, "now-1234,75"));

        // test no timezone
        date = LocalDateTime.parse("21/05/1999 19:13", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        assertNotEquals(date, Dates.parse(Locale.UK, UTC, clock, "21/05/1999-19:13"));
        assertNotEquals(date, Dates.parse(Locale.UK, null, clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999  19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 \t 19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 \r 19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 \n 19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 \n  \t 19:13"));
        date = LocalDateTime.parse("21/05/1999 19:13", DATE_TIME_FORMATTER).atZone(UTC);
        assertEquals(date, Dates.parse(Locale.UK, null, clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parse(Locale.UK, UTC, clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parse(Locale.UK, UTC, clock, "21/05/1999 19:13"));
        assertEquals(date, Dates.parse(Locale.UK, UTC, clock, "21/05/1999 \t19:13"));
        assertNotEquals(date, Dates.parse(Locale.UK, ZoneId.of("GMT"), clock, "21/05/1999-19:13"));
        date = LocalDateTime.parse("21/05/1999 19:13", DATE_TIME_FORMATTER).atZone(ZoneId.of("GMT"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("GMT"), clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("GMT"), clock, "21/05/1999 19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("GMT"), clock, "21/05/1999\n19:13"));
        assertNotEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999-19:13"));

        // test dates time with timezone
        date = LocalDateTime.parse("21/01/2000 10:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000-10:00-JT"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000-10:00 JT"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000 10:00 JT"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000 10:00   JT"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000 10:00-JT"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.US, null, clock, "01-21-2000-10:00-JT"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.US, null, clock, "01 21 2000-10:00-JT"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.US, null, clock, "01.21.2000-10:00-JT"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/21/2000-10:00-JT"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/21/2000 10:00 JT"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/21/2000 10:00   \t JT"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000-10:00-Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000-10:00  Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000 10:00-Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000 10:00\n Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/21/2000-10:00-Asia/Tokyo"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "21/01/2000-10:00-Asia/Tokyo"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "21/1/2000-10:00-Asia/Tokyo"));

        date = LocalDateTime.parse("01/11/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/01/2000-00:00-CET"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/01/2000-00:00 CET"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/01/2000 00:00 CET"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/01/2000  00:00 CET"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/01/2000-00:00   CET"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/01/2000-00:00  \n CET"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/1/2000-00:00-CET"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/1/2000 00:00-CET"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/11/2000-00:00-CET"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/11/2000 00:00 CET"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "1/11/2000-00:00-CET"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/01/2000-00:00-Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/01/2000-00:00\nEurope/Paris"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/1/2000-00:00-Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/1/2000 00:00-Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/1/2000\t  00:00-Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "11/1/2000 00:00  Europe/Paris"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/11/2000-00:00-Europe/Paris"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "1/11/2000-00:00-Europe/Paris"));

        date = LocalDateTime.parse("21/07/2034 07:38", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals(date, Dates.parse(Locale.US, null, clock, "07/21/2034-07:38-UTC"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "07/21/2034 07:38-UTC"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "07/21/2034  07:38-UTC"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "07/21/2034-07:38 UTC"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "07/21/2034  07:38  UTC"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "7/21/2034-07:38-UTC"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "21/07/2034-07:38-UTC"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "21/7/2034-07:38-UTC"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "07/21/2034-07:38"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "07/21/2034 07:38"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "07/21/2034\r07:38"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "7/21/2034-07:38"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "7/21/2034 07:38"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "7/21/2034 \t\n\t\t\n07:38"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "21/07/2034-07:38"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "21/07/2034 07:38"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "21/07/2034   07:38"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "21/7/2034-07:38"));

        date = LocalDateTime.parse("01/01/1801 00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.ofHours(2));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/1801-00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/1801 00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/1801-00:00 +02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/1801-00:00  +02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/1801-00:00\t+02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/1801 00:00 +02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/1801 00:00   \t+02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/1801\n00:00   \t+02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/1/1801-00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/01/1801-00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/1801-00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/1801 00:00 +02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/1801 00:00  +02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/1801   00:00  +02:00"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/1801-00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/1801 00:00 +02:00"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/1801\t00:00 +02:00"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/1801\t00:00\t\t\t+02:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "1/01/1801-00:00+02:00"));

        date = LocalDateTime.parse("01/01/0073 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/0073-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/0073-00:00 Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/0073 00:00 Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/0073\t00:00 Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/73-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/1/0073-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/01/0073-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/0073-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/73-00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/73 00:00Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/73-00:00 Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/73-00:00\tZ"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/73-00:00\t\tZ"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/73 00:00 Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/73  00:00 Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/1/73 \t00:00  Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/0073-00:00Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/73-00:00Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/73-00:00 Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/73-00:00\tZ"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/73 00:00 Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/73   00:00 Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "01/01/73   00:00  \n Z"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "1/01/0073-00:00Z"));

        date = LocalDateTime.parse("13/01/0003 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/13/0003-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/13/0003 00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/13/03-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/13/03 00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/13/03\t00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/13/3-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/13/0003-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/13/03-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/13/3-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/13/3 00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/13/3\t\t00:00-Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/13/3 00:00 Z"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/13/3   00:00 Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/0003-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/03-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3-00:00-Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3 00:00-Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3  00:00-Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3 00:00 Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3\t00:00\tZ"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3 00:00  Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3  00:00  Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3-00:00 Z"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3-00:00\nZ"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3-00:00  \nZ"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "13/01/3-00:00\n  Z"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "13/1/0003-00:00-Z"));

        date = LocalDateTime.parse("17/01/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.ofHoursMinutes(-2, -10));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/17/2000-00:00-02:10"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/17/2000 00:00 -02:10"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/17/2000 00:00  -02:10"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/17/2000 00:00\t-02:10"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/17/2000\t00:00  -02:10"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/17/2000  00:00  -02:10"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/17/2000-00:00-02:10"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/17/2000 00:00 -02:10"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/17/2000   00:00 -02:10"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/17/2000 00:00        -02:10"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "17/01/2000-00:00-02:10"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "17/01/2000 00:00 -02:10"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "17/01/2000 00:00\t-02:10"));
        assertEquals(date, Dates.parse(Locale.FRENCH, null, clock, "17/01/2000\t00:00\t-02:10"));
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "17/1/2000-00:00-02:10"));

        // test other locales formatting
        date = LocalDateTime.parse("17/01/2011 00:00", DATE_TIME_FORMATTER).atZone(UTC);
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17 00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17   00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\t00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\n00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\r00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\t  00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17-00:00-UTC"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17 00:00-UTC"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17  00:00-UTC"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17    00:00-UTC"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\r00:00-UTC"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17-00:00 UTC"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17 00:00 UTC"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\t00:00 UTC"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\n 00:00 UTC"));
        assertEquals(date, Dates.parse(Locale.CANADA, null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.CANADA, null, clock, "2011-01-17 00:00"));
        assertEquals(date, Dates.parse(Locale.CANADA, null, clock, "2011-01-17\t \t00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Danish"), null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Danish"), null, clock, "2011-01-17 00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Danish"), null, clock, "2011-01-17\t00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Spanish"), null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Spanish"), null, clock, "2011-01-17 00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Spanish"), null, clock, "2011-01-17   00:00"));
    }

    @Test
    void formatUTC() {
        assertThrows(NullPointerException.class, () -> Dates.formatUTC(Locale.FRENCH, null));
        assertThrows(NullPointerException.class, () -> Dates.formatUTC(null, ZonedDateTime.now()));
        ZonedDateTime date = LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        assertNotEquals("01/01/2000-00:00", Dates.formatUTC(Locale.US, date));
        assertNotEquals("01/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, date));
        assertEquals("12/31/1999-15:00", Dates.formatUTC(Locale.US, date));
        assertEquals("31/12/1999-15:00", Dates.formatUTC(Locale.FRENCH, date));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals("1/1/2000-00:00", Dates.formatUTC(Locale.US, utcDate));
        assertEquals("01/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, utcDate));
        utcDate = LocalDateTime.parse("28/01/2000 00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals("1/28/2000-00:00", Dates.formatUTC(Locale.US, utcDate));
        assertEquals("28/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, utcDate));
        utcDate = LocalDateTime.parse("09/01/2000 00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals("1/9/2000-00:00", Dates.formatUTC(Locale.US, utcDate));
        assertEquals("09/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, utcDate));
    }

    @Test
    void formatDiscord() {
        ZonedDateTime date = LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        long epoch = date.toEpochSecond();
        assertEquals("<t:" + epoch + ":d> <t:" + epoch + ":t>", Dates.formatDiscord(date));
        date = LocalDateTime.now().atZone(ZoneId.of("Asia/Tokyo"));
        epoch = date.toEpochSecond();
        assertEquals("<t:" + epoch + ":d> <t:" + epoch + ":t>", Dates.formatDiscord(date));
        date = LocalDateTime.now().atZone(UTC);
        epoch = date.toEpochSecond();
        assertEquals("<t:" + epoch + ":d> <t:" + epoch + ":t>", Dates.formatDiscord(date));
    }

    @Test
    void formatDiscordRelative() {
        ZonedDateTime date = LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
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
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
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
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        Timestamp timestamp = new Timestamp(utcDate.toInstant().toEpochMilli());
        assertEquals(utcDate, Dates.parseUtcDateTimeOrNull(timestamp));

        utcDate = nowUtc().truncatedTo(ChronoUnit.MILLIS);
        timestamp = new Timestamp(utcDate.toInstant().toEpochMilli());
        assertEquals(utcDate, Dates.parseUtcDateTimeOrNull(timestamp));
    }

    @Test
    void localePatterns() {
        assertEquals("dd/MM/yyyy HH:mm (optional) zone", Dates.LocalePatterns.get(DiscordLocale.FRENCH.toLocale()));
        assertEquals("dd/MM/yyyy HH:mm (optional) zone", Dates.LocalePatterns.get(DiscordLocale.ENGLISH_UK.toLocale()));
        assertEquals("M/d/yyyy HH:mm (optional) zone", Dates.LocalePatterns.get(DiscordLocale.ENGLISH_US.toLocale()));
        assertEquals("yyyy/M/d HH:mm (optional) zone", Dates.LocalePatterns.get(DiscordLocale.JAPANESE.toLocale()));
        assertEquals("dd. MM. yyyy. HH:mm (optional) zone", Dates.LocalePatterns.get(DiscordLocale.CROATIAN.toLocale()));
        assertEquals("dd/MM/yyyy HH:mm (optional) zone", Dates.LocalePatterns.get(DiscordLocale.PORTUGUESE_BRAZILIAN.toLocale()));
        assertEquals("d-M-yyyy HH:mm (optional) zone", Dates.LocalePatterns.get(DiscordLocale.DUTCH.toLocale()));
        assertEquals("dd.MM.yyyy HH:mm (optional) zone", Dates.LocalePatterns.get(DiscordLocale.TURKISH.toLocale()));
        assertEquals("d.MM.yyyy HH:mm (optional) zone", Dates.LocalePatterns.get(DiscordLocale.POLISH.toLocale()));
    }
}