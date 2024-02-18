package org.sbot.commands.reader;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.sbot.commands.context.CommandContext;
import org.sbot.services.context.Context;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandContextTest {

    @Test
    void constructor() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        Context context = mock(Context.class);
        assertThrows(NullPointerException.class, () -> CommandContext.of(context, event, ""));
        when(event.getMessage()).thenReturn(mock(Message.class));
        assertThrows(IllegalArgumentException.class, () -> CommandContext.of(context, event, ""));
    }

    @Test
    void serverId() {
    }

    @Test
    void noMoreArgs() {
    }

    @Test
    void reply() {
    }

    @Test
    void testReply() {
    }

    @Test
    void testReply1() {
    }

    @Test
    void testReply2() {
    }
}