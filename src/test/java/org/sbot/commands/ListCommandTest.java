package org.sbot.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.services.context.Context;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.discord.Discord;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.ListCommand.*;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.AlertTest.createTestAlert;
import static org.sbot.entities.alerts.AlertTest.createTestAlertWithUserId;
import static org.sbot.utils.Dates.UTC;

class ListCommandTest {

    @Test
    void asNextCommand() {
        Arguments arguments = new Arguments(null, null, null, null, null, null);
        assertEquals("list all 99", arguments.asNextCommand());
        arguments = new Arguments(null, range, "selection", 123L, "eth", 12L);
        assertEquals("list <@123> eth range 111", arguments.asNextCommand());
        arguments = new Arguments(null, range, "selection", 123L, null, 12L);
        assertEquals("list <@123> range 111", arguments.asNextCommand());
        arguments = new Arguments(null, range, "selection", null, null, 12L);
        assertEquals("list all range 111", arguments.asNextCommand());
    }

    @Test
    void asDescription() {
        Arguments arguments = new Arguments(null, null, null, null, null, null);
        assertEquals("for all", arguments.asDescription());
        arguments = new Arguments(null, range, "selection", 123L, "eth", 12L);
        assertEquals("for user <@123> ticker or pair 'eth' with type range, and offset : 12", arguments.asDescription());
        arguments = new Arguments(null, trend, "selection", 123L, null, 12L);
        assertEquals("for user <@123> with type trend, and offset : 12", arguments.asDescription());
        arguments = new Arguments(null, remainder, "selection", null, null, 12L);
        assertEquals("for all with type remainder, and offset : 12", arguments.asDescription());
    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME);
        var arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertNull(arguments.type());
        assertEquals(LIST_ALL, arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        // id command
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  123 ");
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(123L, arguments.alertId());
        assertNull(arguments.type());
        assertNull(arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertNull(arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  123 az ");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  123 45 1");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  123 45 AE");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        // alert id negative
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  -123");
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> ListCommand.arguments(commandContext[0]));

        // settings, locales, timezone, exchanges commands
        for(var arg : List.of(LIST_SETTINGS, LIST_LOCALES, LIST_TIMEZONES, LIST_EXCHANGES)) {
            commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  " + arg);
            arguments = ListCommand.arguments(commandContext[0]);
            assertNotNull(arguments);
            assertNull(arguments.alertId());
            assertNull(arguments.type());
            assertEquals(arg, arguments.selection());
            assertNull(arguments.ownerId());
            assertNull(arguments.tickerOrPair());
            assertNull(arguments.offset());

            commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  " + arg + " sfs");
            assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                    () -> ListCommand.arguments(commandContext[0]));
        }

        // all, user, ticker or pair

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  azef efefe");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  azefefefel/lklk");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  btc/lk");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH/USD badType");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH USD");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  eth/btc USD");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  all btc ");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  all btc/lk ");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  all btc lk ");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  all range btc");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  all range 12 btc");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  all <@123> ");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  all range <@123> btc");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  all range <@123> 1&");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  all 12 <@123> range");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH/USD range 12 12");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH/USD range 12 az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH/USD  12 lm");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH/USD range 12 lm");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH/USD 12   <@321>");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH/USD range 12 <@321> az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@321> 12 23");
        assertExceptionContains(IllegalArgumentException.class, "ticker",
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@321> btc 23 1");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@321> ETH/USD 12 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@321> ETH/USD range 12 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH/USD range 12 <@321> ar 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH/USD 12 <@321> az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  ETH/USD 12 <@321> 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        // all
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + " all  " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertNull(arguments.type());
        assertEquals(LIST_ALL, arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + " all range " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(range, arguments.type());
        assertEquals(LIST_ALL, arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + " all trend 12 " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_ALL, arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(12L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + " all 33 trend  " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_ALL, arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(33L, arguments.offset());

        // user
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@321>  " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertNull(arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(321L, arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@321> xrp " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertNull(arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(321L, arguments.ownerId());
        assertEquals("XRP", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@321> 44 " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertNull(arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(321L, arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(44L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@321> remainder " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(remainder, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(321L, arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@987> remainder dot/usd " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(remainder, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@987>  dot/usd  trend" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@987>  14 dot/usd  trend" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(14L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@987>  14  trend dot/usd" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(14L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@987>   dot/usd 14 trend" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(14L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@987>   eth/usd  range 19" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(range, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("ETH/USD", arguments.tickerOrPair());
        assertEquals(19L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@987> trend  eth/usd  19" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("ETH/USD", arguments.tickerOrPair());
        assertEquals(19L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  <@97> trend  11 btc  " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(97L, arguments.ownerId());
        assertEquals("BTC", arguments.tickerOrPair());
        assertEquals(11L, arguments.offset());

        // ticker or pair
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  btc  " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertNull(arguments.type());
        assertEquals("btc", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("BTC", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  xrp   " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertNull(arguments.type());
        assertEquals("xrp", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("XRP", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + " eth/btc 44 " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertNull(arguments.type());
        assertEquals("eth/btc", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("ETH/BTC", arguments.tickerOrPair());
        assertEquals(44L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  dot/usd remainder " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(remainder, arguments.type());
        assertEquals("dot/usd", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + " usd  trend" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals("usd", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("USD", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + " dot/usd 14 trend" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals("dot/usd", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(14L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + " eth/usd  range 19" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(range, arguments.type());
        assertEquals("eth/usd", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("ETH/USD", arguments.tickerOrPair());
        assertEquals(19L, arguments.offset());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  eth/usd trend  139" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals("eth/usd", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("ETH/USD", arguments.tickerOrPair());
        assertEquals(139L, arguments.offset());
    }

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
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.system(UTC));
        AlertsDao alertsDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);
        Context.Services services = mock();
        when(services.discord()).thenReturn(discord);
        when(context.services()).thenReturn(services);

        var command = new ListCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        // settings, locales, timezone, exchanges commands
        for(var arg : List.of(LIST_SETTINGS, LIST_LOCALES, LIST_TIMEZONES, LIST_EXCHANGES)) {
            var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  " + arg));
            doNothing().when(commandContext).reply(anyList(), anyInt());
            command.onCommand(commandContext);
            ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
            verify(commandContext).reply(messagesReply.capture(), anyInt());
            List<Message> messages = messagesReply.getValue();
            assertEquals(LIST_TIMEZONES == arg ? 3 : 1, messages.size());
            assertEquals(1, messages.get(0).embeds().size());
            assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains(arg));
        }

        // id command
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  123"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(123L);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        List<Message> messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("123 not found"));

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  321"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.getAlertWithoutMessage(321L)).thenReturn(Optional.of(createTestAlert().withServerId(serverId)));
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(321L);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("TestAlert"));
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("serverId=123"));
        assertNull(messages.get(0).component()); // no edit menu

        // with same user -> editable
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  321"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.getAlertWithoutMessage(321L)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(serverId)));
        command.onCommand(commandContext);
        verify(alertsDao, times(2)).getAlertWithoutMessage(321L);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("TestAlert"));
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("serverId=123"));
        assertNotNull(messages.get(0).component());
        assertNotNull(messages.get(0).component().get(0)); // edit menu

        // not same user, admin -> editable
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, ListCommand.NAME + "  321"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.getAlertWithoutMessage(321L)).thenReturn(Optional.of(createTestAlertWithUserId(userId+1).withServerId(serverId)));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        command.onCommand(commandContext);
        verify(alertsDao, times(3)).getAlertWithoutMessage(321L);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("TestAlert"));
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("serverId=123"));
        assertNotNull(messages.get(0).component());
        assertNotNull(messages.get(0).component().get(0));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);

        // all, user, ticker or pair

    }

    @Test
    void toMessageWithEdit() {
    }

    @Test
    void listAlert() {
    }
}