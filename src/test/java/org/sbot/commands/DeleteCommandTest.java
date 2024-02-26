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
import org.sbot.services.context.Context.DataServices;
import org.sbot.services.context.Context.Services;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.discord.Discord;

import java.util.List;
import java.util.Optional;

import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.*;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.DeleteCommand.DELETE_ALL;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;
import static org.sbot.entities.alerts.Alert.PRIVATE_ALERT;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.AlertTest.createTestAlertWithUserId;

class DeleteCommandTest {

    @Test
    void onCommandDeleteById() {
        long alertId = 787L;
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
        AlertsDao alertsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);
        Services services = mock();
        when(services.discord()).thenReturn(discord);
        when(context.services()).thenReturn(services);

        var command = new DeleteCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        // delete by id, not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).deleteAlert(alertId);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        List<Message> messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // delete by id, alert exists, not same user, private channel, not found
        alertId++;
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId)));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).deleteAlert(alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // delete by id, alert exists, same user, private channel, ok
        alertId++;
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(serverId)));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao).deleteAlert(alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("deleted"));

        alertId++;
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(PRIVATE_ALERT)));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao).deleteAlert(alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("deleted"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any());

        // delete by id, alert exists, not same user, not same channel, not found
        when(messageReceivedEvent.getMember()).thenReturn(member);
        alertId++;
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).deleteAlert(alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // delete by id, alert exists, same user, not same channel, not found
        alertId++;
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(serverId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).deleteAlert(alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // delete by id, alert exists, not same user, same channel, access denied
        alertId++;
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).deleteAlert(alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // delete by id, alert exists, not same user, same channel, admin user, ok
        alertId++;
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId)));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao).deleteAlert(alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord).sendPrivateMessage(eq(userId + 1), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("deleted"));

        // delete by id, alert exists, same user, same channel, ok
        alertId++;
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(serverId)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao).deleteAlert(alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(eq(userId), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("deleted"));
    }

    @Test
    void onCommandDeleteByFilter() {

    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, EXCHANGE_ARGUMENT,
                () -> QuoteCommand.arguments(commandContext[0]));

        // id command
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  123 az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  123 45");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  123");
        var arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(123L, arguments.alertId());
        assertNull(arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        // alert id negative
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  -123");
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> DeleteCommand.arguments(commandContext[0]));

        // filter command
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  az");
        assertExceptionContains(IllegalArgumentException.class, "ticker",
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  azef efefe");
        assertExceptionContains(IllegalArgumentException.class, TYPE_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  azefefefel/lklk");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  btc/lk");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  ETH/USD badType");
        assertExceptionContains(IllegalArgumentException.class, TYPE_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + "  ETH/USD range <@321> az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + " eth/usd <@32>  " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals("eth/usd", arguments.tickerOrPair());
        assertEquals(32L, arguments.ownerId());
        assertNull(arguments.type());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + " dot/usd  range  " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals("dot/usd", arguments.tickerOrPair());
        assertNull(arguments.ownerId());
        assertEquals(range, arguments.type());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + " eth/usd  trend   <@321> " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals("eth/usd", arguments.tickerOrPair());
        assertEquals(321L, arguments.ownerId());
        assertEquals(trend, arguments.type());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + " btc/usd <@3210>  remainder" );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals("btc/usd", arguments.tickerOrPair());
        assertEquals(3210L, arguments.ownerId());
        assertEquals(remainder, arguments.type());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + " all   " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(DELETE_ALL, arguments.tickerOrPair());
        assertNull(arguments.ownerId());
        assertNull(arguments.type());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, DeleteCommand.NAME + " all  remainder  " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(DELETE_ALL, arguments.tickerOrPair());
        assertNull(arguments.ownerId());
        assertEquals(remainder, arguments.type());
    }
}