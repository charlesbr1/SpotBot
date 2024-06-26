package org.sbot.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.settings.Settings;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.DataServices;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.UserSettingsDao;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.*;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.UpdateCommand.CHOICE_MESSAGE;
import static org.sbot.entities.settings.UserSettings.DEFAULT_LOCALE;
import static org.sbot.entities.settings.UserSettings.NO_USER;
import static org.sbot.entities.alerts.Alert.DEFAULT_SNOOZE_HOURS;
import static org.sbot.entities.alerts.Alert.MARGIN_DISABLED;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;
import static org.sbot.utils.ArgumentValidator.MESSAGE_MAX_LENGTH;
import static org.sbot.utils.Dates.UTC;

class RemainderCommandTest {

    @Test
    void onCommand() {
        long userId = 321L;
        long serverId = 123L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(userId);
        Member member = mock();
        when(messageReceivedEvent.getMember()).thenReturn(member);
        Guild guild = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(serverId);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        String date = Dates.formatUTC(DEFAULT_LOCALE, now.plusHours(3L).plusDays(2L));
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        UserSettingsDao settingsDao = mock();
        AlertsDao alertsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.userSettingsDao()).thenReturn(v -> settingsDao);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);
        var settings = new Settings(NO_USER, ServerSettings.PRIVATE_SERVER);


        var command = new RemainderCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message fe fe  " + date));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(settingsDao).userExists(DISCORD, userId);
        verify(alertsDao, never()).addAlert(any());
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        List<Message> messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("Missing user account setup"));

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message fe fe  " + date));
        doNothing().when(commandContext).reply(anyList(), eq(command.responseTtlSeconds));
        when(settingsDao.userExists(DISCORD, userId)).thenReturn(true);
        command.onCommand(commandContext);

        verify(settingsDao, times(2)).userExists(DISCORD, userId);
        var alertReply = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).addAlert(alertReply.capture());
        var alert = alertReply.getValue();
        assertEquals(remainder, alert.type);
        assertEquals(userId, alert.userId);
        assertEquals(serverId, alert.serverId);
        assertNull(alert.lastTrigger);
        assertNotNull(alert.listeningDate);
        assertEquals(alert.listeningDate, alert.fromDate);
        assertEquals(now, alert.creationDate);
        assertEquals("ETH/USD", alert.pair);
        assertEquals(now.plusHours(3L).plusDays(2L), alert.fromDate);
        assertEquals("a  message fe fe", alert.message);
        assertNull(alert.toDate);
        assertNull(alert.fromPrice);
        assertNull(alert.toPrice);
        assertEquals(MARGIN_DISABLED, alert.margin);
        assertEquals(REMAINDER_DEFAULT_REPEAT, alert.repeat);
        assertEquals(DEFAULT_SNOOZE_HOURS, alert.snooze);

        messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), eq(command.responseTtlSeconds));
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertFalse(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("Missing user account setup"));
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().toUpperCase()
                .contains("Remainder".toUpperCase()));
    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        Context context = mock(Context.class);
        var settings = new Settings(NO_USER, ServerSettings.PRIVATE_SERVER);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> RemainderCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + "  a message fe fe fe");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> RemainderCommand.arguments(commandContext[0], now));

        String date = Dates.formatUTC(commandContext[0].locale, now.plusHours(3L).plusDays(2L));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " " + date);
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> RemainderCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " ethusd");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> RemainderCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd");
        assertExceptionContains(IllegalArgumentException.class, DATE_ARGUMENT,
                () -> RemainderCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd a message fe fe fe");
        assertExceptionContains(IllegalArgumentException.class, DATE_ARGUMENT,
                () -> RemainderCommand.arguments(commandContext[0], now));

        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd " + date);
        assertExceptionContains(IllegalArgumentException.class, MESSAGE_ARGUMENT,
                () -> RemainderCommand.arguments(commandContext[0], now));

        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd " + date + " to");
        assertExceptionContains(IllegalArgumentException.class, DATE_ARGUMENT,
                () -> RemainderCommand.arguments(commandContext[0], now));

        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message fe fe  " + date + " too mu");
        assertExceptionContains(IllegalArgumentException.class, DATE_ARGUMENT,
                () -> RemainderCommand.arguments(commandContext[0], now));

        // use dash for date time separator in string commands
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message 10/10/3010 20:01");
        assertExceptionContains(IllegalArgumentException.class, DATE_ARGUMENT,
                () -> RemainderCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message fe fe  " + date);
        var arguments = RemainderCommand.arguments(commandContext[0], now);
        assertNotNull(arguments);
        assertEquals("ETH/USD", arguments.pair());
        assertEquals(now.plusHours(3L).plusDays(2L), arguments.date());
        assertEquals("a  message fe fe", arguments.message());

        // test date in past
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message 10/10/1010-20:01");
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RemainderCommand.arguments(commandContext[0], now));

        // test date now ko
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message " + Dates.formatUTC(Locale.UK, now));
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RemainderCommand.arguments(commandContext[0], now));

        // test date now + 1h ok
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message " + Dates.formatUTC(Locale.UK, now.plusHours(1L).minusSeconds(1L)));
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RemainderCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd   a  message " + Dates.formatUTC(Locale.UK, now.plusHours(1L)));
        assertDoesNotThrow(() -> RemainderCommand.arguments(commandContext[0], now));

        // message too long
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd " + "aa".repeat(MESSAGE_MAX_LENGTH) + " 10/10/2210-20:01");
        assertExceptionContains(IllegalArgumentException.class, "too long",
                () -> RemainderCommand.arguments(commandContext[0], now));

        // missing message
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RemainderCommand.NAME + " eth/usd    10/10/2210-20:01");
        assertExceptionContains(IllegalArgumentException.class, CHOICE_MESSAGE,
                () -> RemainderCommand.arguments(commandContext[0], now));
    }
}