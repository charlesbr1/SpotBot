package org.sbot.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.settings.Settings;
import org.sbot.entities.alerts.Alert;
import org.sbot.exchanges.binance.BinanceClient;
import org.sbot.services.context.Context;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.UserSettingsDao;
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.*;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.UpdateCommand.CHOICE_MESSAGE;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;
import static org.sbot.entities.settings.UserSettings.DEFAULT_LOCALE;
import static org.sbot.entities.settings.UserSettings.NO_USER;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.trend;
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.utils.ArgumentValidator.MESSAGE_MAX_LENGTH;
import static org.sbot.utils.Dates.UTC;

class TrendCommandTest {

    @Test
    void onCommandCreate() {
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
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        UserSettingsDao settingsDao = mock();
        AlertsDao alertsDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.userSettingsDao()).thenReturn(v -> settingsDao);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);

        String dateFrom = Dates.formatUTC(DEFAULT_LOCALE, now.plusHours(3L).plusDays(2L));
        String dateTo = Dates.formatUTC(DEFAULT_LOCALE, now.plusHours(9L).plusDays(2L));
        var settings = new Settings(NO_USER, ServerSettings.PRIVATE_SERVER);

        var command = new TrendCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var fc1 = spy(CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp    \t 12,45 " + dateFrom + " 23232.6 " + dateTo));
        doNothing().when(fc1).reply(anyList(), anyInt());
        assertExceptionContains(IllegalArgumentException.class, CHOICE_MESSAGE, () -> command.onCommand(fc1));

        var commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45 " + dateFrom + " 23232.6 " + dateTo));
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

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45 " + dateFrom + " 23232.6 " + dateTo));
        doNothing().when(commandContext).reply(anyList(), eq(command.responseTtlSeconds));
        when(settingsDao.userExists(DISCORD, userId)).thenReturn(true);
        command.onCommand(commandContext);

        verify(settingsDao, times(2)).userExists(DISCORD, userId);
        var alertReply = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).addAlert(alertReply.capture());
        var alert = alertReply.getValue();
        assertEquals(trend, alert.type);
        assertEquals(userId, alert.userId);
        assertEquals(serverId, alert.serverId);
        assertNull(alert.lastTrigger);
        assertEquals(now, alert.listeningDate);
        assertEquals(now, alert.creationDate);
        assertEquals("DOT/XRP", alert.pair);
        assertEquals(now.plusHours(3L).plusDays(2L), alert.fromDate);
        assertEquals(now.plusHours(9L).plusDays(2L), alert.toDate);
        assertEquals(new BigDecimal("12.45"), alert.fromPrice);
        assertEquals(new BigDecimal("23232.6"), alert.toPrice);
        assertEquals("this is the message !!", alert.message);
        assertEquals(MARGIN_DISABLED, alert.margin);
        assertEquals(DEFAULT_REPEAT, alert.repeat);
        assertEquals(DEFAULT_SNOOZE_HOURS, alert.snooze);

        messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), eq(command.responseTtlSeconds));
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertFalse(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("Missing user account setup"));
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().toUpperCase()
                .contains("Trend".toUpperCase()));
    }

    @Test
    void onCommendGetCurrentTrend() {
        // test trend id command (to show estimated price at a date)
        long alertId = 344343L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(TEST_USER_ID);
        Member member = mock();
        when(messageReceivedEvent.getMember()).thenReturn(member);
        Guild guild = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(PRIVATE_MESSAGES);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        AlertsDao alertsDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);
        var settings = new Settings(NO_USER, ServerSettings.PRIVATE_SERVER);

        String dateFrom = Dates.formatUTC(DEFAULT_LOCALE, now.plusHours(3L).plusDays(2L));

        var command = new TrendCommand();

        CommandContext[] commandContext = new CommandContext[1];
        when(user.getIdLong()).thenReturn(TEST_USER_ID);
        commandContext[0] = spy(CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + "   " + alertId + " " + dateFrom));
        doNothing().when(commandContext[0]).reply(anyList(), eq(command.responseTtlSeconds));
        command.onCommand(commandContext[0]);

        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext[0]).reply(messagesReply.capture(), eq(command.responseTtlSeconds));
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        var message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().equals("Alert " + alertId + " not found"));

        commandContext[0] = spy(CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + "   " + alertId + " " + dateFrom));
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(createTestAlert()
                .withServerId(PRIVATE_MESSAGES)));
        assertThrows(IllegalArgumentException.class, () -> command.onCommand(commandContext[0]));

        commandContext[0] = spy(CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + "   " + alertId + " " + dateFrom));
        doNothing().when(commandContext[0]).reply(anyList(), eq(command.responseTtlSeconds));
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(createTestAlertWithType(trend)
                .withServerId(PRIVATE_MESSAGES))); // switch alert as private to get security grant
        command.onCommand(commandContext[0]);

        verify(commandContext[0]).reply(messagesReply.capture(), eq(command.responseTtlSeconds));
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getFields().stream().map(Field::getName).anyMatch(DISPLAY_CURRENT_TREND_PRICE::equals));
        assertTrue(message.getFields().stream().map(Field::getName).anyMatch(DISPLAY_FROM_PRICE::equals));
        assertTrue(message.getFields().stream().map(Field::getName).anyMatch(DISPLAY_FROM_DATE::equals));
        assertTrue(message.getFields().stream().map(Field::getName).anyMatch(DISPLAY_TO_PRICE::equals));
        assertTrue(message.getFields().stream().map(Field::getName).anyMatch(DISPLAY_TO_DATE::equals));
        assertTrue(message.getDescriptionBuilder().toString().contains(TEST_PAIR.toUpperCase()));
    }
    @Test
    void arguments() {
        long alertId = 123L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        String dateFrom = Dates.formatUTC(DEFAULT_LOCALE, now.plusHours(3L).plusDays(2L));
        String dateTo = Dates.formatUTC(DEFAULT_LOCALE, now.plusHours(9L).plusDays(2L));
        var settings = new Settings(NO_USER, ServerSettings.PRIVATE_SERVER);

        CommandContext[] commandContext = new CommandContext[1];

        // test alert id arguments (to show estimated price at a date)
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + alertId);
        assertExceptionContains(IllegalArgumentException.class, DATE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + alertId + " " + dateFrom + " other");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + alertId + " " + dateFrom + " " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + "   " + alertId + " " + dateFrom);
        var arguments = TrendCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(alertId, arguments.alertId());
        assertEquals(now.plusHours(3L).plusDays(2L), arguments.fromDate());
        assertNull(arguments.exchange());
        assertNull(arguments.pair());
        assertNull(arguments.toDate());
        assertNull(arguments.fromPrice());
        assertNull(arguments.toPrice());
        assertNull(arguments.message());

        // test trend creation arguments
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + "  a message fe fe fe");
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + dateFrom);
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " eth/usd");
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME);
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dotxrp");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp");
        assertExceptionContains(IllegalArgumentException.class, TO_DATE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp " + "123");
        assertExceptionContains(IllegalArgumentException.class, TO_DATE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp " + dateFrom);
        assertExceptionContains(IllegalArgumentException.class, TO_PRICE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp " + dateFrom + " 12,6");
        assertExceptionContains(IllegalArgumentException.class, TO_DATE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp  12,6 " + dateFrom);
        assertExceptionContains(IllegalArgumentException.class, FROM_DATE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp " + dateFrom + " 12,6 " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, FROM_PRICE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp 12,2 " + dateFrom + "  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, TO_PRICE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp " + dateFrom + " 12,6 " + dateTo + " 123.334 ");
        assertExceptionContains(IllegalArgumentException.class, TO_DATE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp 12,45 " + dateFrom + " 23232.6 " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, MESSAGE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45 " + dateFrom + " 23232.6 " + dateTo + " to");
        assertExceptionContains(IllegalArgumentException.class, TO_DATE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45 " + dateFrom + " 23232.6 " + dateTo + " tr ud");
        assertExceptionContains(IllegalArgumentException.class, TO_DATE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        // use dash for date time separator in string commands
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45 " + dateFrom + " 23232.6   10/10/3010 20:01");
        assertExceptionContains(IllegalArgumentException.class, TO_DATE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45 " + dateFrom + " 23232.6 " + dateTo);
        arguments = TrendCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(BinanceClient.NAME, arguments.exchange());
        assertEquals("DOT/XRP", arguments.pair());
        assertEquals(now.plusHours(3L).plusDays(2L), arguments.fromDate());
        assertEquals(now.plusHours(9L).plusDays(2L), arguments.toDate());
        assertEquals(new BigDecimal("12.45"), arguments.fromPrice());
        assertEquals(new BigDecimal("23232.6"), arguments.toPrice());

        assertEquals("this is the message !!", arguments.message());

        // alert id negative
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + "  -1 " + dateFrom);
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> TrendCommand.arguments(commandContext[0]));

        // bad exchange
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " badExchange dot/xrp this is the message !! 12,45 " + dateFrom + " 23232.6 " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> TrendCommand.arguments(commandContext[0]));

        // negative prices
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! -12,45 " + dateFrom + " 23232.6 " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45 " + dateFrom + " -23232.6 " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! -12,45 " + dateFrom + " -23232.6 " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> TrendCommand.arguments(commandContext[0]));

        // prices too long
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45 " + dateFrom + " 123456789012345678901 " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "too long",
                () -> TrendCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 123456789012345678901 " + dateFrom + " 123 " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "too long",
                () -> TrendCommand.arguments(commandContext[0]));

        // dates in past OK
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45 10/10/1010-20:01 23232.6 10/10/1810-20:01");
        assertDoesNotThrow(() -> TrendCommand.arguments(commandContext[0]));

        // message too long
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp " + "aa".repeat(MESSAGE_MAX_LENGTH) + " 12,45 " + dateFrom + " 23232.6 " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "too long",
                () -> TrendCommand.arguments(commandContext[0]));

        // missing message
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, TrendCommand.NAME + " " + BinanceClient.NAME + " dot/xrp    \t 12,45 " + dateFrom + " 23232.6 " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, CHOICE_MESSAGE,
                () -> TrendCommand.arguments(commandContext[0]));
    }
}