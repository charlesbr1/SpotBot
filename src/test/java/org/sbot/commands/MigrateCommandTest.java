package org.sbot.commands;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.context.Context;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.AlertsDao.SelectionFilter;
import org.sbot.services.discord.Discord;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static net.dv8tion.jda.api.Permission.ADMINISTRATOR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.PAIR_ARGUMENT;
import static org.sbot.commands.CommandAdapter.TICKER_PAIR_ARGUMENT;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.MigrateCommand.GUILD_ARGUMENT;
import static org.sbot.commands.MigrateCommand.MIGRATE_ALL;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.AlertTest.createTestAlertWithUserId;
import static org.sbot.services.dao.AlertsDao.UpdateField.SERVER_ID;
import static org.sbot.services.dao.AlertsDaoTest.assertDeepEquals;

class MigrateCommandTest {

    @Test
    void onCommandMigrateById() {
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
        Context.DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);
        Context.Services services = mock();
        when(services.discord()).thenReturn(discord);
        when(context.services()).thenReturn(services);

        var command = new MigrateCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        // migrate by id, not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao, never()).updateServerIdOf(any(), anyLong());
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        List<Message> messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // migrate by id, alert exists, not same user, private channel, not found
        alertId++;
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId)));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao, never()).updateServerIdOf(any(), anyLong());
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // migrate by id, alert exists, same user, private channel, alert server, to private -> ok
        alertId++;
        var alert = createTestAlertWithUserId(userId).withServerId(serverId);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(SERVER_ID)));
        var alertArg = alertCaptor.getValue();
        assertDeepEquals(alert.withServerId(PRIVATE_MESSAGES), alertArg);
        verify(alertsDao, never()).updateServerIdOf(any(), anyLong());
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("migrated"));

        // migrate by id, alert exists, same user, private channel, alert private, to private -> ko
        alertId++;
        alert = createTestAlertWithUserId(userId).withServerId(PRIVATE_MESSAGES);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        var finalCommandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " 0"));
        assertExceptionContains(IllegalArgumentException.class, "already in this private channel", () -> command.onCommand(finalCommandContext));
        verify(alertsDao).update(alert.withServerId(PRIVATE_MESSAGES), Set.of(SERVER_ID));
        verify(alertsDao, never()).updateServerIdOf(any(), anyLong());

        // migrate by id, alert exists, same user, private channel, alert server, to same server -> ko
        alertId++;
        alert = createTestAlertWithUserId(userId).withServerId(serverId);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        var finalCommandContext2 = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " " + serverId));
        assertExceptionContains(IllegalArgumentException.class, "Bot is not supported on this guild", () -> command.onCommand(finalCommandContext2));
        when(discord.guildServer(serverId)).thenReturn(Optional.of(guild));
        var finalCommandContext3 = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " " + serverId));
        assertExceptionContains(IllegalArgumentException.class, "already in guild", () -> command.onCommand(finalCommandContext3));
        verify(alertsDao).update(alert.withServerId(serverId), Set.of(SERVER_ID));
        verify(alertsDao, never()).updateServerIdOf(any(), anyLong());

        // migrate by id, alert exists, same user, private channel, alert server, to another server -> ok
        alertId++;
        var fid = alertId;
        alert = createTestAlertWithUserId(userId).withServerId(serverId).withId(() -> fid);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        when(messageReceivedEvent.getMember()).thenReturn(null);
        Guild guild2 = mock();
        when(guild2.getIdLong()).thenReturn(serverId + 1);
        when(discord.guildServer(serverId + 1)).thenReturn(Optional.of(guild2));
        CacheRestAction<Member> restAction = mock();
        when(guild2.retrieveMemberById(userId)).thenReturn(restAction);

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " " + (serverId + 1)));
        doNothing().when(fc1).reply(anyList(), anyInt());
        assertExceptionContains(IllegalArgumentException.class, "not a member of guild", () -> command.onCommand(fc1));
        verify(alertsDao).getAlertWithoutMessage(alertId);

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " " + (serverId + 1)));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(restAction.complete()).thenReturn(mock());

        command.onCommand(commandContext);
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(SERVER_ID)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(alert.withServerId(serverId + 1), alertArg);

        verify(alertsDao, never()).updateServerIdOf(any(), anyLong());
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("migrated"));

        // guild channel
        // migrate by id, alert exists, not same user, not same channel, not found
        AlertsDao alertsDao2 = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao2);
        when(messageReceivedEvent.getMember()).thenReturn(member);
        alertId++;
        when(alertsDao2.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao2).getAlertWithoutMessage(alertId);
        verify(alertsDao2, never()).update(any(), any());
        verify(alertsDao2, never()).updateServerIdOf(any(), anyLong());
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // migrate by id, alert exists, same user, not same channel, not found
        alertId++;
        when(alertsDao2.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(serverId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao2).getAlertWithoutMessage(alertId);
        verify(alertsDao2, never()).update(any(), any());
        verify(alertsDao2, never()).updateServerIdOf(any(), anyLong());
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not found"));

        // migrate by id, alert exists, not same user, same channel, access denied
        alertId++;
        when(alertsDao2.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(serverId)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao2).getAlertWithoutMessage(alertId);
        verify(alertsDao2, never()).update(any(), any());
        verify(alertsDao2, never()).updateServerIdOf(any(), anyLong());
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // migrate by id, alert exists, not same user, same channel, admin user, ok
        alertId++;
        var fid2 = alertId;
        alert = createTestAlertWithUserId(userId + 1).withServerId(serverId).withId(() -> fid2);
        when(alertsDao2.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao2).getAlertWithoutMessage(alertId);
        verify(alertsDao2).update(alertCaptor.capture(), eq(Set.of(SERVER_ID)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(alert.withServerId(PRIVATE_MESSAGES), alertArg);
        verify(alertsDao2, never()).updateServerIdOf(any(), anyLong());
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), eq(null));
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("migrated"));

        alertId++;
        var fid22 = alertId;
        alert = createTestAlertWithUserId(userId + 1).withServerId(serverId).withId(() -> fid22);
        when(alertsDao2.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " " + (serverId + 1)));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        Guild guild3 = mock();
        when(guild3.getIdLong()).thenReturn(serverId + 1);
        when(discord.guildServer(serverId + 1)).thenReturn(Optional.of(guild3));
        restAction = mock();
        when(guild3.retrieveMemberById(userId + 1)).thenReturn(restAction);
        when(restAction.complete()).thenReturn(mock());

        command.onCommand(commandContext);
        verify(alertsDao2, times(2)).getAlertWithoutMessage(alertId);
        verify(alertsDao2, times(2)).update(alertCaptor.capture(), eq(Set.of(SERVER_ID)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(alert.withServerId(serverId + 1), alertArg);
        verify(alertsDao2, never()).updateServerIdOf(any(), anyLong());
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, times(2)).sendPrivateMessage(eq(userId + 1), any(), eq(null));
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("migrated"));

        // migrate by id, alert exists, same user, same channel, ok
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);
        alertId++;
        var fid3 = alertId;
        alert = createTestAlertWithUserId(userId).withServerId(serverId).withId(() -> fid3);
        when(alertsDao2.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao2).getAlertWithoutMessage(alertId);
        verify(alertsDao2, times(3)).update(alertCaptor.capture(), eq(Set.of(SERVER_ID)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(alert.withServerId(PRIVATE_MESSAGES), alertArg);
        verify(alertsDao2, never()).updateServerIdOf(any(), anyLong());
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(eq(userId), any(), eq(null));
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("migrated"));

        alertId++;
        var fid4 = alertId;
        alert = createTestAlertWithUserId(userId).withServerId(serverId).withId(() -> fid4);
        when(alertsDao2.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  " + alertId + " " + (serverId + 2)));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(guild3.getIdLong()).thenReturn(serverId + 2);
        when(discord.guildServer(serverId + 2)).thenReturn(Optional.of(guild3));
        restAction = mock();
        when(guild3.retrieveMemberById(userId)).thenReturn(restAction);
        when(restAction.complete()).thenReturn(mock());

        command.onCommand(commandContext);
        verify(alertsDao2, times(2)).getAlertWithoutMessage(alertId);
        verify(alertsDao2, times(4)).update(alertCaptor.capture(), eq(Set.of(SERVER_ID)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(alert.withServerId(serverId + 2), alertArg);
        verify(alertsDao2, never()).updateServerIdOf(any(), anyLong());
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(eq(userId), any(), eq(null));
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("migrated"));
    }

    @Test
    void onCommandMigrateByFilter() {
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
        AlertsDao alertsDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);
        Context.Services services = mock();
        when(services.discord()).thenReturn(discord);
        when(context.services()).thenReturn(services);

        var command = new MigrateCommand();

        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        // migrate all, current user, private channel, ok
        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all 0" ));
        doNothing().when(fc1).reply(anyList(), anyInt());
        assertExceptionContains(IllegalArgumentException.class, "already into the private channel", () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all " + serverId));
        doNothing().when(fc2).reply(anyList(), anyInt());
        assertExceptionContains(IllegalArgumentException.class, "Bot is not supported on this guild", () -> command.onCommand(fc2));

        when(discord.guildServer(serverId)).thenReturn(Optional.of(guild));
        var fc3 = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all " + serverId));
        doNothing().when(fc3).reply(anyList(), anyInt());
        CacheRestAction<Member> restAction = mock();
        when(guild.retrieveMemberById(userId)).thenReturn(restAction);
        assertExceptionContains(IllegalArgumentException.class, "not a member of guild", () -> command.onCommand(fc3));

        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all " + serverId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        when(restAction.complete()).thenReturn(mock());
        command.onCommand(commandContext);

        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(any(), eq(serverId));
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert migrated"));


        // migrate all, not same user, private channel, denied
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all " + serverId + " <@" + (userId + 1) + "> "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(any(), eq(serverId));
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // migrate filter, current user, private channel, ok
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " eth/usd  " + serverId + " <@" + (userId) + "> "));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(SelectionFilter.ofUser(userId, null).withTickerOrPair("ETH/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert migrated"));

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " btc/usd  " + serverId + " trend <@" + (userId) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(SelectionFilter.ofUser(userId, trend).withTickerOrPair("BTC/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert migrated"));

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " eth/xrp remainder " + serverId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(SelectionFilter.ofUser(userId, remainder).withTickerOrPair("ETH/XRP"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert migrated"));

        // migrate filter, not same user, private channel, denied
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " eth/usd " + serverId + " <@" + (userId + 2) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao, never()).updateServerIdOf(SelectionFilter.ofUser(userId + 2, null).withTickerOrPair("ETH/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // migrate all, current user, server channel, ok
        serverId++;
        Guild guild2 = mock();
        when(guild2.getIdLong()).thenReturn(serverId);
        when(guild2.retrieveMemberById(userId)).thenReturn(restAction);
        when(discord.guildServer(serverId)).thenReturn(Optional.of(guild2));
        when(messageReceivedEvent.getMember()).thenReturn(member);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all " + serverId));
        doNothing().when(commandContext).reply(anyList(), anyInt());

        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao, never()).updateServerIdOf(SelectionFilter.of(serverId, userId, null), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert migrated"));

        // migrate all, not same user, server channel, denied
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all " + serverId + " <@" + (userId + 1) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao, never()).updateServerIdOf(SelectionFilter.of(serverId, userId + 1, null), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // migrate all, not same user, server channel, admin, ok
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        when(guild2.retrieveMemberById(userId + 1)).thenReturn(restAction);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all " + serverId + " <@" + (userId + 1) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId + 1, null), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(eq(userId + 1), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert migrated"));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);

        when(alertsDao.updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId + 1, null), serverId)).thenReturn(3L);
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all " + serverId + " <@" + (userId + 1) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao, times(2)).updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId + 1, null), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("3 alerts migrated"));
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(false);

        // migrate filter, current user, server channel, ok
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " eth/usd " + serverId + " <@" + userId + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId, null).withTickerOrPair("ETH/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(eq(userId), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert migrated"));

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " btc/usd " + serverId + "  remainder " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId, remainder).withTickerOrPair("BTC/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(eq(userId), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert migrated"));

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " btc/usd  range " + serverId));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId, range).withTickerOrPair("BTC/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(eq(userId), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert migrated"));

        // migrate filter, not same user, server channel, denied
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " eth/usd " + serverId + " <@" + (userId + 3) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao, never()).updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId + 3, null).withTickerOrPair("ETH/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(eq(userId + 3), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " eth/usd  " + serverId + " trend <@" + (userId + 3) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao, never()).updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId + 3, trend).withTickerOrPair("ETH/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(eq(userId + 3), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("not allowed"));

        // migrate filter, not same user, server channel, admin, ok
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        when(guild2.retrieveMemberById(userId + 3)).thenReturn(restAction);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " eth/usd " + serverId + " <@" + (userId + 3) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId + 3, null).withTickerOrPair("ETH/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord, never()).sendPrivateMessage(eq(userId + 3), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("0 alert migrated"));

        when(alertsDao.updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId + 3, null).withTickerOrPair("ETH/USD"), serverId)).thenReturn(1L);
        when(member.hasPermission(ADMINISTRATOR)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " eth/usd " + serverId + " <@" + (userId + 3) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao, times(2)).updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId + 3, null).withTickerOrPair("ETH/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord).sendPrivateMessage(eq(userId + 3), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("1 alert migrated"));

        when(guild2.retrieveMemberById(userId + 5)).thenReturn(restAction);
        when(alertsDao.updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId + 5, trend).withTickerOrPair("ETH/USD"), serverId)).thenReturn(7L);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " eth/usd trend " + serverId + " <@" + (userId + 5) + "> " ));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        verify(alertsDao, never()).update(any(), any());
        verify(alertsDao).updateServerIdOf(SelectionFilter.of(member.getGuild().getIdLong(), userId + 5, trend).withTickerOrPair("ETH/USD"), serverId);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(discord).sendPrivateMessage(eq(userId + 5), any(), any());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        assertTrue(messages.get(0).embeds().get(0).getDescriptionBuilder().toString().contains("7 alerts migrated"));
    }

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, TICKER_PAIR_ARGUMENT,
                () -> MigrateCommand.arguments(commandContext[0]));

        // id command
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  123 az");
        assertExceptionContains(IllegalArgumentException.class, GUILD_ARGUMENT,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  123 45 az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  123 45 54");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  123 45");
        var arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(123L, arguments.alertId());
        assertNull(arguments.type());
        assertEquals(45L, arguments.serverId());
        assertNull(arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        // alert id negative
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  -123");
        assertExceptionContains(IllegalArgumentException.class, "Negative",
                () -> MigrateCommand.arguments(commandContext[0]));

        // filter command
        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  az");
        assertExceptionContains(IllegalArgumentException.class, "ticker",
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  azef efefe");
        assertExceptionContains(IllegalArgumentException.class, GUILD_ARGUMENT,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  azefefefel/lklk");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  btc/lk");
        assertExceptionContains(IllegalArgumentException.class, PAIR_ARGUMENT,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  ETH/USD badType");
        assertExceptionContains(IllegalArgumentException.class, GUILD_ARGUMENT,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  all ");
        assertExceptionContains(IllegalArgumentException.class, GUILD_ARGUMENT,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  all range");
        assertExceptionContains(IllegalArgumentException.class, GUILD_ARGUMENT,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  all range <@123>");
        assertExceptionContains(IllegalArgumentException.class, GUILD_ARGUMENT,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  all 12 <@123> range");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  ETH/USD range ");
        assertExceptionContains(IllegalArgumentException.class, GUILD_ARGUMENT,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  ETH/USD  12 lm");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  ETH/USD range 12 lm");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  ETH/USD 12   <@321> range");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  ETH/USD range 12 <@321> az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  ETH/USD range 12 <@321> 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  ETH/USD range 12 <@321> ar 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  ETH/USD  12 <@321> az");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + "  ETH/USD  12 <@321> 23");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> MigrateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all 12  " );
        arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertNull(arguments.type());
        assertEquals(12, arguments.serverId());
        assertEquals(MIGRATE_ALL, arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all 33 remainder  " );
        arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(remainder, arguments.type());
        assertEquals(33, arguments.serverId());
        assertEquals(MIGRATE_ALL, arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all range 66  " );
        arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(range, arguments.type());
        assertEquals(66, arguments.serverId());
        assertEquals(MIGRATE_ALL, arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all 132 range <@124>  " );
        arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(range, arguments.type());
        assertEquals(132, arguments.serverId());
        assertEquals(MIGRATE_ALL, arguments.tickerOrPair());
        assertEquals(124L, arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " all trend 52 <@1244>  " );
        arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals(52, arguments.serverId());
        assertEquals(MIGRATE_ALL, arguments.tickerOrPair());
        assertEquals(1244L, arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " dot/usd  12  " );
        arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertNull(arguments.type());
        assertEquals(12, arguments.serverId());
        assertEquals("dot/usd", arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " dot/XRP trend 10  " );
        arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(trend, arguments.type());
        assertEquals(10, arguments.serverId());
        assertEquals("dot/XRP", arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " ETH/usd 13 remainder " );
        arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(remainder, arguments.type());
        assertEquals(13, arguments.serverId());
        assertEquals("ETH/usd", arguments.tickerOrPair());
        assertNull(arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " btc/usd 213 remainder <@3210>" );
        arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(remainder, arguments.type());
        assertEquals(213, arguments.serverId());
        assertEquals("btc/usd", arguments.tickerOrPair());
        assertEquals(3210L, arguments.ownerId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, MigrateCommand.NAME + " eth/btc  range 120 <@315> " );
        arguments = MigrateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertNull(arguments.alertId());
        assertEquals(range, arguments.type());
        assertEquals(120, arguments.serverId());
        assertEquals("eth/btc", arguments.tickerOrPair());
        assertEquals(315L, arguments.ownerId());
    }

    @Test
    void sameId() {
        assertFalse(MigrateCommand.sameId(null, 1L));
        assertTrue(MigrateCommand.sameId(null, PRIVATE_MESSAGES));
        Guild guild = mock();
        when(guild.getIdLong()).thenReturn(123L);
        assertFalse(MigrateCommand.sameId(guild, 1L));
        assertFalse(MigrateCommand.sameId(guild, PRIVATE_MESSAGES));
        assertTrue(MigrateCommand.sameId(guild, 123L));
    }

    @Test
    void requireGuildMember() {
        assertDoesNotThrow(() -> MigrateCommand.requireGuildMember(null, 0L));
        assertDoesNotThrow(() -> MigrateCommand.requireGuildMember(null, 123L));
        CacheRestAction<Member> restAction = mock();
        Guild guild = mock();
        when(guild.retrieveMemberById(123L)).thenReturn(restAction);
        when(restAction.complete()).thenReturn(mock());
        assertDoesNotThrow(() -> MigrateCommand.requireGuildMember(guild, 123L));
        verify(guild).retrieveMemberById(123L);

        when(guild.retrieveMemberById(456L)).thenReturn(restAction);
        when(restAction.complete()).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> MigrateCommand.requireGuildMember(guild, 456L));
        verify(guild).retrieveMemberById(456L);
    }
}