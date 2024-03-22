package org.sbot.commands.context;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.sbot.services.context.Context;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandContextTest {


    @Test
    void constructor() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        Context context = mock(Context.class);
        assertThrows(NullPointerException.class, () -> CommandContext.of(context, mock(), event, ""));
        when(event.getMessage()).thenReturn(mock(Message.class));
        assertThrows(IllegalArgumentException.class, () -> CommandContext.of(context, mock(), event, ""));
    }

    @Test
    void isStringReader() {

    }

    @Test
    void noMoreArgs() {
    }
}