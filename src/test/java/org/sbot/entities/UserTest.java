package org.sbot.entities;

import org.junit.jupiter.api.Test;
import org.sbot.utils.DatesTest;

import java.time.ZoneId;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.utils.Dates.UTC;

class UserTest {

    @Test
    void constructor() {
        var now = DatesTest.nowUtc();
        var user = new User(1L, Locale.US, null, now);
        assertEquals(1L, user.id());
        assertEquals(Locale.US, user.locale());
        assertEquals(now, user.lastAccess());
        assertNull(user.timeZone());
        assertEquals(UTC,  new User(1L, Locale.US, UTC, now).timeZone());
        assertThrows(NullPointerException.class, () -> new User(1L, null, null, now));
        assertThrows(NullPointerException.class, () -> new User(1L, Locale.UK, null, null));
    }

    @Test
    void withLocale() {
        var user = new User(1L, Locale.US, null, DatesTest.nowUtc());
        assertEquals(Locale.US, user.locale());
        assertEquals(Locale.UK, user.withLocale(Locale.UK).locale());
    }

    @Test
    void withTimezone() {
        var user = new User(1L, Locale.US, null, DatesTest.nowUtc());
        assertNull(user.timeZone());
        assertEquals(UTC, user.withTimezone(UTC).timeZone());
        assertEquals(ZoneId.of("GMT"), user.withTimezone(ZoneId.of("GMT")).timeZone());
        assertEquals(ZoneId.systemDefault(), user.withTimezone(ZoneId.systemDefault()).timeZone());
    }

    @Test
    void withLastAccess() {
        var now = DatesTest.nowUtc();
        var user = new User(1L, Locale.US, null, now);
        assertEquals(now.plusMinutes(31L), user.withLastAccess(now.plusMinutes(31L)).lastAccess());
    }
}