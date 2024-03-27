package org.sbot.commands;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.ServerSettings;
import org.sbot.entities.Settings;
import org.sbot.entities.UserSettings;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.Parameters;
import org.sbot.services.context.Context.Services;
import org.sbot.services.discord.Discord;
import org.sbot.utils.DatesTest;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import static net.dv8tion.jda.api.entities.MessageEmbed.DESCRIPTION_MAX_LENGTH;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.SpotBotCommand.*;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;
import static org.sbot.entities.UserSettings.NO_USER;
import static org.sbot.utils.ArgumentValidator.SETTINGS_MAX_LENGTH;
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
        Parameters parameters = mock();
        when(context.parameters()).thenReturn(parameters);
        var settings = new Settings(NO_USER, ServerSettings.PRIVATE_SERVER);

        var command = new SpotBotCommand();
        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var fc1 = spy(CommandContext.of(context, settings, messageReceivedEvent, SpotBotCommand.NAME + " badarg"));
        assertExceptionContains(IllegalArgumentException.class, "Invalid", () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, settings, messageReceivedEvent, SpotBotCommand.NAME + " " + CHOICE_DOC + " la"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc2));

        // test doc
        var commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, SpotBotCommand.NAME + "  " + CHOICE_DOC));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(discord.spotBotUserMention()).thenReturn("<@spotbot>");
        command.onCommand(commandContext);

        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(0).files().size());
        var description = messages.getFirst().embeds().getFirst().build().getDescription();
        assertTrue(description.length() <= DESCRIPTION_MAX_LENGTH);
        assertTrue(description.contains(END_DOC_CONTENT.substring(0, 15)));

        // test doc max length
        var longTimezone = "America/Argentina/ComodRivadavia";
        var locale = "pt-BR";
        var longSettings = "0".repeat(SETTINGS_MAX_LENGTH);
        settings = new Settings(UserSettings.ofDiscordUser(123L, Locale.forLanguageTag(locale), ZoneId.of(longTimezone), now),
                ServerSettings.ofDiscordServer(123L, ZoneId.of(longTimezone), longSettings, longSettings, longSettings, now));

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, SpotBotCommand.NAME + "  " + CHOICE_DOC));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(discord.spotBotUserMention()).thenReturn("<@spotbot>");
        command.onCommand(commandContext);

        messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(0).files().size());
        description = messages.getFirst().embeds().getFirst().build().getDescription();
        assertTrue(description.length() <= DESCRIPTION_MAX_LENGTH);
        assertFalse(description.contains(END_DOC_CONTENT.substring(0, 15)));

        // test command
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, SpotBotCommand.NAME + "  " + CHOICE_COMMANDS));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(3, messages.size());
        for(var message : messages) {
            assertEquals(1, message.embeds().size());
            description = messages.getFirst().embeds().getFirst().build().getDescription();
            assertTrue(description.length() <= DESCRIPTION_MAX_LENGTH);
        }
    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);
        var settings = new Settings(NO_USER, ServerSettings.PRIVATE_SERVER);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, SpotBotCommand.NAME + "  selection too");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> SpotBotCommand.arguments(commandContext[0]));


        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, SpotBotCommand.NAME + "  selection " );
        var arguments = SpotBotCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals("selection", arguments.selection());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, SpotBotCommand.NAME + "   " );
        arguments = SpotBotCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_DOC, arguments.selection());
    }
}