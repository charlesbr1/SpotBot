package org.sbot.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.ServerSettings;
import org.sbot.entities.Settings;
import org.sbot.entities.UserSettings;
import org.sbot.services.NotificationsService;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.DataServices;
import org.sbot.services.context.Context.Services;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.dao.NotificationsDao;

import java.time.Clock;
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
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.AlertTest.TEST_CLIENT_TYPE;
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
        Services services = mock();
        when(context.services()).thenReturn(services);
        NotificationsService notificationsService = mock();
        when(services.notificationService()).thenReturn(notificationsService);
        AlertsDao alertsDao = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(context.dataServices()).thenReturn(dataServices);
        when(context.clock()).thenReturn(Clock.systemUTC());
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);

        var command = new DeleteCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        // delete by id, not found
        var commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(alertsDao, never()).delete(TEST_CLIENT_TYPE, alertId);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        List<Message> messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // delete by id, alert exists, not same user, private channel, not found
        alertId++;
        when(alertsDao.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId)));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(alertsDao, never()).delete(TEST_CLIENT_TYPE, alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // delete by id, alert exists, same user, private channel, ok
        alertId++;
        when(alertsDao.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(serverId)));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(alertsDao).delete(TEST_CLIENT_TYPE, alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("deleted"));

        alertId++;
        when(alertsDao.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(PRIVATE_MESSAGES)));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(alertsDao).delete(TEST_CLIENT_TYPE, alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("deleted"));
        verify(notificationsService, never()).sendNotifications();

        // guild channel
        // delete by id, alert exists, not same user, not same channel, not found
        when(messageReceivedEvent.getMember()).thenReturn(member);
        alertId++;
        when(alertsDao.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId + 1)));
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(alertsDao, never()).delete(TEST_CLIENT_TYPE, alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // delete by id, alert exists, same user, not same channel, not found
        alertId++;
        when(alertsDao.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(serverId + 1)));
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(alertsDao, never()).delete(TEST_CLIENT_TYPE, alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // delete by id, alert exists, not same user, same channel, access denied
        alertId++;
        when(alertsDao.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId)));
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(alertsDao, never()).delete(TEST_CLIENT_TYPE, alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // delete by id, alert exists, not same user, same channel, admin user, ok
        alertId++;
        when(alertsDao.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId)));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(alertsDao).delete(TEST_CLIENT_TYPE, alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("deleted"));

        // delete by id, alert exists, same user, same channel, ok
        alertId++;
        when(alertsDao.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(serverId)));
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  " + alertId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(alertsDao).delete(TEST_CLIENT_TYPE, alertId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("deleted"));
    }

    @Test
    void onCommandDeleteByFilter() {
        long userId = 321L;
        long serverId = 123L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(userId);
        Member member = mock();
        Guild guild = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(serverId);
        Context context = mock(Context.class);
        when(context.clock()).thenReturn(Clock.systemUTC());
        AlertsDao alertsDao = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(context.dataServices()).thenReturn(dataServices);
        Services services = mock();
        when(context.services()).thenReturn(services);
        NotificationsService notificationsService = mock();
        when(services.notificationService()).thenReturn(notificationsService);
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);

        var command = new DeleteCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        // delete all, current user, private channel, ok
        var commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " all" ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null));
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService, never()).sendNotifications();
        List<Message> messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert deleted"));


        // delete all, not same user, private channel, denied
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " all  <@" + (userId + 1) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).delete(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId + 1, null));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService, never()).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // delete filter, current user, private channel, ok
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/usd  <@" + (userId) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, null).withTickerOrPair("ETH/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService, never()).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert deleted"));

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " btc/usd trend <@" + (userId) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, trend).withTickerOrPair("BTC/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert deleted"));

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/xrp remainder" ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId, remainder).withTickerOrPair("ETH/XRP"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert deleted"));

        // delete filter, not same user, private channel, denied
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/usd  <@" + (userId + 2) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).delete(SelectionFilter.ofUser(TEST_CLIENT_TYPE, userId + 2, null).withTickerOrPair("ETH/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService, never()).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // delete all, current user, server channel, ok
        when(messageReceivedEvent.getMember()).thenReturn(member);

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " all " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService, never()).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert deleted"));

        // delete all, not same user, server channel, denied
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " all  <@" + (userId + 1) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 1, null));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService, never()).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // delete all, not same user, server channel, admin, ok
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " all  <@" + (userId + 1) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 1, null));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService, never()).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert deleted"));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);

        when(alertsDao.delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 1, null))).thenReturn(3L);
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " all  <@" + (userId + 1) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, times(2)).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 1, null));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("3 alerts deleted"));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);

        // delete filter, current user, server channel, ok
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/usd  <@" + userId + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, null).withTickerOrPair("ETH/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert deleted"));

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " btc/usd  remainder " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, remainder).withTickerOrPair("BTC/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert deleted"));

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " btc/usd  range " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId, range).withTickerOrPair("BTC/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert deleted"));

        // delete filter, not same user, server channel, denied
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/usd  <@" + (userId + 3) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 3, null).withTickerOrPair("ETH/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/usd trend <@" + (userId + 3) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 3, trend).withTickerOrPair("ETH/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // delete filter, not same user, server channel, admin, ok
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/usd  <@" + (userId + 3) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 3, null).withTickerOrPair("ETH/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert deleted"));

        when(alertsDao.delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 3, null).withTickerOrPair("ETH/USD"))).thenReturn(1L);
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/usd  <@" + (userId + 3) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, times(2)).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 3, null).withTickerOrPair("ETH/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService, times(2)).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("1 alert deleted"));

        when(alertsDao.delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 5, trend).withTickerOrPair("ETH/USD"))).thenReturn(7L);
        commandContext = spy(CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/usd trend <@" + (userId + 5) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).delete(SelectionFilter.of(TEST_CLIENT_TYPE, serverId, userId + 5, trend).withTickerOrPair("ETH/USD"));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(notificationsService, times(3)).sendNotifications();
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("7 alerts deleted"));
    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);
        var settings = new Settings(UserSettings.NO_USER, ServerSettings.PRIVATE_SERVER);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, TICKER_PAIR_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        // id command
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  123 az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  123 45");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  123");
        var arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(123L, arguments.alertId());
        assertNull(arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        // alert id negative
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  -123");
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> DeleteCommand.arguments(commandContext[0]));

        // filter command
        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  z");
        assertExceptionContains(IllegalArgumentException.class, "ticker",
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  azef efefe");
        assertExceptionContains(IllegalArgumentException.class, TYPE_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  azefefefel/lklk");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  bc/k");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  ETH/USD badType");
        assertExceptionContains(IllegalArgumentException.class, TYPE_ARGUMENT,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + "  ETH/USD range <@321> az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> DeleteCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/usd <@32>  " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals("eth/usd", arguments.tickerOrPair());
        assertEquals(32L, arguments.ownerId());
        assertNull(arguments.type());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " dot/usd  range  " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals("dot/usd", arguments.tickerOrPair());
        assertNull(arguments.ownerId());
        assertEquals(range, arguments.type());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " eth/usd  trend   <@321> " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals("eth/usd", arguments.tickerOrPair());
        assertEquals(321L, arguments.ownerId());
        assertEquals(trend, arguments.type());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " btc/usd <@3210>  remainder" );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals("btc/usd", arguments.tickerOrPair());
        assertEquals(3210L, arguments.ownerId());
        assertEquals(remainder, arguments.type());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " all   " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(DELETE_ALL, arguments.tickerOrPair());
        assertNull(arguments.ownerId());
        assertNull(arguments.type());

        commandContext[0] = CommandContext.of(context, settings, messageReceivedEvent, DeleteCommand.NAME + " all  remainder  " );
        arguments = DeleteCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(DELETE_ALL, arguments.tickerOrPair());
        assertNull(arguments.ownerId());
        assertEquals(remainder, arguments.type());
    }
}