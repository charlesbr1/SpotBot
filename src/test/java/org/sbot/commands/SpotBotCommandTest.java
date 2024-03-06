package org.sbot.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.Services;
import org.sbot.services.discord.Discord;
import org.sbot.utils.DatesTest;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static net.dv8tion.jda.api.entities.MessageEmbed.DESCRIPTION_MAX_LENGTH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.SpotBotCommand.CHOICE_COMMANDS;
import static org.sbot.commands.SpotBotCommand.CHOICE_DOC;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;
import static org.sbot.utils.Dates.UTC;

class SpotBotCommandTest {

    @Test
    void onCommand() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        Services services = mock();
        Discord discord = mock();
        when(services.discord()).thenReturn(discord);
        when(context.services()).thenReturn(services);

        var command = new SpotBotCommand();
        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, SpotBotCommand.NAME + " badarg"));
        assertExceptionContains(IllegalArgumentException.class, "Invalid", () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, null, messageReceivedEvent, SpotBotCommand.NAME + " " + CHOICE_DOC + " la"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc2));

        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, SpotBotCommand.NAME + "  " + CHOICE_DOC));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(discord.spotBotUserMention()).thenReturn("<@spotbot>");
        command.onCommand(commandContext);

        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(0).files().size());

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, SpotBotCommand.NAME + "  " + CHOICE_COMMANDS));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(3, messages.size());
        for(var message : messages) {
            assertEquals(1, message.embeds().size());
            assertTrue(message.embeds().getFirst().getDescriptionBuilder().length() <= DESCRIPTION_MAX_LENGTH);
        }
    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, SpotBotCommand.NAME + "  selection too");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> SpotBotCommand.arguments(commandContext[0]));


        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, SpotBotCommand.NAME + "  selection " );
        var arguments = SpotBotCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals("selection", arguments.selection());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, SpotBotCommand.NAME + "   " );
        arguments = SpotBotCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_DOC, arguments.selection());
    }
}