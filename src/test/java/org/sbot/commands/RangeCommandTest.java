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
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.*;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.UpdateCommand.CHOICE_MESSAGE;
import static org.sbot.entities.settings.UserSettings.DEFAULT_LOCALE;
import static org.sbot.entities.settings.UserSettings.NO_USER;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.utils.ArgumentValidator.MESSAGE_MAX_LENGTH;
import static org.sbot.utils.Dates.UTC;

class RangeCommandTest {

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

        var command = new RangeCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " ada/btc this is the message !! 12,45 " + dateFrom + " 23232.6 " + dateTo));
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

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " ada/btc this is the message !! 12,45  23232.6 " + dateFrom + " " + dateTo));
        doNothing().when(commandContext).reply(anyList(), eq(command.responseTtlSeconds));
        when(settingsDao.userExists(DISCORD, userId)).thenReturn(true);
        command.onCommand(commandContext);

        verify(settingsDao, times(2)).userExists(DISCORD, userId);
        var alertReply = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).addAlert(alertReply.capture());
        var alert = alertReply.getValue();
        assertEquals(range, alert.type);
        assertEquals(userId, alert.userId);
        assertEquals(serverId, alert.serverId);
        assertNull(alert.lastTrigger);
        assertEquals(alert.fromDate, alert.listeningDate);
        assertEquals(now, alert.creationDate);
        assertEquals("ADA/BTC", alert.pair);
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
                .contains("Range".toUpperCase()));

        // check dates and price reordering

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " ada/btc this is the message !!  23232.6 12,45 " + dateFrom + " " + dateTo));
        doNothing().when(commandContext).reply(anyList(), eq(command.responseTtlSeconds));
        when(settingsDao.userExists(DISCORD, userId)).thenReturn(true);
        command.onCommand(commandContext);

        verify(settingsDao, times(3)).userExists(DISCORD, userId);
        alertReply = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao, times(2)).addAlert(alertReply.capture());
        alert = alertReply.getValue();
        assertEquals(range, alert.type);
        assertEquals(userId, alert.userId);
        assertEquals(serverId, alert.serverId);
        assertNull(alert.lastTrigger);
        assertEquals(alert.fromDate, alert.listeningDate);
        assertEquals(now, alert.creationDate);
        assertEquals("ADA/BTC", alert.pair);
        assertEquals(now.plusHours(3L).plusDays(2L), alert.fromDate);
        assertEquals(now.plusHours(9L).plusDays(2L), alert.toDate);
        assertEquals(new BigDecimal("12.45"), alert.fromPrice);
        assertEquals(new BigDecimal("23232.6"), alert.toPrice);
        assertEquals("this is the message !!", alert.message);
        assertEquals(MARGIN_DISABLED, alert.margin);
        assertEquals(DEFAULT_REPEAT, alert.repeat);
        assertEquals(DEFAULT_SNOOZE_HOURS, alert.snooze);

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " ada/btc this is the message !!  23232.6 12,45 " + dateTo + " " + dateFrom));
        doNothing().when(commandContext).reply(anyList(), eq(command.responseTtlSeconds));
        when(settingsDao.userExists(DISCORD, userId)).thenReturn(true);
        command.onCommand(commandContext);

        verify(settingsDao, times(4)).userExists(DISCORD, userId);
        alertReply = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao, times(3)).addAlert(alertReply.capture());
        alert = alertReply.getValue();
        assertEquals(range, alert.type);
        assertEquals(userId, alert.userId);
        assertEquals(serverId, alert.serverId);
        assertNull(alert.lastTrigger);
        assertEquals(alert.fromDate, alert.listeningDate);
        assertEquals(now, alert.creationDate);
        assertEquals("ADA/BTC", alert.pair);
        assertEquals(now.plusHours(3L).plusDays(2L), alert.fromDate);
        assertEquals(now.plusHours(9L).plusDays(2L), alert.toDate);
        assertEquals(new BigDecimal("12.45"), alert.fromPrice);
        assertEquals(new BigDecimal("23232.6"), alert.toPrice);
        assertEquals("this is the message !!", alert.message);
        assertEquals(MARGIN_DISABLED, alert.margin);
        assertEquals(DEFAULT_REPEAT, alert.repeat);
        assertEquals(DEFAULT_SNOOZE_HOURS, alert.snooze);

        // check single price range

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " ada/btc this is the message !!  12,45 "));
        doNothing().when(commandContext).reply(anyList(), eq(command.responseTtlSeconds));
        when(settingsDao.userExists(DISCORD, userId)).thenReturn(true);
        command.onCommand(commandContext);

        verify(settingsDao, times(5)).userExists(DISCORD, userId);
        alertReply = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao, times(4)).addAlert(alertReply.capture());
        alert = alertReply.getValue();
        assertEquals(range, alert.type);
        assertEquals(userId, alert.userId);
        assertEquals(serverId, alert.serverId);
        assertNull(alert.lastTrigger);
        assertEquals(now, alert.listeningDate);
        assertEquals(now, alert.creationDate);
        assertEquals("ADA/BTC", alert.pair);
        assertNull(alert.fromDate);
        assertNull(alert.toDate);
        assertEquals(new BigDecimal("12.45"), alert.fromPrice);
        assertEquals(new BigDecimal("12.45"), alert.toPrice);
        assertEquals("this is the message !!", alert.message);
        assertEquals(MARGIN_DISABLED, alert.margin);
        assertEquals(DEFAULT_REPEAT, alert.repeat);
        assertEquals(DEFAULT_SNOOZE_HOURS, alert.snooze);


        // check from dates in past

        var fc1 = spy(CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " ada/btc this is the message !!  12,45 12/12/1012-21:00 12/12/1111-20:00"));
        doNothing().when(fc1).reply(anyList(), eq(command.responseTtlSeconds));
        assertThrows(IllegalArgumentException.class, () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " ada/btc this is the message !!  12,45 12/12/1012-21:00 12/12/3111-20:00"));
        doNothing().when(fc2).reply(anyList(), eq(command.responseTtlSeconds));
        assertThrows(IllegalArgumentException.class, () -> command.onCommand(fc2));

        var fc3 = spy(CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " ada/btc this is the message !!  12,45 12/12/3012-21:00 12/12/1111-20:00"));
        doNothing().when(fc3).reply(anyList(), eq(command.responseTtlSeconds));
        assertThrows(IllegalArgumentException.class, () -> command.onCommand(fc3));

        // empty message
        var fc4 = spy(CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " ada/btc 12,45 12/12/3012-21:00 12/12/3111-20:00"));
        doNothing().when(fc3).reply(anyList(), eq(command.responseTtlSeconds));
        assertThrows(IllegalArgumentException.class, () -> command.onCommand(fc4));
    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        var settings = new Settings(NO_USER, ServerSettings.PRIVATE_SERVER);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + "  a message fe fe fe");
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        String dateFrom = Dates.formatUTC(commandContext[0].locale, now.plusHours(3L).plusDays(2L));
        String dateTo = Dates.formatUTC(commandContext[0].locale, now.plusHours(9L).plusDays(2L));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + dateFrom);
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " eth/usd");
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME);
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp");
        assertExceptionContains(IllegalArgumentException.class, MESSAGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp " + "123");
        assertExceptionContains(IllegalArgumentException.class, MESSAGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp " + "a de fe");
        assertExceptionContains(IllegalArgumentException.class, LOW_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp " + dateFrom);
        assertExceptionContains(IllegalArgumentException.class, MESSAGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp  12,6 " + dateFrom);
        assertExceptionContains(IllegalArgumentException.class, MESSAGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp  12,6 " + dateTo + " " + dateFrom);
        assertExceptionContains(IllegalArgumentException.class, MESSAGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp 12,2 45 " + dateFrom + "  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, MESSAGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp 12,2 45 " + dateFrom);
        assertExceptionContains(IllegalArgumentException.class, MESSAGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));


        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6 " + dateFrom + "  " + dateTo + " tr");
        assertExceptionContains(IllegalArgumentException.class, LOW_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45 " + dateFrom + " 23232.6 " + dateTo + " tr ud");
        assertExceptionContains(IllegalArgumentException.class, LOW_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        // use dash for date time separator in string commands
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6 " + dateFrom + "  10/10/3010 20:01");
        assertExceptionContains(IllegalArgumentException.class, LOW_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6 " + dateFrom + "  " + dateTo);
        var arguments = RangeCommand.arguments(commandContext[0], now);
        assertNotNull(arguments);
        assertEquals(BinanceClient.NAME, arguments.exchange());
        assertEquals("DOT/XRP", arguments.pair());
        assertEquals(now.plusHours(3L).plusDays(2L), arguments.fromDate());
        assertEquals(now.plusHours(9L).plusDays(2L), arguments.toDate());
        assertEquals(new BigDecimal("12.45"), arguments.low());
        assertEquals(new BigDecimal("23232.6"), arguments.high());
        assertEquals("this is the message !!", arguments.message());

        // test optional arguments, no to price (=low) no dates
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !!   123.6");
        arguments = RangeCommand.arguments(commandContext[0], now);
        assertNotNull(arguments);
        assertEquals(BinanceClient.NAME, arguments.exchange());
        assertEquals("DOT/XRP", arguments.pair());
        assertNull(arguments.fromDate());
        assertNull(arguments.toDate());
        assertEquals(new BigDecimal("123.6"), arguments.low());
        assertEquals(new BigDecimal("123.6"), arguments.high());
        assertEquals("this is the message !!", arguments.message());

        // test optional arguments, no dates
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !!   123.6 322 ");
        arguments = RangeCommand.arguments(commandContext[0], now);
        assertNotNull(arguments);
        assertEquals(BinanceClient.NAME, arguments.exchange());
        assertEquals("DOT/XRP", arguments.pair());
        assertNull(arguments.fromDate());
        assertNull(arguments.toDate());
        assertEquals(new BigDecimal("123.6"), arguments.low());
        assertEquals(new BigDecimal("322"), arguments.high());
        assertEquals("this is the message !!", arguments.message());

        // test optional arguments, no to price, dates
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !!   123.6 " + dateFrom);
        arguments = RangeCommand.arguments(commandContext[0], now);
        assertNotNull(arguments);
        assertEquals(BinanceClient.NAME, arguments.exchange());
        assertEquals("DOT/XRP", arguments.pair());
        assertEquals(now.plusHours(3L).plusDays(2L), arguments.fromDate());
        assertNull(arguments.toDate());
        assertEquals(new BigDecimal("123.6"), arguments.low());
        assertEquals(new BigDecimal("123.6"), arguments.high());
        assertEquals("this is the message !!", arguments.message());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !!   123.6 " + dateFrom + " " + dateTo);
        arguments = RangeCommand.arguments(commandContext[0], now);
        assertNotNull(arguments);
        assertEquals(BinanceClient.NAME, arguments.exchange());
        assertEquals("DOT/XRP", arguments.pair());
        assertEquals(now.plusHours(3L).plusDays(2L), arguments.fromDate());
        assertEquals(now.plusHours(9L).plusDays(2L), arguments.toDate());
        assertEquals(new BigDecimal("123.6"), arguments.low());
        assertEquals(new BigDecimal("123.6"), arguments.high());
        assertEquals("this is the message !!", arguments.message());


        // bad exchange
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " badExchange dot/xrp this is the message !! 12,45  23232.6 " + dateFrom + "  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> RangeCommand.arguments(commandContext[0], now));

        // negative prices
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! -12,45  23232.6 " + dateFrom + "  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  -23232.6 " + dateFrom + "  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! -12,45  -23232.6 " + dateFrom + "  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> RangeCommand.arguments(commandContext[0], now));

        // prices too long
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  123456789012345678901 " + dateFrom + "  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "too long",
                () -> RangeCommand.arguments(commandContext[0], now));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 123456789012345678901  123 " + dateFrom + "  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "too long",
                () -> RangeCommand.arguments(commandContext[0], now));

        // test dates in past
        // from date in past ko
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6 11/11/1011-11:11  ");
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RangeCommand.arguments(commandContext[0], now));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6 11/11/1011-11:11  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RangeCommand.arguments(commandContext[0], now));

        // from date now ok
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6 " + Dates.formatUTC(Locale.UK, now.minusSeconds(1L)));
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RangeCommand.arguments(commandContext[0], now));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6 " + Dates.formatUTC(Locale.UK, now));
        assertDoesNotThrow(() -> RangeCommand.arguments(commandContext[0], now));

        // to date in past ko
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6 11/11/1011-11:11  11/11/1111-11:11 ");
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RangeCommand.arguments(commandContext[0], now));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6 11/11/3011-11:11  11/11/1111-11:11 ");
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RangeCommand.arguments(commandContext[0], now));

        // to date now ko
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6  " + Dates.formatUTC(Locale.UK, now) + " " + Dates.formatUTC(Locale.UK, now));
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RangeCommand.arguments(commandContext[0], now));
        // to date now + 1h ok
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6  " + Dates.formatUTC(Locale.UK, now) + " " + Dates.formatUTC(Locale.UK, now.plusHours(1L).minusSeconds(1L)));
        assertExceptionContains(IllegalArgumentException.class, "date",
                () -> RangeCommand.arguments(commandContext[0], now));
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp this is the message !! 12,45  23232.6  " + Dates.formatUTC(Locale.UK, now) + " " + Dates.formatUTC(Locale.UK, now.plusHours(1L)));
        assertDoesNotThrow(() -> RangeCommand.arguments(commandContext[0], now));

        // message too long
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp " + "aa".repeat(MESSAGE_MAX_LENGTH) + " 12,45  23232.6 " + dateFrom + "  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, "too long",
                () -> RangeCommand.arguments(commandContext[0], now));

        // missing message
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, RangeCommand.NAME + " " + BinanceClient.NAME + " dot/xrp    12,45  23232.6 " + dateFrom + "  " + dateTo);
        assertExceptionContains(IllegalArgumentException.class, CHOICE_MESSAGE,
                () -> RangeCommand.arguments(commandContext[0], now));
    }
}