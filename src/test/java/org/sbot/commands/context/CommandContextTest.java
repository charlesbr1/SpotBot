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
    void ofTest() {
//        assertExceptionContains(IllegalArgumentException.class, "Missing command",
  //              () -> CommandContext.of(context, null, messageReceivedEvent, ""));
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
    void of() {
    }

    @Test
    void testOf() {
    }

    @Test
    void testOf1() {
    }

    @Test
    void testOf2() {
    }

    @Test
    void withArgumentsAndReplyMapper() {
    }

    @Test
    void serverId() {
    }

    @Test
    void noMoreArgs() {
    }

    @Test
    void clock() {
    }

    @Test
    void dataServices() {
    }

    @Test
    void services() {
    }

    @Test
    void parameters() {
    }
}