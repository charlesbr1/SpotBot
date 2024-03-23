package org.sbot.entities;

import org.junit.jupiter.api.Test;
import org.sbot.entities.notifications.RecipientType;

import static org.junit.jupiter.api.Assertions.*;

class RecipientTypeTest {

    @Test
    void SHORTNAMES() {
        assertNotEquals(RecipientType.DISCORD_USER, RecipientType.SHORTNAMES.get(RecipientType.DISCORD_SERVER.shortName));
        assertEquals(RecipientType.DISCORD_USER, RecipientType.SHORTNAMES.get(RecipientType.DISCORD_USER.shortName));
        assertEquals(RecipientType.DISCORD_SERVER, RecipientType.SHORTNAMES.get(RecipientType.DISCORD_SERVER.shortName));
    }
}