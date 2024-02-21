package org.sbot.entities;

import org.junit.jupiter.api.Test;
import org.sbot.utils.DatesTest;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void constructor() {
        var now = DatesTest.nowUtc();
        var user = new User(1L, Locale.US, now);
        assertEquals(1L, user.id());
        assertEquals(Locale.US, user.locale());
        assertEquals(now, user.lastAccess());
        assertThrows(NullPointerException.class, () -> new User(1L, null, now));
        assertThrows(NullPointerException.class, () -> new User(1L, Locale.UK, null));
    }

    @Test
    void withLocale() {
        var user = new User(1L, Locale.US, DatesTest.nowUtc());
        assertEquals(Locale.US, user.locale());
        assertEquals(Locale.UK, user.withLocale(Locale.UK).locale());
    }

    @Test
    void withLastAccess() {
        var now = DatesTest.nowUtc();
        var user = new User(1L, Locale.US, now);
        assertEquals(now.plusMinutes(31L), user.withLastAccess(now.plusMinutes(31L)).lastAccess());
    }
}