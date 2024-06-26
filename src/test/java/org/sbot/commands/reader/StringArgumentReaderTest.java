package org.sbot.commands.reader;

import org.junit.jupiter.api.Test;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.utils.Dates.UTC;

class StringArgumentReaderTest {

    @Test
    void reversed() {
        StringArgumentReader reader = new StringArgumentReader("test").reversed();
        assertEquals("test", reader.getString("").get());
        assertTrue(reader.getString("").isEmpty());

        reader = new StringArgumentReader("").reversed();
        assertTrue(reader.getString("").isEmpty());

        reader = new StringArgumentReader(" a fzfze fze ").reversed();
        assertEquals("fze", reader.getString("").get());
        assertEquals("a fzfze", reader.getLastArgs("").get());
        assertEquals("fzfze", reader.getString("").get());
        assertEquals("a", reader.getLastArgs("").get());
        assertEquals("a", reader.getString("").get());
        assertTrue(reader.getString("").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());
    }

    @Test
    void getString() {
        assertThrows(NullPointerException.class, () -> new StringArgumentReader(null));
        StringArgumentReader reader = new StringArgumentReader("test");
        assertEquals("test", reader.getString("").get());
        assertTrue(reader.getString("").isEmpty());

        reader = new StringArgumentReader("");
        assertTrue(reader.getString("").isEmpty());

        reader = new StringArgumentReader(" a fzfze fze ");
        assertEquals("a", reader.getString("").get());
        assertEquals("fzfze fze", reader.getLastArgs("").get());
        assertEquals("fzfze", reader.getString("").get());
        assertEquals("fze", reader.getLastArgs("").get());
        assertEquals("fze", reader.getString("").get());
        assertTrue(reader.getString("").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());
    }

    @Test
    void getNumber() {
        StringArgumentReader reader = new StringArgumentReader("test");
        assertTrue(reader.getNumber("").isEmpty());
        assertEquals("test", reader.getLastArgs("").get());

        reader = new StringArgumentReader("123");
        assertEquals(new BigDecimal("123"), reader.getNumber("").get());

        reader = new StringArgumentReader("12 3 ");
        assertEquals(new BigDecimal("12"), reader.getNumber("").get());
        assertEquals(new BigDecimal("3"), reader.getNumber("").get());
        assertTrue(reader.getNumber("").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());
    }

    @Test
    void getLong() {
        StringArgumentReader reader = new StringArgumentReader("test");
        assertTrue(reader.getLong("").isEmpty());
        assertEquals("test", reader.getLastArgs("").get());

        reader = new StringArgumentReader(" 321");
        assertEquals(321L, reader.getLong("").get());

        reader = new StringArgumentReader(" 21 33 ");
        assertEquals(21L, reader.getLong("").get());
        assertEquals(33L, reader.getLong("").get());
        assertTrue(reader.getLong("").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());
    }

    @Test
    void getType() {
        StringArgumentReader reader = new StringArgumentReader("test");
        assertTrue(reader.getType("").isEmpty());
        assertEquals("test", reader.getLastArgs("").get());

        reader = new StringArgumentReader("range");
        assertEquals(range, reader.getType("").get());

        reader = new StringArgumentReader("trend");
        assertEquals(trend, reader.getType("").get());

        reader = new StringArgumentReader("remainder");
        assertEquals(remainder, reader.getType("").get());

        reader = new StringArgumentReader(" trend remainder ");
        assertEquals(trend, reader.getType("").get());
        assertEquals(remainder, reader.getType("").get());
        assertTrue(reader.getType("").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());
    }

    @Test
    void getDateTime() {
        StringArgumentReader reader = new StringArgumentReader("test");
        assertTrue(reader.getDateTime(Locale.US, null, mock(), "").isEmpty());
        assertEquals("test", reader.getLastArgs("").get());

        reader = new StringArgumentReader(" 12/12/2000-20:00");
        assertEquals(Dates.parse(Locale.US, null, mock(), "12/12/2000-20:00"), reader.getDateTime(Locale.US, null, mock(), "").get());
        reader = new StringArgumentReader(" 12/12/2000-20:00");
        assertEquals(Dates.parse(Locale.FRENCH, UTC, mock(), "12/12/2000-20:00"), reader.getDateTime(Locale.US, UTC, mock(), "").get());

        reader = new StringArgumentReader(" 12/12/2000-20:00 02/11/2200-00:00 ");
        assertEquals(Dates.parse(Locale.US, null, mock(), "12/12/2000-20:00"), reader.getDateTime(Locale.US, null, mock(), "").get());
        assertEquals(Dates.parse(Locale.US, ZoneId.of("Europe/Paris"), mock(), "02/11/2200-00:00"), reader.getDateTime(Locale.US, ZoneId.of("Europe/Paris"), mock(), "").get());
        assertTrue(reader.getDateTime(Locale.US, null, mock(), "").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());

        reader = new StringArgumentReader(" 12/12/2000-20:00 02/11/2200-00:00 ");
        assertEquals(Dates.parse(Locale.FRENCH, null, mock(), "12/12/2000-20:00"), reader.getDateTime(Locale.US, null, mock(), "").get());
        assertEquals(Dates.parse(Locale.FRENCH, UTC, mock(), "11/02/2200-00:00"), reader.getDateTime(Locale.US, UTC, mock(), "").get());
        assertTrue(reader.getDateTime(Locale.US, null, mock(), "").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());

        reader = new StringArgumentReader("now");
        ZonedDateTime now = LocalDateTime.now().atZone(UTC);
        assertEquals(now, reader.getDateTime(Locale.US, null, Clock.fixed(now.toInstant(), now.getZone()), "").get());
        assertTrue(reader.getDateTime(Locale.US, null, mock(), "").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());

        reader = new StringArgumentReader("NOW");
        assertEquals(now.withZoneSameInstant(ZoneId.of("Z")), reader.getDateTime(Locale.US, ZoneId.of("Z"), Clock.fixed(now.toInstant(), UTC), "").get());
        assertTrue(reader.getDateTime(Locale.US, null, mock(), "").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());

        reader = new StringArgumentReader("nOw");
        assertEquals(now.withZoneSameInstant(ZoneId.of("Europe/Paris")), reader.getDateTime(Locale.US, ZoneId.of("Europe/Paris"), Clock.fixed(now.toInstant(), UTC), "").get());
        assertTrue(reader.getDateTime(Locale.US, null, mock(), "").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());
    }

    @Test
    void getLocalDateTime() {
        StringArgumentReader reader = new StringArgumentReader("test");
        assertTrue(reader.getLocalDateTime(Locale.US, "").isEmpty());
        assertEquals("test", reader.getLastArgs("").get());

        reader = new StringArgumentReader("02/11/2000-20:00");
        assertEquals(Dates.parseLocalDateTime(Locale.US, "2/11/2000-20:00"), reader.getLocalDateTime(Locale.US, "").get());
        reader = new StringArgumentReader("02/11/2000-20:00");
        assertEquals(Dates.parseLocalDateTime(Locale.FRENCH, "11/02/2000-20:00"), reader.getLocalDateTime(Locale.US, "").get());

        reader = new StringArgumentReader(" 12/12/2000-20:00");
        assertEquals(Dates.parseLocalDateTime(Locale.US, "12/12/2000-20:00"), reader.getLocalDateTime(Locale.US, "").get());

        reader = new StringArgumentReader(" 12/12/2000-20:00 02/11/2200-00:00 ");
        assertEquals(Dates.parseLocalDateTime(Locale.FRENCH, "12/12/2000-20:00"), reader.getLocalDateTime(Locale.FRENCH, "").get());
        assertEquals(Dates.parseLocalDateTime(Locale.FRENCH, "02/11/2200-00:00"), reader.getLocalDateTime(Locale.FRENCH, "").get());
        assertTrue(reader.getLocalDateTime(Locale.FRENCH, "").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());
    }

    @Test
    void getUserId() {
        StringArgumentReader reader = new StringArgumentReader("test");
        assertTrue(reader.getUserId(DISCORD, "").isEmpty());
        assertEquals("test", reader.getLastArgs("").get());

        reader = new StringArgumentReader(" <@321>");
        assertEquals(321L, reader.getUserId(DISCORD, "").get());

        reader = new StringArgumentReader(" <@21> <@33> @<345> ");
        assertEquals(21L, reader.getUserId(DISCORD, "").get());
        assertEquals(33L, reader.getUserId(DISCORD, "").get());
        assertTrue(reader.getUserId(DISCORD, "").isEmpty());
        assertEquals("@<345>", reader.getLastArgs("").get());
    }

    @Test
    void getLastArgs() {
        StringArgumentReader reader = new StringArgumentReader("");
        assertTrue(reader.getLastArgs("").isEmpty());

        reader = new StringArgumentReader("test");
        assertEquals("test", reader.getLastArgs("").get());
        assertEquals("test", reader.getLastArgs("").get());

        reader = new StringArgumentReader(" test 123 <@21> 456 <@33>  12/11/2200-00:00 and the last  args");
        assertEquals("test 123 <@21> 456 <@33>  12/11/2200-00:00 and the last  args", reader.getLastArgs("").get());
        assertEquals("test", reader.getString("").get());
        assertEquals("123 <@21> 456 <@33>  12/11/2200-00:00 and the last  args", reader.getLastArgs("").get());
        assertEquals(123L, reader.getLong("").get());
        assertEquals("<@21> 456 <@33>  12/11/2200-00:00 and the last  args", reader.getLastArgs("").get());
        assertEquals(21L, reader.getUserId(DISCORD, "").get());
        assertEquals("456 <@33>  12/11/2200-00:00 and the last  args", reader.getLastArgs("").get());
        assertEquals(new BigDecimal("456"), reader.getNumber("").get());
        assertEquals("<@33>  12/11/2200-00:00 and the last  args", reader.getLastArgs("").get());
        assertEquals(33L, reader.getUserId(DISCORD, "").get());
        assertEquals("12/11/2200-00:00 and the last  args", reader.getLastArgs("").get());
        assertEquals(Dates.parse(Locale.US, null, mock(), "12/11/2200-00:00"), reader.getDateTime(Locale.US, null, mock(), "").get());
        assertEquals("and the last  args", reader.getLastArgs("").get());
        reader.getString("");
        reader.getString("");
        reader.getString("");
        assertEquals("args", reader.getLastArgs("").get());
        reader.getString("");
        assertTrue(reader.getLastArgs("").isEmpty());
    }


    @Test
    void getMandatoryString() {
        var reader = new StringArgumentReader("abc");
        assertEquals("abc", reader.getMandatoryString(""));

        var finalReader = new StringArgumentReader("");
        assertThrows(IllegalArgumentException.class, () -> finalReader.getMandatoryString(""));
    }

    @Test
    void getMandatoryNumber() {
        var reader = new StringArgumentReader("1.234");
        assertEquals(new BigDecimal("1.234"), reader.getMandatoryNumber(""));
        reader = new StringArgumentReader("1,234");
        assertEquals(new BigDecimal("1.234"), reader.getMandatoryNumber(""));

        var finalReader = new StringArgumentReader("abc");
        assertThrows(IllegalArgumentException.class, () -> finalReader.getMandatoryNumber(""));
    }

    @Test
    void getMandatoryLong() {
        var reader = new StringArgumentReader("123");
        assertEquals(123, reader.getMandatoryLong(""));

        var finalReader = new StringArgumentReader("abc");
        assertThrows(IllegalArgumentException.class, () -> finalReader.getMandatoryLong(""));
    }

    @Test
    void getMandatoryDateTime() {
        var reader = new StringArgumentReader("12/11/2200-00:00");
        assertEquals(Dates.parse(Locale.US, Dates.UTC, mock(), "12/11/2200-00:00"), reader.getMandatoryDateTime(Locale.US, Dates.UTC, mock(), ""));
        reader = new StringArgumentReader("12/11/2200-00:00");
        assertNotEquals(Dates.parse(Locale.US, Dates.UTC, mock(), "12/11/2200-00:00"), reader.getMandatoryDateTime(Locale.US, ZoneId.of("GMT"), mock(), ""));

        var finalReader = new StringArgumentReader("abc");
        assertThrows(IllegalArgumentException.class, () -> finalReader.getMandatoryDateTime(Locale.US, UTC, mock(), ""));
    }

    @Test
    void getMandatoryType() {
        var reader = new StringArgumentReader("range");
        assertEquals(range, reader.getMandatoryType(""));
        reader = new StringArgumentReader("trend");
        assertEquals(trend, reader.getMandatoryType(""));
        reader = new StringArgumentReader("remainder");
        assertEquals(remainder, reader.getMandatoryType(""));

        var finalReader = new StringArgumentReader("abc");
        assertThrows(IllegalArgumentException.class, () -> finalReader.getMandatoryType(""));
    }
}