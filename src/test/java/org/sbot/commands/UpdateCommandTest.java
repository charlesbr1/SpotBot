package org.sbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.Services;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.UsersDao;
import org.sbot.services.discord.Discord;
import org.sbot.utils.DatesTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.math.BigDecimal.ONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.ALERT_ID_ARGUMENT;
import static org.sbot.commands.CommandAdapter.SELECTION_ARGUMENT;
import static org.sbot.commands.CommandAdapterTest.assertExceptionContains;
import static org.sbot.commands.UpdateCommand.*;
import static org.sbot.commands.context.CommandContext.TOO_MANY_ARGUMENTS;
import static org.sbot.entities.alerts.AlertTest.TEST_SERVER_ID;
import static org.sbot.entities.alerts.AlertTest.createTestAlertWithUserId;
import static org.sbot.services.dao.AlertsDao.UpdateField.*;
import static org.sbot.services.dao.AlertsDaoTest.assertDeepEquals;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.UTC;

class UpdateCommandTest {

    @Test
    void arguments() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);

        CommandContext[] commandContext = new CommandContext[1];

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME);
        assertExceptionContains(IllegalArgumentException.class, SELECTION_ARGUMENT,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  badSelection");
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  locale locale err");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  timezone timezone err");
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  message");
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  message err");
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  message  value 123");
        assertExceptionContains(IllegalArgumentException.class, ALERT_ID_ARGUMENT,
                () -> UpdateCommand.arguments(commandContext[0]));

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  locale locale");
        var arguments = UpdateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_LOCALE, arguments.selection());
        assertEquals("locale", arguments.value());
        assertNull(arguments.alertId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  timezone timezone");
        arguments = UpdateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_TIMEZONE, arguments.selection());
        assertEquals("timezone", arguments.value());
        assertNull(arguments.alertId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  message 123 message");
        arguments = UpdateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_MESSAGE, arguments.selection());
        assertNull(arguments.value());
        assertEquals(123L, arguments.alertId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  from_date 123 message");
        arguments = UpdateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_FROM_DATE, arguments.selection());
        assertNull(arguments.value());
        assertEquals(123L, arguments.alertId());

        commandContext[0] = CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + "  repeat 123 value");
        arguments = UpdateCommand.arguments(commandContext[0]);
        assertNotNull(arguments);
        assertEquals(CHOICE_REPEAT, arguments.selection());
        assertNull(arguments.value());
        assertEquals(123L, arguments.alertId());
    }

    @Test
    void onCommandLocale() {
        long userId = 87543L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(userId);
        Context context = mock(Context.class);
        UsersDao usersDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(context.dataServices()).thenReturn(dataServices);

        var command = new UpdateCommand();
        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var finalCommandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " locale invalidlocale"));
        assertExceptionContains(IllegalArgumentException.class, "locale is not supported", () -> command.onCommand(finalCommandContext));

        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " locale fr"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(usersDao).userExists(userId);
        verify(usersDao, never()).updateLocale(anyInt(), any());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        var message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("Unable to save your locale"));

        when(usersDao.userExists(userId)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " locale fr"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(usersDao, times(2)).userExists(userId);
        verify(usersDao).updateLocale(userId, DiscordLocale.FRENCH.toLocale());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("locale is set to fr"));

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " locale pt-BR"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(usersDao, times(3)).userExists(userId);
        verify(usersDao).updateLocale(userId, DiscordLocale.PORTUGUESE_BRAZILIAN.toLocale());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("locale is set to pt-BR"));

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " locale en-US"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(usersDao, times(4)).userExists(userId);
        verify(usersDao).updateLocale(userId, DiscordLocale.ENGLISH_US.toLocale());
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("locale is set to en-US"));
    }

    @Test
    void onCommandTimezone() {
        long userId = 87543L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(userId);
        Context context = mock(Context.class);
        UsersDao usersDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(context.dataServices()).thenReturn(dataServices);

        var command = new UpdateCommand();
        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var finalCommandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " timezone invalidtimezone"));
        assertExceptionContains(ZoneRulesException.class, "Unknown time-zone ID", () -> command.onCommand(finalCommandContext));

        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " timezone UTC"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(usersDao).userExists(userId);
        verify(usersDao, never()).updateTimezone(anyInt(), any());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        var message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("Unable to save your timezone"));

        when(usersDao.userExists(userId)).thenReturn(true);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " timezone UTC"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(usersDao, times(2)).userExists(userId);
        verify(usersDao).updateTimezone(userId, UTC);
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("timezone is set to UTC"));

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " timezone Europe/Paris"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(usersDao, times(3)).userExists(userId);
        verify(usersDao).updateTimezone(userId, ZoneId.of("Europe/Paris"));
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("timezone is set to Europe/Paris"));

        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " timezone +02:20"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(usersDao, times(4)).userExists(userId);
        verify(usersDao).updateTimezone(userId, ZoneId.of("+02:20"));
        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("timezone is set to +02:20"));
    }

    @Test
    void onCommandFromPrice() {
        long userId = 87543L;
        long alertId = 123L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(userId);
        Context context = mock(Context.class);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        AlertsDao alertsDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);

        var command = new UpdateCommand();
        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_price 123 invalid"));
        assertExceptionContains(IllegalArgumentException.class, "value", () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_price 123 -1"));
        assertExceptionContains(IllegalArgumentException.class, "Negative", () -> command.onCommand(fc2));

        var fc3 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_price 123 123456789012345678901"));
        assertExceptionContains(IllegalArgumentException.class, "too long", () -> command.onCommand(fc3));

        var fc4 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_price 123 1 too"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc4));

        var fc5 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_price 123 1 23"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc5));

        // not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 03"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).update(any(), any());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        var message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));

        // private channel, same user, any server, ok
        Discord discord = mock();
        Services services = mock();
        when(services.discord()).thenReturn(discord);
        when(context.services()).thenReturn(services);
        Guild guild = mock();
        when(discord.getGuildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
        when(guild.getName()).thenReturn("test server");

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 1"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(2)).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(FROM_PRICE)));
        var alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withFromPrice(ONE), alertArg);
        verify(discord).getGuildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_LOW));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 2"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, same user, not same server, not found
        Member member = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(TEST_SERVER_ID);
        when(messageReceivedEvent.getMember()).thenReturn(member);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(4)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, same user, same server  ok
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 77.1234567"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(6)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(FROM_PRICE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID).withFromPrice(new BigDecimal("77.1234567")), alertArg);

        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_LOW));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, same server, admin,  ok, notification sent
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 7"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(8)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(FROM_PRICE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID).withFromPrice(new BigDecimal("7")), alertArg);
        verify(discord).getGuildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_LOW));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), any());
    }

    @Test
    void onCommandToPrice() {
        long userId = 87543L;
        long alertId = 123L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(userId);
        Context context = mock(Context.class);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        AlertsDao alertsDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);

        var command = new UpdateCommand();
        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_price 123 invalid"));
        assertExceptionContains(IllegalArgumentException.class, "value", () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_price 123 -1"));
        assertExceptionContains(IllegalArgumentException.class, "Negative", () -> command.onCommand(fc2));

        var fc3 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_price 123 123456789012345678901"));
        assertExceptionContains(IllegalArgumentException.class, "too long", () -> command.onCommand(fc3));

        var fc4 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_price 123 1 too"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc4));

        var fc5 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_price 123 1 23"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc5));

        // not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 03"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).update(any(), any());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        var message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));

        // private channel, same user, any server, ok
        Discord discord = mock();
        Services services = mock();
        when(services.discord()).thenReturn(discord);
        when(context.services()).thenReturn(services);
        Guild guild = mock();
        when(discord.getGuildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
        when(guild.getName()).thenReturn("test server");

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 1"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(2)).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(TO_PRICE)));
        var alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withToPrice(ONE), alertArg);
        verify(discord).getGuildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_HIGH));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 2"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, same user, not same server, not found
        Member member = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(TEST_SERVER_ID);
        when(messageReceivedEvent.getMember()).thenReturn(member);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(4)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, same user, same server  ok
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 77.1234567"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(6)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(TO_PRICE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID).withToPrice(new BigDecimal("77.1234567")), alertArg);

        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_HIGH));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, same server, admin,  ok, notification sent
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 7"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(8)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(TO_PRICE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID).withToPrice(new BigDecimal("7")), alertArg);
        verify(discord).getGuildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_HIGH));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), any());
    }

    @Test
    void onCommandFromDate() {
    }

    @Test
    void onCommandToDate() {
    }

    @Test
    void onCommandMargin() {
        long userId = 87543L;
        long alertId = 123L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(userId);
        Context context = mock(Context.class);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        AlertsDao alertsDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);

        var command = new UpdateCommand();
        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " margin 123 invalid"));
        assertExceptionContains(IllegalArgumentException.class, "value", () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " margin 123 -1"));
        assertExceptionContains(IllegalArgumentException.class, "Negative", () -> command.onCommand(fc2));

        var fc3 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " margin 123 123456789012345678901"));
        assertExceptionContains(IllegalArgumentException.class, "too long", () -> command.onCommand(fc3));

        var fc4 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " margin 123 1 too"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc4));

        var fc5 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " margin 123 1 23"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc5));

        // not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 03"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).update(any(), any());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        var message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));

        // private channel, same user, any server, ok
        Discord discord = mock();
        Services services = mock();
        when(services.discord()).thenReturn(discord);
        when(context.services()).thenReturn(services);
        Guild guild = mock();
        when(discord.getGuildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
        when(guild.getName()).thenReturn("test server");

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 1"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(2)).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(MARGIN)));
        var alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withMargin(ONE), alertArg);
        verify(discord).getGuildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_MARGIN));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 2"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, same user, not same server, not found
        Member member = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(TEST_SERVER_ID);
        when(messageReceivedEvent.getMember()).thenReturn(member);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(4)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, same user, same server  ok
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 77.1234567"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(6)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(MARGIN)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID).withMargin(new BigDecimal("77.1234567")), alertArg);

        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_MARGIN));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, same server, admin,  ok, notification sent
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 7"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(8)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(MARGIN)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID).withMargin(new BigDecimal("7")), alertArg);
        verify(discord).getGuildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_MARGIN));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), any());
    }

    @Test
    void onCommandRepeat() {
        long userId = 87543L;
        long alertId = 123L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(userId);
        Context context = mock(Context.class);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        AlertsDao alertsDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);

        var command = new UpdateCommand();
        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " repeat 123 invalid"));
        assertExceptionContains(IllegalArgumentException.class, "value", () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " repeat 123 " + (REPEAT_MIN - 1)));
        assertExceptionContains(IllegalArgumentException.class, CHOICE_REPEAT, () -> command.onCommand(fc2));

        var fc3 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " repeat 123 " + (REPEAT_MAX + 1)));
        assertExceptionContains(IllegalArgumentException.class, CHOICE_REPEAT, () -> command.onCommand(fc3));

        var fc4 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " repeat 123 1 too"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc4));

        var fc5 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " repeat 123 1 23"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc5));

        // not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).update(any(), any());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        var message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));

        // private channel, same user, any server, ok
        Discord discord = mock();
        Services services = mock();
        when(services.discord()).thenReturn(discord);
        when(context.services()).thenReturn(services);
        Guild guild = mock();
        when(discord.getGuildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
        when(guild.getName()).thenReturn("test server");

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(2)).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(REPEAT)));
        var alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withRepeat((short) 0), alertArg);
        verify(discord).getGuildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("repeat"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, same user, not same server, not found
        Member member = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(TEST_SERVER_ID);
        when(messageReceivedEvent.getMember()).thenReturn(member);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(4)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, same user, same server  ok
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 77"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(6)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(REPEAT)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID).withRepeat((short) 77), alertArg);

        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("repeat"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 1"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, same server, admin,  ok, notification sent
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 7"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(8)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(REPEAT)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID).withRepeat((short) 7), alertArg);
        verify(discord).getGuildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("repeat"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), any());
    }

    @Test
    void onCommandSnooze() {
        long userId = 87543L;
        long alertId = 123L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        User user = mock();
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        when(user.getIdLong()).thenReturn(userId);
        Context context = mock(Context.class);
        ZonedDateTime now = DatesTest.nowUtc().truncatedTo(ChronoUnit.MINUTES);
        when(context.clock()).thenReturn(Clock.fixed(now.toInstant(), UTC));
        AlertsDao alertsDao = mock();
        Context.DataServices dataServices = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(context.dataServices()).thenReturn(dataServices);

        var command = new UpdateCommand();
        assertThrows(NullPointerException.class, () -> command.onCommand(null));

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " snooze 123 invalid"));
        assertExceptionContains(IllegalArgumentException.class, "value", () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " snooze 123 " + (SNOOZE_MIN - 1)));
        assertExceptionContains(IllegalArgumentException.class, CHOICE_SNOOZE, () -> command.onCommand(fc2));

        var fc3 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " snooze 123 " + (SNOOZE_MAX + 1)));
        assertExceptionContains(IllegalArgumentException.class, CHOICE_SNOOZE, () -> command.onCommand(fc3));

        var fc4 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " snooze 123 1 too"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc4));

        var fc5 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " snooze 123 1 23"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc5));

        // not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 03"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);
        ArgumentCaptor<List<Message>> messagesReply = ArgumentCaptor.forClass(List.class);
        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao).getAlertWithoutMessage(alertId);
        verify(alertsDao, never()).update(any(), any());
        var messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        var message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));

        // private channel, same user, any server, ok
        Discord discord = mock();
        Services services = mock();
        when(services.discord()).thenReturn(discord);
        when(context.services()).thenReturn(services);
        Guild guild = mock();
        when(discord.getGuildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
        when(guild.getName()).thenReturn("test server");

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 1"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(2)).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(SNOOZE)));
        var alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withSnooze((short) 1), alertArg);
        verify(discord).getGuildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("snooze"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 2"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, same user, not same server, not found
        Member member = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(TEST_SERVER_ID);
        when(messageReceivedEvent.getMember()).thenReturn(member);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(4)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, same user, same server  ok
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 77"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(6)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(SNOOZE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID).withSnooze((short) 77), alertArg);

        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("snooze"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).getGuildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyInt(), any(), any());

        // server channel, not same user, same server, admin,  ok, notification sent
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 7"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(8)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(SNOOZE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID).withSnooze((short) 7), alertArg);
        verify(discord).getGuildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("snooze"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), any());
    }

    @Test
    void onCommandEnable() {
    }

    @Test
    void listeningDate() {
    }
}