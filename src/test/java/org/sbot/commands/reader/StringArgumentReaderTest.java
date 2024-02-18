package org.sbot.commands.reader;

import org.junit.jupiter.api.Test;
import org.sbot.utils.Dates;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

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
    void getDateTime() {
        StringArgumentReader reader = new StringArgumentReader("test");
        assertTrue(reader.getDateTime("").isEmpty());
        assertEquals("test", reader.getLastArgs("").get());

        reader = new StringArgumentReader(" 12/12/2000-20:00");
        assertEquals(Dates.parseUTC("12/12/2000-20:00"), reader.getDateTime("").get());

        reader = new StringArgumentReader(" 12/12/2000-20:00 02/11/2200-00:00 ");
        assertEquals(Dates.parseUTC("12/12/2000-20:00"), reader.getDateTime("").get());
        assertEquals(Dates.parseUTC("02/11/2200-00:00"), reader.getDateTime("").get());
        assertTrue(reader.getDateTime("").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());
    }

    @Test
    void getLocalDateTime() {
        StringArgumentReader reader = new StringArgumentReader("test");
        assertTrue(reader.getLocalDateTime("").isEmpty());
        assertEquals("test", reader.getLastArgs("").get());

        reader = new StringArgumentReader(" 12/12/2000-20:00");
        assertEquals(Dates.parseLocal("12/12/2000-20:00"), reader.getLocalDateTime("").get());

        reader = new StringArgumentReader(" 12/12/2000-20:00 02/11/2200-00:00 ");
        assertEquals(Dates.parseLocal("12/12/2000-20:00"), reader.getLocalDateTime("").get());
        assertEquals(Dates.parseLocal("02/11/2200-00:00"), reader.getLocalDateTime("").get());
        assertTrue(reader.getLocalDateTime("").isEmpty());
        assertTrue(reader.getLastArgs("").isEmpty());
    }

    @Test
    void getUserId() {
        StringArgumentReader reader = new StringArgumentReader("test");
        assertTrue(reader.getUserId("").isEmpty());
        assertEquals("test", reader.getLastArgs("").get());

        reader = new StringArgumentReader(" <@321>");
        assertEquals(321L, reader.getUserId("").get());

        reader = new StringArgumentReader(" <@21> <@33> @<345> ");
        assertEquals(21L, reader.getUserId("").get());
        assertEquals(33L, reader.getUserId("").get());
        assertTrue(reader.getUserId("").isEmpty());
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
        assertEquals(21L, reader.getUserId("").get());
        assertEquals("456 <@33>  12/11/2200-00:00 and the last  args", reader.getLastArgs("").get());
        assertEquals(new BigDecimal("456"), reader.getNumber("").get());
        assertEquals("<@33>  12/11/2200-00:00 and the last  args", reader.getLastArgs("").get());
        assertEquals(33L, reader.getUserId("").get());
        assertEquals("12/11/2200-00:00 and the last  args", reader.getLastArgs("").get());
        assertEquals(Dates.parseUTC("12/11/2200-00:00"), reader.getDateTime("").get());
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
        StringArgumentReader reader = new StringArgumentReader("");
        assertThrows(IllegalArgumentException.class, () -> reader.getMandatoryString(""));
    }

    @Test
    void getMandatoryNumber() {
        StringArgumentReader reader = new StringArgumentReader("abc");
        assertThrows(IllegalArgumentException.class, () -> reader.getMandatoryNumber(""));
    }

    @Test
    void getMandatoryLong() {
        StringArgumentReader reader = new StringArgumentReader("abc");
        assertThrows(IllegalArgumentException.class, () -> reader.getMandatoryLong(""));
    }

    @Test
    void getMandatoryDateTime() {
        StringArgumentReader reader = new StringArgumentReader("abc");
        assertThrows(IllegalArgumentException.class, () -> reader.getMandatoryDateTime(""));
    }

    @Test
    void getMandatoryUserId() {
        StringArgumentReader reader = new StringArgumentReader("abc");
        assertThrows(IllegalArgumentException.class, () -> reader.getMandatoryUserId(""));
    }
}