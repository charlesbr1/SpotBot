package org.sbot.entities.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.settings.ServerSettings.PRIVATE_SERVER;
import static org.sbot.entities.settings.UserSettings.NO_USER;

class SettingsTest {

    @Test
    void constructor() {
        assertThrows(NullPointerException.class, () -> new Settings(NO_USER, null));
        assertThrows(NullPointerException.class, () -> new Settings(null, PRIVATE_SERVER));
        assertEquals(NO_USER, new Settings(NO_USER, PRIVATE_SERVER).userSettings());
        assertEquals(PRIVATE_SERVER, new Settings(NO_USER, PRIVATE_SERVER).serverSettings());
    }
}