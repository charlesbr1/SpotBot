package org.sbot.commands.interactions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.commands.interactions.Interactions.INTERACTION_ID_SEPARATOR;

class InteractionsTest {

    @Test
    void interactionId() {
        assertThrows(NullPointerException.class, () -> Interactions.interactionId(null, 123L));
        assertEquals("id" + INTERACTION_ID_SEPARATOR + 123L, Interactions.interactionId("id", 123L));
    }

    @Test
    void componentIdOf() {
        assertThrows(NullPointerException.class, () -> Interactions.componentIdOf(null));
        assertThrows(IllegalArgumentException.class, () -> Interactions.componentIdOf(""));
        assertThrows(IllegalArgumentException.class, () -> Interactions.componentIdOf("abd"));
        assertThrows(IllegalArgumentException.class, () -> Interactions.componentIdOf(INTERACTION_ID_SEPARATOR + "123"));
        assertEquals("id", Interactions.componentIdOf("id" + INTERACTION_ID_SEPARATOR));
        assertEquals("id2", Interactions.componentIdOf("id2" + INTERACTION_ID_SEPARATOR + "321"));
    }

    @Test
    void alertIdOf() {
        assertThrows(NullPointerException.class, () -> Interactions.alertIdOf(null));
        assertThrows(IllegalArgumentException.class, () -> Interactions.alertIdOf(""));
        assertThrows(IllegalArgumentException.class, () -> Interactions.alertIdOf("abd"));
        assertThrows(IllegalArgumentException.class, () -> Interactions.alertIdOf("123" + INTERACTION_ID_SEPARATOR));
        assertThrows(IllegalArgumentException.class, () -> Interactions.alertIdOf(INTERACTION_ID_SEPARATOR + "123"));
        assertEquals("456", Interactions.alertIdOf("id" + INTERACTION_ID_SEPARATOR + "456"));
    }
}