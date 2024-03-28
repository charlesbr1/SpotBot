package org.sbot.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.settings.Settings;
import org.sbot.entities.settings.UserSettings;
import org.sbot.services.context.Context;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);

        var fc1 = CommandContext.of(context, settings, event, "uptime a");
        assertThrows(IllegalArgumentException.class, () -> upTimeCommand.onCommand(fc1));
        verify(context, never()).clock();

        CommandContext commandContext = spy(CommandContext.of(context, settings, event, "uptime"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        ArgumentCaptor<List<Message>> argumentCaptor = ArgumentCaptor.forClass(List.class);

        upTimeCommand.onCommand(commandContext);
        verify(commandContext).reply(argumentCaptor.capture(), eq(upTimeCommand.responseTtlSeconds));

        List<Message> message = argumentCaptor.getValue();
        assertEquals(1, message.size());
        assertEquals(1, message.get(0).embeds().size());
        assertTrue(message.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("SpotBot started"));
    }
}