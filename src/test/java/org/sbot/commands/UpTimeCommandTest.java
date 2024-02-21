package org.sbot.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.services.context.Context;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.time.Clock;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.entities.alerts.AlertTest.TEST_USER_ID;

class UpTimeCommandTest {

    private final UpTimeCommand upTimeCommand = new UpTimeCommand();

    @Test
    void isSlashCommand() {
        assertFalse(upTimeCommand.isSlashCommand());
    }

    @Test
    void onCommand() {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        when(event.getMessage()).thenReturn(mock(net.dv8tion.jda.api.entities.Message.class));
        when(event.getAuthor()).thenReturn(mock(User.class));
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        org.sbot.entities.User user = new org.sbot.entities.User(TEST_USER_ID, Locale.UK, Dates.UTC, DatesTest.nowUtc());

        assertThrows(IllegalArgumentException.class,
                () -> upTimeCommand.onCommand(CommandContext.of(context, user, event, "uptime a")));
        verify(context, never()).clock();

        CommandContext commandContext = spy(CommandContext.of(context, user, event, "uptime"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        ArgumentCaptor<List<Message>> argumentCaptor = ArgumentCaptor.forClass(List.class);

        upTimeCommand.onCommand(commandContext);
        verify(context).clock();
        verify(commandContext).reply(argumentCaptor.capture(), eq(upTimeCommand.responseTtlSeconds));

        List<Message> message = argumentCaptor.getValue();
        assertEquals(1, message.size());
        assertEquals(1, message.get(0).embeds().size());
        assertTrue(message.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("up"));
        assertTrue(message.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("up"));
    }
}