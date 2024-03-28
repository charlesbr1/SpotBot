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
import org.sbot.entities.settings.UserSettings;
import org.sbot.services.context.Context;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
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
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.utils.Dates.UTC;

class ListCommandTest {

    @Test
    void asNextCommand() {
        Arguments arguments = new Arguments(null, null, null, null, null, null);
        assertEquals("list all 22", arguments.asNextCommand(22));
        arguments = new Arguments(null, range, "selection", 123L, "eth", 12L);
        assertEquals("list <@123> eth range 34", arguments.asNextCommand(22));
        arguments = new Arguments(null, range, "selection", 123L, null, 12L);
        assertEquals("list <@123> range 24", arguments.asNextCommand(12));
        arguments = new Arguments(null, range, "selection", null, null, 12L);
        assertEquals("list all range 20", arguments.asNextCommand(8));
    }

    @Test
    void asDescription() {
        Arguments arguments = new Arguments(null, null, null, null, null, null);
        assertEquals("for all", arguments.asDescription());
        arguments = new Arguments(null, range, "selection", 123L, "eth", 12L);
        assertEquals("for user <@123> ticker or pair eth with type range, and offset : 12", arguments.asDescription());
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
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME);
        var arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertNull(arguments.type());
        assertEquals(LIST_ALL, arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        // id command
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  123 ");
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(List.of(123L), arguments.alertIds());
        assertNull(arguments.type());
        assertNull(arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertNull(arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  123 az ");
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  123 45 AE");
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT,
                () -> ListCommand.arguments(commandContext[0]));

        // alert id negative
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  -123");
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  123 -4");
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> ListCommand.arguments(commandContext[0]));

        // settings, locales, timezone, exchanges commands
        for(var arg : List.of(LIST_SETTINGS, LIST_LOCALES, LIST_TIMEZONES, LIST_EXCHANGES)) {
            commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  " + arg);
            arguments = ListCommand.arguments(commandContext[0]);
            assertNotNull(arguments);
            assertNull(arguments.alertIds());
            assertNull(arguments.type());
            assertEquals(arg, arguments.selection());
            assertNull(arguments.ownerId());
            assertNull(arguments.tickerOrPair());
            assertNull(arguments.offset());

            commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  " + arg + " sfs");
            assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                    () -> ListCommand.arguments(commandContext[0]));
        }

        // all, user, ticker or pair

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  azef efefe");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  azefefefel/lklk");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  bt/k");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH/USD badType");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH USD");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  eth/btc USD");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  all btc ");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  all btc/lk ");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  all btc lk ");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  all range btc");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  all range 12 btc");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  all <@123> ");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  all range <@123> btc");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  all range <@123> 1&");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  all 12 <@123> range");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH/USD range 12 12");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH/USD range 12 az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH/USD  12 lm");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH/USD range 12 lm");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH/USD 12   <@321>");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH/USD range 12 <@321> az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@321> 12 2");
        assertExceptionContains(IllegalArgumentException.class, "ticker",
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@321> btc 23 1");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@321> ETH/USD 12 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@321> ETH/USD range 12 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH/USD range 12 <@321> ar 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH/USD 12 <@321> az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  ETH/USD 12 <@321> 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> ListCommand.arguments(commandContext[0]));

        // all
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " all  " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertNull(arguments.type());
        assertEquals(LIST_ALL, arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " all range " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(range, arguments.type());
        assertEquals(LIST_ALL, arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " all trend 12 " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_ALL, arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(12L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " all 33 trend  " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_ALL, arguments.selection());
        assertNull(arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(33L, arguments.offset());

        // user
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@321>  " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertNull(arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(321L, arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@321> xrp " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertNull(arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(321L, arguments.ownerId());
        assertEquals("XRP", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@321> 44 " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertNull(arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(321L, arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(44L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@321> remainder " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(remainder, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(321L, arguments.ownerId());
        assertNull(arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@987> remainder dot/usd " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(remainder, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@987>  dot/usd  trend" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@987>  14 dot/usd  trend" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(14L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@987>  14  trend dot/usd" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(14L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@987>   dot/usd 14 trend" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(14L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@987>   eth/usd  range 19" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(range, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("ETH/USD", arguments.tickerOrPair());
        assertEquals(19L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@987> trend  eth/usd  19" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(987L, arguments.ownerId());
        assertEquals("ETH/USD", arguments.tickerOrPair());
        assertEquals(19L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  <@97> trend  11 btc  " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(trend, arguments.type());
        assertEquals(LIST_USER, arguments.selection());
        assertEquals(97L, arguments.ownerId());
        assertEquals("BTC", arguments.tickerOrPair());
        assertEquals(11L, arguments.offset());

        // ticker or pair
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  btc  " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertNull(arguments.type());
        assertEquals("btc", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("BTC", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  xrp   " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertNull(arguments.type());
        assertEquals("xrp", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("XRP", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " eth/btc 44 " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertNull(arguments.type());
        assertEquals("eth/btc", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("ETH/BTC", arguments.tickerOrPair());
        assertEquals(44L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  dot/usd remainder " );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(remainder, arguments.type());
        assertEquals("dot/usd", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " usd  trend" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(trend, arguments.type());
        assertEquals("usd", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("USD", arguments.tickerOrPair());
        assertEquals(0L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " dot/usd 14 trend" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(trend, arguments.type());
        assertEquals("dot/usd", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("DOT/USD", arguments.tickerOrPair());
        assertEquals(14L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " eth/usd  range 19" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
        assertEquals(range, arguments.type());
        assertEquals("eth/usd", arguments.selection());
        assertNull(arguments.ownerId());
        assertEquals("ETH/USD", arguments.tickerOrPair());
        assertEquals(19L, arguments.offset());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  eth/usd trend  139" );
        arguments = ListCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertIds());
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
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);

        var command = new ListCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        // settings, locales, timezone, exchanges commands
        for(var arg : List.of(LIST_SETTINGS, LIST_LOCALES, LIST_TIMEZONES, LIST_EXCHANGES)) {
            var commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  " + arg));
            doNothing().when(commandContext).reply(anyList(), anyInt());
            command.onCommand(commandContext);
            ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
            verify(commandContext).reply(messagesReply.capture(), anyInt());
            List<Message> messages = messagesReply.getValue();
            assertEquals(LIST_TIMEZONES == arg ? 3 : 1, messages.size());
            assertEquals(1, messages.get(0).embeds().size());
            assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains(arg));
        }

        // id command, public channel, not found
        var commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  123"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlert(TEST_CLIENT_TYPE, 123L);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        List<Message> messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        var embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("123 not found"));
        assertNull(embed.getFooter());

        // id command, public channel, not same user, found, not editable
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  321"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        var alert = createTestAlert().withServerId(serverId);
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, 321L)).thenReturn(Optional.of(alert));
        command.onCommand(commandContext);
        verify(alertsDao).getAlert(TEST_CLIENT_TYPE, 321L);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=123"));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertNull(embed.getFooter());
        assertNull(messages.get(0).component()); // no edit menu

        // id command, public channel, same user, found, editable
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  321"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        alert = createTestAlertWithUserId(userId).withServerId(serverId);
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, 321L)).thenReturn(Optional.of(alert));
        command.onCommand(commandContext);
        verify(alertsDao, times(2)).getAlert(TEST_CLIENT_TYPE, 321L);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=123"));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertNull(embed.getFooter());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0)); // edit menu

        // id command, many ids, public channel, same user, found, editable
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  321 333 44"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        alert = createTestAlertWithUserId(userId).withServerId(serverId);
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, 321L)).thenReturn(Optional.of(alert));
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, 333L)).thenReturn(Optional.of(alert));
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, 44L)).thenReturn(Optional.of(alert));
        command.onCommand(commandContext);
        verify(alertsDao, times(3)).getAlert(TEST_CLIENT_TYPE, 321L);
        verify(alertsDao).getAlert(TEST_CLIENT_TYPE, 333L);
        verify(alertsDao).getAlert(TEST_CLIENT_TYPE, 44L);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(3, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertEquals(1, messages.get(2).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        assertNotNull(messages.get(1).embeds().get(0));
        assertNotNull(messages.get(2).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=123"));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertNull(embed.getFooter());
        assertEquals(1, messages.get(0).component().size());
        assertEquals(1, messages.get(1).component().size());
        assertEquals(1, messages.get(2).component().size());
        assertNotNull(messages.get(0).component().get(0)); // edit menu
        assertNotNull(messages.get(1).component().get(0)); // edit menu
        assertNotNull(messages.get(2).component().get(0)); // edit menu

        // id command, private channel, not same user, not found
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  321"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        alert = createTestAlertWithUserId(userId + 1);
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, 321L)).thenReturn(Optional.of(alert));
        command.onCommand(commandContext);
        verify(alertsDao, times(4)).getAlert(TEST_CLIENT_TYPE, 321L);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("321 not found"));
        assertNull(embed.getFooter());
        assertNull(messages.get(0).component()); // no edit menu


        // id command, private channel with same user -> editable
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  321"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, 321L)).thenReturn(Optional.of(createTestAlertWithUserId(userId)));
        command.onCommand(commandContext);
        verify(alertsDao, times(5)).getAlert(TEST_CLIENT_TYPE, 321L);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + TEST_SERVER_ID));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertNull(embed.getFooter());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0)); // edit menu

        // id command, public channel not same user, admin -> found, editable
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "  321"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, 321L)).thenReturn(Optional.of(createTestAlertWithUserId(userId+1).withServerId(serverId)));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        command.onCommand(commandContext);
        verify(alertsDao, times(6)).getAlert(TEST_CLIENT_TYPE, 321L);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertNull(embed.getFooter());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);

        // all, private channel, not editable
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " all  3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null))).thenReturn(7L);
        alert = createTestAlertWithUserId(userId);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null), 3, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert, alert));
        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null), 3, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(2, messages.size());
        assertEquals(3, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + TEST_SERVER_ID));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(4/7)", embed.getFooter().getText());
        assertEquals("(5/7)", messages.get(0).embeds().get(1).build().getFooter().getText());
        assertEquals("(6/7)", messages.get(0).embeds().get(2).build().getFooter().getText());
        assertTrue(messages.get(1).embeds().get(0).build().getDescription().contains("More results found"));
        assertNull(messages.get(0).component());

        // all, with type, private channel, editable
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + "all remainder 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, remainder))).thenReturn(6L);
        alert = createTestAlertWithUserId(userId);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, remainder), 3, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert, alert));
        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, remainder));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, remainder), 3, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(3, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + TEST_SERVER_ID));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(4/6)", embed.getFooter().getText());
        assertEquals("(5/6)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertEquals("(6/6)", messages.get(2).embeds().get(0).build().getFooter().getText());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));

        // all, public channel, not editable
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " all  3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null))).thenReturn(7L);
        alert = createTestAlertWithUserId(userId).withServerId(serverId);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null), 3, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert, alert));
        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null), 3, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(2, messages.size());
        assertEquals(3, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(4/7)", embed.getFooter().getText());
        assertEquals("(5/7)", messages.get(0).embeds().get(1).build().getFooter().getText());
        assertEquals("(6/7)", messages.get(0).embeds().get(2).build().getFooter().getText());
        assertTrue(messages.get(1).embeds().get(0).build().getDescription().contains("More results found"));
        assertNull(messages.get(0).component());

        // all, with type, public channel, editable
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " all range "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, range))).thenReturn(4L);
        alert = createTestAlertWithUserId(userId).withServerId(serverId);
        var alert2 = createTestAlertWithUserId(userId).withServerId(serverId);
        var alert3 = createTestAlertWithUserId(userId+1).withServerId(serverId);
        var alert4 = createTestAlertWithUserId(userId+1).withServerId(serverId);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, range), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert2, alert3, alert4));
        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, range));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, range), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(3, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertEquals(2, messages.get(2).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/4)", embed.getFooter().getText());
        assertEquals("(2/4)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertEquals("(3/4)", messages.get(2).embeds().get(0).build().getFooter().getText());
        assertEquals("(4/4)", messages.get(2).embeds().get(1).build().getFooter().getText());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));
        assertNotNull(messages.get(1).component());
        assertEquals(1, messages.get(1).component().size());
        assertNotNull(messages.get(1).component().get(0));
        assertNull(messages.get(2).component()); // only 2 first alerts editables

        // all, with type, public channel, admin, not all editable
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " all remainder "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, remainder))).thenReturn(4L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, remainder), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert2, alert3, alert4));
        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, remainder));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, remainder), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(3, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertEquals(2, messages.get(2).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/4)", embed.getFooter().getText());
        assertEquals("(2/4)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertEquals("(3/4)", messages.get(2).embeds().get(0).build().getFooter().getText());
        assertEquals("(4/4)", messages.get(2).embeds().get(1).build().getFooter().getText());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));
        assertNotNull(messages.get(1).component());
        assertEquals(1, messages.get(1).component().size());
        assertNotNull(messages.get(1).component().get(0));
        assertNull(messages.get(2).component()); // only 2 first alerts editables
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);

        // user, same user, with type, private channel, found, editable
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " <@" + userId + "> " + " range "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, range))).thenReturn(2L);
        alert = createTestAlertWithUserId(userId).withServerId(serverId);
        alert2 = createTestAlertWithUserId(userId).withServerId(serverId);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, range), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert2));
        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, range));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, range), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(2, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/2)", embed.getFooter().getText());
        assertEquals("(2/2)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));
        assertNotNull(messages.get(1).component());
        assertEquals(1, messages.get(1).component().size());
        assertNotNull(messages.get(1).component().get(0));

        // user, not same user, private channel, denied
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " <@" + userId+1 + "> " + " range "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId+1, range));
        verify(alertsDao, never()).getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId+1, range), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("not allowed"));


        // user all, same user, public channel, not editable
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " <@" + userId + ">  "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null))).thenReturn(2L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert2));

        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(2, messages.get(0).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/2)", embed.getFooter().getText());
        assertEquals("(2/2)", messages.get(0).embeds().get(1).build().getFooter().getText());
        assertNull(messages.get(0).component());

        // user all, same user, with type, public channel, editable
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " <@" + userId + "> remainder "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, remainder))).thenReturn(2L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, remainder), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert2));

        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, remainder));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, remainder), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(2, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/2)", embed.getFooter().getText());
        assertEquals("(2/2)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));
        assertNotNull(messages.get(1).component());
        assertEquals(1, messages.get(1).component().size());
        assertNotNull(messages.get(1).component().get(0));

        // user all, public channel, admin, all editable
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " <@345>  "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 345L, null))).thenReturn(5L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 345L, null), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert2, alert3, alert4));

        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 345L, null));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 345L, null), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(5, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertEquals(1, messages.get(2).embeds().size());
        assertEquals(1, messages.get(3).embeds().size());
        assertEquals(1, messages.get(4).embeds().size());
        assertTrue(messages.get(4).embeds().get(0).build().getDescription().contains("More results found"));
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/5)", embed.getFooter().getText());
        assertEquals("(2/5)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertEquals("(3/5)", messages.get(2).embeds().get(0).build().getFooter().getText());
        assertEquals("(4/5)", messages.get(3).embeds().get(0).build().getFooter().getText());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));
        assertNotNull(messages.get(1).component());
        assertEquals(1, messages.get(1).component().size());
        assertNotNull(messages.get(1).component().get(0));
        assertNotNull(messages.get(2).component());
        assertEquals(1, messages.get(2).component().size());
        assertNotNull(messages.get(2).component().get(0));
        assertNotNull(messages.get(3).component());
        assertEquals(1, messages.get(3).component().size());
        assertNotNull(messages.get(3).component().get(0));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);

        // user, not same user, public channel, not admin, not editable
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " <@543>  "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 543, null))).thenReturn(2L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 543, null), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert2));

        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 543, null));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 543, null), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(2, messages.get(0).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/2)", embed.getFooter().getText());
        assertEquals("(2/2)", messages.get(0).embeds().get(1).build().getFooter().getText());
        assertNull(messages.get(0).component());

        // user, not same user, public channel, admin, all editable
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " <@9989>  "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 9989, null))).thenReturn(2L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 9989, null), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert4));

        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 9989, null));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, 9989, null), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(2, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/2)", embed.getFooter().getText());
        assertEquals("(2/2)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));
        assertNotNull(messages.get(1).component());
        assertEquals(1, messages.get(1).component().size());
        assertNotNull(messages.get(1).component().get(0));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);

        // ticker or pair, private channel, editable
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " eth/usd  "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null).withTickerOrPair("ETH/USD"))).thenReturn(2L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null).withTickerOrPair("ETH/USD"), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert2));

        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null).withTickerOrPair("ETH/USD"));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null).withTickerOrPair("ETH/USD"), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(2, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/2)", embed.getFooter().getText());
        assertEquals("(2/2)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));
        assertNotNull(messages.get(1).component());
        assertEquals(1, messages.get(1).component().size());
        assertNotNull(messages.get(1).component().get(0));

        // ticker or pair, with type, private channel, editable
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " range eth/usd  "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, range).withTickerOrPair("ETH/USD"))).thenReturn(2L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, range).withTickerOrPair("ETH/USD"), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert2));

        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, range).withTickerOrPair("ETH/USD"));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, range).withTickerOrPair("ETH/USD"), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(2, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/2)", embed.getFooter().getText());
        assertEquals("(2/2)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));
        assertNotNull(messages.get(1).component());
        assertEquals(1, messages.get(1).component().size());
        assertNotNull(messages.get(1).component().get(0));

        // ticker or pair, public channel, editable
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " dot/usd  "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null).withTickerOrPair("DOT/USD"))).thenReturn(4L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null).withTickerOrPair("DOT/USD"), 0, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert3, alert4, alert, alert2));

        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null).withTickerOrPair("DOT/USD"));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, null).withTickerOrPair("DOT/USD"), 0, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(3, messages.size());
        assertEquals(2, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertEquals(1, messages.get(2).embeds().size());
        assertNotNull(messages.get(1).embeds().get(0));
        embed = messages.get(1).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(1/4)", messages.get(0).embeds().get(0).build().getFooter().getText());
        assertEquals("(2/4)", messages.get(0).embeds().get(1).build().getFooter().getText());
        assertEquals("(3/4)", embed.getFooter().getText());
        assertEquals("(4/4)", messages.get(2).embeds().get(0).build().getFooter().getText());
        assertNotNull(messages.get(1).component());
        assertEquals(1, messages.get(1).component().size());
        assertNotNull(messages.get(1).component().get(0));
        assertNotNull(messages.get(2).component());
        assertEquals(1, messages.get(2).component().size());
        assertNotNull(messages.get(2).component().get(0));

        // ticker or pair, with type, public channel, editable
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " dot/usd  trend 4"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, trend).withTickerOrPair("DOT/USD"))).thenReturn(8L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, trend).withTickerOrPair("DOT/USD"), 4, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert, alert4, alert3, alert2));

        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, trend).withTickerOrPair("DOT/USD"));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, trend).withTickerOrPair("DOT/USD"), 4, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(3, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(2, messages.get(1).embeds().size());
        assertEquals(1, messages.get(2).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(5/8)", embed.getFooter().getText());
        assertEquals("(6/8)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertEquals("(7/8)", messages.get(1).embeds().get(1).build().getFooter().getText());
        assertEquals("(8/8)", messages.get(2).embeds().get(0).build().getFooter().getText());
        assertNull(messages.get(1).component());
        assertNotNull(messages.get(0).component());
        assertEquals(1, messages.get(0).component().size());
        assertNotNull(messages.get(0).component().get(0));
        assertNotNull(messages.get(2).component());
        assertEquals(1, messages.get(2).component().size());
        assertNotNull(messages.get(2).component().get(0));

        // different alerts order
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, ListCommand.NAME + " dot/usd  remainder 4"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(alertsDao.countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, remainder).withTickerOrPair("DOT/USD"))).thenReturn(8L);
        when(alertsDao.getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, remainder).withTickerOrPair("DOT/USD"), 4, MESSAGE_LIST_CHUNK)).thenReturn(List.of(alert4, alert, alert2, alert3));

        command.onCommand(commandContext);
        verify(alertsDao).countAlerts(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, remainder).withTickerOrPair("DOT/USD"));
        verify(alertsDao).getAlertsOrderByPairUserIdId(SelectionFilter.ofServer(TEST_CLIENT_TYPE, serverId, remainder).withTickerOrPair("DOT/USD"), 4, MESSAGE_LIST_CHUNK);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(4, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertEquals(1, messages.get(1).embeds().size());
        assertEquals(1, messages.get(2).embeds().size());
        assertEquals(1, messages.get(3).embeds().size());
        assertNotNull(messages.get(0).embeds().get(0));
        embed = messages.get(0).embeds().get(0).build();
        assertTrue(embed.getDescription().contains("TestAlert"));
        assertTrue(embed.getDescription().contains("serverId=" + serverId));
        assertTrue(embed.getTitle().contains(alert.pair));
        assertTrue(embed.getTitle().contains(alert.message));
        assertEquals("(5/8)", embed.getFooter().getText());
        assertEquals("(6/8)", messages.get(1).embeds().get(0).build().getFooter().getText());
        assertEquals("(7/8)", messages.get(2).embeds().get(0).build().getFooter().getText());
        assertEquals("(8/8)", messages.get(3).embeds().get(0).build().getFooter().getText());
        assertNull(messages.get(0).component());
        assertNotNull(messages.get(1).component());
        assertEquals(1, messages.get(1).component().size());
        assertNotNull(messages.get(1).component().get(0));
        assertNotNull(messages.get(2).component());
        assertEquals(1, messages.get(2).component().size());
        assertNotNull(messages.get(2).component().get(0));
        assertNull(messages.get(3).component());
    }
}