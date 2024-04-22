package org.sbot.utils;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import static java.time.temporal.ChronoUnit.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.utils.Dates.DATE_TIME_FORMATTER;
import static org.sbot.utils.Dates.UTC;

public class DatesTest {

    public static long nowUtc() {
        return Instant.now().toEpochMilli();
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
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.US, Dates.UTC, null, "notnow"));
        assertThrows(NullPointerException.class, () -> Dates.parse(null, Dates.UTC, Clock.systemUTC(), "notnow"));
        assertDoesNotThrow(() -> Dates.parse(null, Dates.UTC, Clock.systemUTC(), "now"));

        long now = DatesTest.nowUtc();
        Clock clock = Clock.fixed(Instant.ofEpochMilli(now), UTC);
        assertEquals(now, Dates.parse(Locale.US, null, clock, "now"));
        assertEquals(now, Dates.parse(Locale.US, ZoneId.of("Asia/Tokyo"), clock, "now"));
        assertEquals(Dates.plusHours(now, 1L), Dates.parse(Locale.US, null, clock, "now +1"));
        assertEquals(Dates.minusMinutes(now, 15L), Dates.parse(Locale.US, null, clock, "now-0,25"));

        long date = LocalDateTime.parse("21/05/1999 19:13", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli();
        assertNotEquals(date, Dates.parse(Locale.UK, UTC, clock, "21/05/1999-19:13"));
        assertNotEquals(date, Dates.parse(Locale.UK, null, clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 19:13"));
        assertEquals(date, Dates.parse(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999  19:13"));

        date = LocalDateTime.parse("01/01/1801 00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.ofHours(2)).toInstant().toEpochMilli();
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/1801-00:00+02:00"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/1801 00:00+02:00"));

        date = LocalDateTime.parse("21/01/2000 10:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli();
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000-10:00-JT"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/21/2000-10:00 JT"));
        assertEquals(date, Dates.parse(Locale.US, null, clock, "1/21/2000-10:00-Asia/Tokyo"));

        date = LocalDateTime.parse("01/01/0073 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Z")).toInstant().toEpochMilli();
        assertEquals(date, Dates.parse(Locale.US, null, clock, "01/01/73-00:00Z"));

        date = LocalDateTime.parse("17/01/2011 00:00", DATE_TIME_FORMATTER).atZone(UTC).toInstant().toEpochMilli();
        assertThrows(DateTimeParseException.class, () -> Dates.parse(Locale.FRENCH, null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parse(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17 00:00"));
    }

    @Test
    void parseDateTime() {
        assertThrows(NullPointerException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), null));
        assertThrows(NullPointerException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, null, "now"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, null, "notnow"));
        assertThrows(NullPointerException.class, () -> Dates.parseDateTime(null, Dates.UTC, Clock.systemUTC(), "notnow"));
        assertDoesNotThrow(() -> Dates.parseDateTime(null, Dates.UTC, Clock.systemUTC(), "now"));

        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "now-"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "now+"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "now+lala"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "now+nan"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "now+NaN"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "now-NaN"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "now1"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "now 1"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "now--"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "nowUTC-"));
        assertDoesNotThrow(() -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "01/21/2000-10:00-Asia/Tokyo"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "-01/21/2000-10:00-Asia/Tokyo"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "01/21/200010:00-Asia/Tokyo"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "01/21/200010:00-Asia/To-kyo"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, Dates.UTC, Clock.systemUTC(), "01/21/200010:00-Asia/Tokyo-"));

        // test now
        LocalDateTime now = LocalDateTime.now();
        ZonedDateTime date = now.atZone(UTC);
        Clock clock = Clock.fixed(date.toInstant(), UTC);
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.US, UTC, clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.US, ZoneId.of("GMT"), clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.US, ZoneId.of("Asia/Tokyo"), clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.US, UTC, clock, "NOW"));
        assertEquals(date, Dates.parseDateTime(Locale.US, UTC, clock, "Now"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, UTC, clock, "noW"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, UTC, clock, "nOw"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, UTC, clock, "nOW    "));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, UTC, clock, "NOw \t  "));
        assertEquals(date, Dates.parseDateTime(Locale.US, ZoneId.of("JST", ZoneId.SHORT_IDS), clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.US, ZoneId.of("Asia/Tokyo"), clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.US, ZoneId.of("CET"), clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, ZoneId.of("CET"), clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.US, ZoneId.of("Europe/Paris"), clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, ZoneId.of("Europe/Paris"), clock, "now"));
        assertEquals(date, Dates.parseDateTime(Locale.US, ZoneId.of("+12:33"), clock, "  now"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, ZoneId.of("+12:33"), clock, "now  "));
        assertEquals(date, Dates.parseDateTime(Locale.US, ZoneId.of("-08:01"), clock, " now "));
        assertEquals(date, Dates.parseDateTime(Locale.US, ZoneId.of("-08:01"), clock, "\tnow\t"));

        // test now with delta
        date = now.atZone(UTC);
        assertEquals(date.plusHours(1L), Dates.parseDateTime(Locale.US, null, clock, "now+1"));
        assertEquals(date.plusHours(1L), Dates.parseDateTime(Locale.US, null, clock, "now +1"));
        assertEquals(date.plusHours(1L), Dates.parseDateTime(Locale.US, null, clock, "now\t+1"));
        assertEquals(date.plusHours(1L), Dates.parseDateTime(Locale.US, null, clock, "now\t   +1"  ));
        assertEquals(date.minusHours(1L), Dates.parseDateTime(Locale.US, null, clock, "now-1"));
        assertEquals(date.minusHours(1L), Dates.parseDateTime(Locale.US, null, clock, "now -1"));
        assertEquals(date.minusHours(1L), Dates.parseDateTime(Locale.US, null, clock, "now   \t\t-1"));
        assertEquals(date.plusMinutes(30L), Dates.parseDateTime(Locale.US, ZoneId.of("Asia/Tokyo"), clock, "now+0.5"));
        assertEquals(date.minusMinutes(15L), Dates.parseDateTime(Locale.US, null, clock, "now-0,25"));
        assertEquals(date.minusMinutes((1234*60) + 45), Dates.parseDateTime(Locale.US, null, clock, "now -1234.75"));
        assertEquals(date.minusMinutes((1234*60) + 45), Dates.parseDateTime(Locale.US, null, clock, "now-1234,75"));
        assertEquals(date.plusMinutes(52560000L), Dates.parseDateTime(Locale.US, null, clock, "now +876000"));
        assertEquals(date.minusMinutes(52560000L), Dates.parseDateTime(Locale.US, null, clock, "now -876000"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, null, clock, "now +876001"));
        assertThrows(DateTimeException.class, () -> Dates.parseDateTime(Locale.US, null, clock, "now -876001"));

        // test no timezone
        date = LocalDateTime.parse("21/05/1999 19:13", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        assertNotEquals(date, Dates.parseDateTime(Locale.UK, UTC, clock, "21/05/1999-19:13"));
        assertNotEquals(date, Dates.parseDateTime(Locale.UK, null, clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999  19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 \t 19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 \r 19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 \n 19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999 \n  \t 19:13"));
        date = LocalDateTime.parse("21/05/1999 19:13", DATE_TIME_FORMATTER).atZone(UTC);
        assertEquals(date, Dates.parseDateTime(Locale.UK, null, clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, UTC, clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, UTC, clock, "21/05/1999 19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, UTC, clock, "21/05/1999 \t19:13"));
        assertNotEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("GMT"), clock, "21/05/1999-19:13"));
        date = LocalDateTime.parse("21/05/1999 19:13", DATE_TIME_FORMATTER).atZone(ZoneId.of("GMT"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("GMT"), clock, "21/05/1999-19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("GMT"), clock, "21/05/1999 19:13"));
        assertEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("GMT"), clock, "21/05/1999\n19:13"));
        assertNotEquals(date, Dates.parseDateTime(Locale.UK, ZoneId.of("Asia/Tokyo"), clock, "21/05/1999-19:13"));

        // test dates time with timezone
        date = LocalDateTime.parse("21/01/2000 10:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/21/2000-10:00-JT"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/21/2000-10:00 JT"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/21/2000 10:00 JT"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/21/2000 10:00   JT"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/21/2000 10:00-JT"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.US, null, clock, "01-21-2000-10:00-JT"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.US, null, clock, "01 21 2000-10:00-JT"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.US, null, clock, "01.21.2000-10:00-JT"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/21/2000-10:00-JT"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/21/2000 10:00 JT"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/21/2000 10:00   \t JT"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/21/2000-10:00-Asia/Tokyo"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/21/2000-10:00  Asia/Tokyo"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/21/2000 10:00-Asia/Tokyo"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/21/2000 10:00\n Asia/Tokyo"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/21/2000-10:00-Asia/Tokyo"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "21/01/2000-10:00-Asia/Tokyo"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.FRENCH, null, clock, "21/1/2000-10:00-Asia/Tokyo"));

        date = LocalDateTime.parse("01/11/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Europe/Paris"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/01/2000-00:00-CET"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/01/2000-00:00 CET"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/01/2000 00:00 CET"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/01/2000  00:00 CET"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/01/2000-00:00   CET"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/01/2000-00:00  \n CET"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/1/2000-00:00-CET"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/1/2000 00:00-CET"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/11/2000-00:00-CET"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/11/2000 00:00 CET"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.FRENCH, null, clock, "1/11/2000-00:00-CET"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/01/2000-00:00-Europe/Paris"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/01/2000-00:00\nEurope/Paris"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/1/2000-00:00-Europe/Paris"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/1/2000 00:00-Europe/Paris"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/1/2000\t  00:00-Europe/Paris"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "11/1/2000 00:00  Europe/Paris"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/11/2000-00:00-Europe/Paris"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.FRENCH, null, clock, "1/11/2000-00:00-Europe/Paris"));

        date = LocalDateTime.parse("21/07/2034 07:38", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "07/21/2034-07:38-UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "07/21/2034 07:38-UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "07/21/2034  07:38-UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "07/21/2034-07:38 UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "07/21/2034  07:38  UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "7/21/2034-07:38-UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "21/07/2034-07:38-UTC"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.FRENCH, null, clock, "21/7/2034-07:38-UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "07/21/2034-07:38"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "07/21/2034 07:38"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "07/21/2034\r07:38"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "7/21/2034-07:38"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "7/21/2034 07:38"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "7/21/2034 \t\n\t\t\n07:38"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "21/07/2034-07:38"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "21/07/2034 07:38"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "21/07/2034   07:38"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.FRENCH, null, clock, "21/7/2034-07:38"));

        date = LocalDateTime.parse("01/01/1801 00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.ofHours(2));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/1801-00:00+02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/1801 00:00+02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/1801-00:00 +02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/1801-00:00  +02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/1801-00:00\t+02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/1801 00:00 +02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/1801 00:00   \t+02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/1801\n00:00   \t+02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/1/1801-00:00+02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/01/1801-00:00+02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/1801-00:00+02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/1801 00:00 +02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/1801 00:00  +02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/1801   00:00  +02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/1801-00:00+02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/1801 00:00 +02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/1801\t00:00 +02:00"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/1801\t00:00\t\t\t+02:00"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.FRENCH, null, clock, "1/01/1801-00:00+02:00"));

        date = LocalDateTime.parse("01/01/0073 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/0073-00:00Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/0073-00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/0073 00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/0073\t00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/01/73-00:00Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/1/0073-00:00Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/01/0073-00:00Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/0073-00:00Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/73-00:00Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/73 00:00Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/73-00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/73-00:00\tZ"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/73-00:00\t\tZ"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/73 00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/73  00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/1/73 \t00:00  Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/0073-00:00Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/73-00:00Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/73-00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/73-00:00\tZ"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/73 00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/73   00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "01/01/73   00:00  \n Z"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.FRENCH, null, clock, "1/01/0073-00:00Z"));

        date = LocalDateTime.parse("13/01/0003 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/13/0003-00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/13/0003 00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/13/03-00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/13/03 00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/13/03\t00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/13/3-00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/13/0003-00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/13/03-00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/13/3-00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/13/3 00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/13/3\t\t00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/13/3 00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/13/3   00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/0003-00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/03-00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3-00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3 00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3  00:00-Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3 00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3\t00:00\tZ"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3 00:00  Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3  00:00  Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3-00:00 Z"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3-00:00\nZ"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3-00:00  \nZ"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "13/01/3-00:00\n  Z"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.FRENCH, null, clock, "13/1/0003-00:00-Z"));

        date = LocalDateTime.parse("17/01/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneOffset.ofHoursMinutes(-2, -10));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/17/2000-00:00-02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/17/2000 00:00 -02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/17/2000 00:00  -02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/17/2000 00:00\t-02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/17/2000\t00:00  -02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "01/17/2000  00:00  -02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/17/2000-00:00-02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/17/2000 00:00 -02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/17/2000   00:00 -02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.US, null, clock, "1/17/2000 00:00        -02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "17/01/2000-00:00-02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "17/01/2000 00:00 -02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "17/01/2000 00:00\t-02:10"));
        assertEquals(date, Dates.parseDateTime(Locale.FRENCH, null, clock, "17/01/2000\t00:00\t-02:10"));
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.FRENCH, null, clock, "17/1/2000-00:00-02:10"));

        // test other locales formatting
        date = LocalDateTime.parse("17/01/2011 00:00", DATE_TIME_FORMATTER).atZone(UTC);
        assertThrows(DateTimeParseException.class, () -> Dates.parseDateTime(Locale.FRENCH, null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17 00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17   00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\t00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\n00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\r00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\t  00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17-00:00-UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17 00:00-UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17  00:00-UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17    00:00-UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\r00:00-UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17-00:00 UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17 00:00 UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\t00:00 UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Finnish"), null, clock, "2011-01-17\n 00:00 UTC"));
        assertEquals(date, Dates.parseDateTime(Locale.CANADA, null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.CANADA, null, clock, "2011-01-17 00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.CANADA, null, clock, "2011-01-17\t \t00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Danish"), null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Danish"), null, clock, "2011-01-17 00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Danish"), null, clock, "2011-01-17\t00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Spanish"), null, clock, "2011-01-17-00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Spanish"), null, clock, "2011-01-17 00:00"));
        assertEquals(date, Dates.parseDateTime(Locale.forLanguageTag("Spanish"), null, clock, "2011-01-17   00:00"));
    }

    @Test
    void format() {
        assertThrows(NullPointerException.class, () -> Dates.format(Locale.FRENCH, null));
        assertThrows(NullPointerException.class, () -> Dates.format(null, ZonedDateTime.now()));
        ZonedDateTime date = LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo"));
        assertEquals("1/1/2000-00:00", Dates.format(Locale.US, date));
        assertEquals("01/01/2000-00:00", Dates.format(Locale.FRENCH, date));
        ZonedDateTime utcDate = LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Europe/Paris"));
        assertEquals("1/1/2000-00:00", Dates.format(Locale.US, utcDate));
        assertEquals("01/01/2000-00:00", Dates.format(Locale.FRENCH, utcDate));
        utcDate = LocalDateTime.parse("28/01/2000 00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC);
        assertEquals("1/28/2000-00:00", Dates.format(Locale.US, utcDate));
        assertEquals("28/01/2000-00:00", Dates.format(Locale.FRENCH, utcDate));
        utcDate = LocalDateTime.parse("09/01/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("GMT"));
        assertEquals("1/9/2000-00:00", Dates.format(Locale.US, utcDate));
        assertEquals("09/01/2000-00:00", Dates.format(Locale.FRENCH, utcDate));
    }

    @Test
    void formatUTC() {
        assertThrows(NullPointerException.class, () -> Dates.formatUTC(null, nowUtc()));
        long date = LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER).atZone(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli();
        assertNotEquals("01/01/2000-00:00", Dates.formatUTC(Locale.US, date));
        assertNotEquals("01/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, date));
        assertEquals("12/31/1999-15:00", Dates.formatUTC(Locale.US, date));
        assertEquals("31/12/1999-15:00", Dates.formatUTC(Locale.FRENCH, date));
        long utcDate = LocalDateTime.parse("01/01/2000 00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC).toInstant().toEpochMilli();
        assertEquals("1/1/2000-00:00", Dates.formatUTC(Locale.US, utcDate));
        assertEquals("01/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, utcDate));
        utcDate = LocalDateTime.parse("28/01/2000 00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC).toInstant().toEpochMilli();
        assertEquals("1/28/2000-00:00", Dates.formatUTC(Locale.US, utcDate));
        assertEquals("28/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, utcDate));
        utcDate = LocalDateTime.parse("09/01/2000 00:00", DATE_TIME_FORMATTER).atZone(Dates.UTC).toInstant().toEpochMilli();
        assertEquals("1/9/2000-00:00", Dates.formatUTC(Locale.US, utcDate));
        assertEquals("09/01/2000-00:00", Dates.formatUTC(Locale.FRENCH, utcDate));
    }

    @Test
    void formatDiscord() {
        assertThrows(IllegalArgumentException.class, () -> Dates.formatDiscord(0L));
        assertThrows(IllegalArgumentException.class, () -> Dates.formatDiscord(-1L));
        long epoch = 1234000L;
        assertEquals("<t:" + epoch/1000 + ":d> <t:" + epoch/1000 + ":t>", Dates.formatDiscord(epoch));
        epoch = DatesTest.nowUtc();
        assertEquals("<t:" + epoch/1000 + ":d> <t:" + epoch/1000 + ":t>", Dates.formatDiscord(epoch));
    }

    @Test
    void formatDiscordRelative() {
        assertThrows(IllegalArgumentException.class, () -> Dates.formatDiscordRelative(0L));
        assertThrows(IllegalArgumentException.class, () -> Dates.formatDiscordRelative(-1L));
        long epoch = 1234000L;
        assertEquals("<t:" + epoch/1000 + ":R>", Dates.formatDiscordRelative(epoch));
        epoch = DatesTest.nowUtc();
        assertEquals("<t:" + epoch/1000 + ":R>", Dates.formatDiscordRelative(epoch));
    }

    @Test
    void nowEpochTest() {
        assertThrows(NullPointerException.class, () -> Dates.nowEpoch(null));
        long now = ZonedDateTime.now().toInstant().toEpochMilli();
        long nowEpoch = Dates.nowEpoch(Clock.systemUTC());
        assertTrue(nowEpoch > Dates.plusSeconds(now, -1L));
        now = ZonedDateTime.now().toInstant().toEpochMilli();
        assertTrue(nowEpoch < Dates.plusSeconds(now, 1L));
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
    void plusWeeks() {
        var now = Instant.now();
        var nowEpoch = now.toEpochMilli();
        assertEquals(now.plus(7L, DAYS).toEpochMilli(), Dates.plusWeeks(nowEpoch, 1L));
        assertEquals(now.plus(-7L, DAYS).toEpochMilli(), Dates.plusWeeks(nowEpoch, -1L));
        assertEquals(now.plus(1234L * 7L, DAYS).toEpochMilli(), Dates.plusWeeks(nowEpoch, 1234L));
        assertEquals(now.minus(1234L * 7L, DAYS).toEpochMilli(), Dates.plusWeeks(nowEpoch, -1234L));
    }

    @Test
    void plusHours() {
        var now = Instant.now();
        var nowEpoch = now.toEpochMilli();
        assertEquals(now.plus(1L, HOURS).toEpochMilli(), Dates.plusHours(nowEpoch, 1L));
        assertEquals(now.plus(-1L, HOURS).toEpochMilli(), Dates.plusHours(nowEpoch, -1L));
        assertEquals(now.plus(1234L, HOURS).toEpochMilli(), Dates.plusHours(nowEpoch, 1234L));
        assertEquals(now.minus(1234L, HOURS).toEpochMilli(), Dates.plusHours(nowEpoch, -1234L));
    }

    @Test
    void plusMinutes() {
        var now = Instant.now();
        var nowEpoch = now.toEpochMilli();
        assertEquals(now.plus(1L, MINUTES).toEpochMilli(), Dates.plusMinutes(nowEpoch, 1L));
        assertEquals(now.plus(-1L, MINUTES).toEpochMilli(), Dates.plusMinutes(nowEpoch, -1L));
        assertEquals(now.plus(1234L, MINUTES).toEpochMilli(), Dates.plusMinutes(nowEpoch, 1234L));
        assertEquals(now.minus(1234L, MINUTES).toEpochMilli(), Dates.plusMinutes(nowEpoch, -1234L));
    }

    @Test
    void minusMinutes() {
        var now = Instant.now();
        var nowEpoch = now.toEpochMilli();
        assertEquals(now.minus(1L, MINUTES).toEpochMilli(), Dates.minusMinutes(nowEpoch, 1L));
        assertEquals(now.minus(1234L, MINUTES).toEpochMilli(), Dates.minusMinutes(nowEpoch, 1234L));
    }

    @Test
    void plusSeconds() {
        var now = Instant.now();
        var nowEpoch = now.toEpochMilli();
        assertEquals(now.plusSeconds(1L).toEpochMilli(), Dates.plusSeconds(nowEpoch, 1L));
        assertEquals(now.plusSeconds(-1L).toEpochMilli(), Dates.plusSeconds(nowEpoch, -1L));
        assertEquals(now.plusSeconds(1234L).toEpochMilli(), Dates.plusSeconds(nowEpoch, 1234L));
        assertEquals(now.plusSeconds(-1234L).toEpochMilli(), Dates.plusSeconds(nowEpoch, -1234L));
    }

    @Test
    void localePatterns() {
        assertEquals("dd/MM/yyyy HH:mm (optional timezone)", Dates.LocalePatterns.get(DiscordLocale.FRENCH.toLocale()));
        assertEquals("dd/MM/yyyy HH:mm (optional timezone)", Dates.LocalePatterns.get(DiscordLocale.ENGLISH_UK.toLocale()));
        assertEquals("M/d/yyyy HH:mm (optional timezone)", Dates.LocalePatterns.get(DiscordLocale.ENGLISH_US.toLocale()));
        assertEquals("yyyy/M/d HH:mm (optional timezone)", Dates.LocalePatterns.get(DiscordLocale.JAPANESE.toLocale()));
        assertEquals("dd. MM. yyyy. HH:mm (optional timezone)", Dates.LocalePatterns.get(DiscordLocale.CROATIAN.toLocale()));
        assertEquals("dd/MM/yyyy HH:mm (optional timezone)", Dates.LocalePatterns.get(DiscordLocale.PORTUGUESE_BRAZILIAN.toLocale()));
        assertEquals("d-M-yyyy HH:mm (optional timezone)", Dates.LocalePatterns.get(DiscordLocale.DUTCH.toLocale()));
        assertEquals("dd.MM.yyyy HH:mm (optional timezone)", Dates.LocalePatterns.get(DiscordLocale.TURKISH.toLocale()));
        assertEquals("d.MM.yyyy HH:mm (optional timezone)", Dates.LocalePatterns.get(DiscordLocale.POLISH.toLocale()));
    }
}