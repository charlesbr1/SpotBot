package org.sbot.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CharSequenceHolderTest {

    @Test
    void constructorCheck() {
        assertThrows(NullPointerException.class, () -> new CharSequenceHolder(null));
        assertEquals("123", new CharSequenceHolder("123").charSequence());
        assertNotEquals(new StringBuilder("123"), new CharSequenceHolder(new StringBuilder("123")).charSequence());
        assertEquals("123", new CharSequenceHolder(new StringBuilder("123")).charSequence().toString());
    }

    @Test
    void equals() {
        var csh = new CharSequenceHolder("123");
        assertFalse(csh.equals(null));
        assertFalse(csh.equals("123"));
        assertTrue(csh.equals(new CharSequenceHolder("123")));
        assertEquals(csh.hashCode(), new CharSequenceHolder("123").hashCode());
        assertTrue(csh.equals(new CharSequenceHolder(new StringBuilder("123"))));
        assertEquals(csh.hashCode(), new CharSequenceHolder(new StringBuilder("123")).hashCode());
        assertTrue(new CharSequenceHolder(new StringBuilder("123")).equals(new CharSequenceHolder(new StringBuilder("123"))));
    }

    @Test
    void testEquals() {
        assertThrows(NullPointerException.class, () -> CharSequenceHolder.equals(null, "", 0, 1));
        assertThrows(NullPointerException.class, () -> CharSequenceHolder.equals("1", null, 0, 1));
        assertTrue(CharSequenceHolder.equals("1", "1", 0, 1));
        assertFalse(CharSequenceHolder.equals("1", "1", 0, 0));
        assertFalse(CharSequenceHolder.equals("12", "123", 0, 1));
        assertTrue(CharSequenceHolder.equals("12", "123", 0, 2));
        assertTrue(CharSequenceHolder.equals("12", "1231234", 3, 5));
        assertFalse(CharSequenceHolder.equals("12", "1231234", 3, 6));
        assertFalse(CharSequenceHolder.equals("12", "1231234", 3, 16));
        assertFalse(CharSequenceHolder.equals("123456789", "1234567", 0, 8));
    }
}