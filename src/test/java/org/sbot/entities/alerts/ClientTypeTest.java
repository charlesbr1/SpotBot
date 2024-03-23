package org.sbot.entities.alerts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientTypeTest {

    @Test
    void SHORTNAMES() {
        assertEquals("d", ClientType.DISCORD.shortName);
        assertEquals(ClientType.DISCORD, ClientType.SHORTNAMES.get(ClientType.DISCORD.shortName));
    }
}