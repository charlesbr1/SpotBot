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
import org.sbot.entities.chart.TimeFrame;
import org.sbot.exchanges.Exchange;
import org.sbot.exchanges.Exchanges;
import org.sbot.exchanges.binance.BinanceClient;
import org.sbot.services.context.Context;
import org.sbot.utils.DatesTest;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.EXCHANGE_ARGUMENT;
import static org.sbot.commands.CommandAdapter.PAIR_ARGUMENT;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;
import static org.sbot.utils.Dates.UTC;

class QuoteCommandTest {

    @Test
    void onCommand() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);

        var command = new QuoteCommand();
        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        Exchanges exchanges = mock();
        when(context.exchanges()).thenReturn(exchanges);
        when(exchanges.get(BinanceClient.NAME)).thenReturn(Optional.empty());

        var finalCommandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, QuoteCommand.NAME + "  " + BinanceClient.NAME + " btc/usd"));
        assertExceptionContains(IllegalArgumentException.class, BinanceClient.NAME, () -> command.onCommand(finalCommandContext));

        Exchange binance = mock();
        when(exchanges.get(BinanceClient.NAME)).thenReturn(Optional.of(binance));
        var commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, QuoteCommand.NAME + "  " + BinanceClient.NAME + " btc/usd"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(binance).getCandlesticks("BTC/USD", TimeFrame.ONE_MINUTE, 1);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        var message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("BTC/USD"));
    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, QuoteCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> QuoteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, QuoteCommand.NAME + "  a message fe fe fe");
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> QuoteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, QuoteCommand.NAME + "  " + BinanceClient.NAME);
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> QuoteCommand.arguments(commandContext[0]));


        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, QuoteCommand.NAME + "  " + BinanceClient.NAME + " feefefefefefefe");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> QuoteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, QuoteCommand.NAME + "  " + BinanceClient.NAME + " eth/ada too");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> QuoteCommand.arguments(commandContext[0]));


        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, QuoteCommand.NAME + "  " + BinanceClient.NAME + " eth/usd   " );
        var arguments = QuoteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals("ETH/USD", arguments.pair());
        assertEquals(BinanceClient.NAME, arguments.exchange());
    }
}