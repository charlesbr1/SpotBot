package org.sbot.commands.context;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.sbot.entities.ServerSettings;
import org.sbot.entities.Settings;
import org.sbot.entities.UserSettings;
import org.sbot.services.context.Context;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandContextTest {


    @Test
    void constructor() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        when(event.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);
        assertThrows(NullPointerException.class, () -> CommandContext.of(context, mock(), event, ""));
        when(event.getMessage()).thenReturn(mock());
        assertThrows(IllegalArgumentException.class, () -> CommandContext.of(context, mock(), event, ""));
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);
        assertDoesNotThrow(() -> CommandContext.of(context, settings, event, "test"));
    }

    @Test
    void isStringReader() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        when(event.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);
        when(event.getMessage()).thenReturn(mock());
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);
        var commandContext = CommandContext.of(context, settings, event, "test");
        assertTrue(commandContext.isStringReader());
    }

    @Test
    void noMoreArgs() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        when(event.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);
        when(event.getMessage()).thenReturn(mock());
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);
        var commandContext = CommandContext.of(context, settings, event, "test");
        assertDoesNotThrow(commandContext::noMoreArgs);

        var c2 = CommandContext.of(context, settings, event, "test 2");
        assertThrows(IllegalArgumentException.class, c2::noMoreArgs);
        assertEquals("2", c2.args.getMandatoryString(""));
        assertDoesNotThrow(c2::noMoreArgs);

        var c3 = CommandContext.of(context, settings, event, "test tt");
        assertThrows(IllegalArgumentException.class, c3::noMoreArgs);
        assertEquals(Optional.of("tt"), c3.args.getString(""));
        assertDoesNotThrow(c3::noMoreArgs);
    }
}