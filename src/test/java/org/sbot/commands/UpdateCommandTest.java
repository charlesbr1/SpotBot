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
import org.sbot.utils.Dates;
import org.sbot.utils.DatesTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneRulesException;
import java.util.List;
import java.util.Locale;
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
import static org.sbot.entities.alerts.Alert.DEFAULT_REPEAT;
import static org.sbot.entities.alerts.Alert.DEFAULT_SNOOZE_HOURS;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.entities.alerts.RemainderAlert.REMAINDER_DEFAULT_REPEAT;
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
    void onCommandMessage() {
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

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " message 123"));
        assertExceptionContains(IllegalArgumentException.class, CHOICE_MESSAGE, () -> command.onCommand(fc1));

        // not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " message 123 new message"));
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
        when(discord.guildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
        when(guild.getName()).thenReturn("test server");
        var newMessage = "new message";

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " message 123 " + newMessage));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(2)).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(MESSAGE)));
        var alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withMessage(newMessage), alertArg);
        verify(discord).guildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_MESSAGE));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " message 123 " + newMessage));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, same user, not same server, not found
        Member member = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(TEST_SERVER_ID);
        when(messageReceivedEvent.getMember()).thenReturn(member);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " message 123 " + newMessage));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(4)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " message 123 " + newMessage));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, same user, same server  ok
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " message 123 " + newMessage));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(6)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(MESSAGE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID).withMessage(newMessage), alertArg);

        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_MESSAGE));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " message 123 " + newMessage));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server, admin,  ok, notification sent
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        newMessage = newMessage + " zer ere 123.2  1 fd";
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " message 123 " + newMessage));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(8)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(MESSAGE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID).withMessage(newMessage), alertArg);
        verify(discord).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_MESSAGE));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), any());
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
        when(discord.guildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
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
        verify(discord).guildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_LOW));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 2"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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

        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_LOW));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_price 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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
        verify(discord).guildServer(anyLong());
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
        when(discord.guildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
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
        verify(discord).guildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_HIGH));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 2"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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

        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_HIGH));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_price 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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
        verify(discord).guildServer(anyLong());
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

        var fc0 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_date 123 "));
        assertExceptionContains(IllegalArgumentException.class, DISPLAY_FROM_DATE, () -> command.onCommand(fc0));

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_date 123 invalid"));
        assertExceptionContains(DateTimeParseException.class, "Malformed", () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_date 123 1"));
        assertExceptionContains(DateTimeParseException.class, "Malformed", () -> command.onCommand(fc2));

        var fc3 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_date 123 now 123456789012345678901"));
        assertExceptionContains(DateTimeParseException.class, "Malformed", () -> command.onCommand(fc3));

        var fc4 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_date 123 12/12/2012-20:12 too"));
        assertExceptionContains(DateTimeParseException.class, "could not be parsed", () -> command.onCommand(fc4));

        var fc5 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " from_date 123 12/12/2012-20:12 23 too"));
        assertExceptionContains(DateTimeParseException.class, "could not be parsed", () -> command.onCommand(fc5));

        // not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 12/12/2012 20:12"));
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
        when(discord.guildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
        when(guild.getName()).thenReturn("test server");
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/BTC", remainder)));

        var fc6 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now"));
        assertExceptionContains(IllegalArgumentException.class, "future", () -> command.onCommand(fc6));
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/BTC", range)));
        var fc7 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now"));
        doNothing().when(fc7).reply(anyList(), anyInt());
        assertDoesNotThrow(() -> command.onCommand(fc7));
        verify(alertsDao).update(any(), eq(Set.of(LISTENING_DATE, FROM_DATE)));
        var fc71 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now-0.1"));
        assertExceptionContains(IllegalArgumentException.class, "future", () -> command.onCommand(fc71));

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/BTC", trend)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now +1"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(FROM_DATE)));
        verify(alertsDao, times(2)).update(any(), any());

        var alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserIdAndPairType(userId, "ETH/BTC", trend).withFromDate(now.plusHours(1L)), alertArg);
        verify(discord, times(2)).guildServer(TEST_SERVER_ID);
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(DISPLAY_FROM_DATE));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(6)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, same user, not same server, not found
        Member member = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(TEST_SERVER_ID);
        when(messageReceivedEvent.getMember()).thenReturn(member);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(8)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, same user, same server  ok
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "DOT/XRP", trend).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now-3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(9)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(FROM_DATE)));
        verify(alertsDao, times(3)).update(any(), any());
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserIdAndPairType(userId, "DOT/XRP", trend).withServerId(TEST_SERVER_ID).withFromDate(now.minusHours(3L)), alertArg);

        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(DISPLAY_FROM_DATE));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 12/12/2012-16:32-JST"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(10)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(any(), any());
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server, admin,  ok, notification sent
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId + 1, "ETH/BTC", trend).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 12/12/2112 16:32 Europe/Paris"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(11)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(FROM_DATE)));
        verify(alertsDao, times(4)).update(any(), any());
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserIdAndPairType(userId + 1, "ETH/BTC", trend).withServerId(TEST_SERVER_ID).withFromDate(Dates.parseLocalDateTime(Locale.UK, "12/12/2112-16:32").atZone(ZoneId.of("Europe/Paris"))), alertArg);
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(3)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(DISPLAY_FROM_DATE));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), any());
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);

        // range, from date after now -> listening date = from_date
        verify(discord).sendPrivateMessage(anyLong(), any(), any());
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", range).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now+14"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(12)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(LISTENING_DATE, FROM_DATE)));
        verify(alertsDao, times(5)).update(any(), any());
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", range).withServerId(TEST_SERVER_ID).withListeningDateFromDate(now.plusHours(14L), now.plusHours(14L)), alertArg);
        verify(discord).sendPrivateMessage(anyLong(), any(), any());

        // parse null, range, listening date = now
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", range).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 null"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(13)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(LISTENING_DATE, FROM_DATE)));
        verify(alertsDao, times(6)).update(any(), any());
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", range).withServerId(TEST_SERVER_ID).withListeningDateFromDate(now, null), alertArg);
        verify(discord).sendPrivateMessage(anyLong(), any(), any());

        // remainder, listening date = from_date
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ADA/USD", remainder).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now +1.5"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(14)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(4)).update(alertCaptor.capture(), eq(Set.of(LISTENING_DATE, FROM_DATE)));
        verify(alertsDao, times(7)).update(any(), any());
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserIdAndPairType(userId, "ADA/USD", remainder).withServerId(TEST_SERVER_ID).withListeningDateFromDate(now.plusMinutes(90L), now.plusMinutes(90L)), alertArg);
        verify(discord).sendPrivateMessage(anyLong(), any(), any());

        // remainder, disabled, listening date = from_date
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ADA/USD", remainder).withListeningDateRepeat(null, (short) -2).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 now +1.5"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(15)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(1)).update(alertCaptor.capture(), eq(Set.of(LISTENING_DATE, FROM_DATE, REPEAT)));
        verify(alertsDao, times(8)).update(any(), any());
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserIdAndPairType(userId, "ADA/USD", remainder).withServerId(TEST_SERVER_ID).withListeningDateFromDate(now.plusMinutes(90L), now.plusMinutes(90L)).withRepeat(REMAINDER_DEFAULT_REPEAT), alertArg);
        verify(discord).sendPrivateMessage(anyLong(), any(), any());

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", remainder).withServerId(TEST_SERVER_ID)));
        var fc8 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 null"));
        assertExceptionContains(IllegalArgumentException.class, "Missing", () -> command.onCommand(fc8));

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", trend).withServerId(TEST_SERVER_ID)));
        var fc9 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " from_date 123 null"));
        assertExceptionContains(IllegalArgumentException.class, "Missing", () -> command.onCommand(fc9));
    }

    @Test
    void onCommandToDate() {
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

        var fc0 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_date 123 "));
        assertExceptionContains(IllegalArgumentException.class, DISPLAY_TO_DATE, () -> command.onCommand(fc0));

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_date 123 invalid"));
        assertExceptionContains(DateTimeParseException.class, "Malformed", () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_date 123 1"));
        assertExceptionContains(DateTimeParseException.class, "Malformed", () -> command.onCommand(fc2));

        var fc3 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_date 123 now 123456789012345678901"));
        assertExceptionContains(DateTimeParseException.class, "Malformed", () -> command.onCommand(fc3));

        var fc4 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_date 123 12/12/2012-20:12 too"));
        assertExceptionContains(DateTimeParseException.class, "could not be parsed", () -> command.onCommand(fc4));

        var fc5 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " to_date 123 12/12/2012-20:12 23 too"));
        assertExceptionContains(DateTimeParseException.class, "could not be parsed", () -> command.onCommand(fc5));

        // not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 12/12/2012 20:12"));
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
        when(discord.guildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
        when(guild.getName()).thenReturn("test server");
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId)));

        var fc6 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 now"));
        assertExceptionContains(IllegalArgumentException.class, "future", () -> command.onCommand(fc6));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 now +1"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(TO_DATE)));
        var alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withToDate(now.plusHours(1L)), alertArg);
        verify(discord).guildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(DISPLAY_TO_DATE));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 now"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(4)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, same user, not same server, not found
        Member member = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(TEST_SERVER_ID);
        when(messageReceivedEvent.getMember()).thenReturn(member);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 now"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 now"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(6)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, same user, same server  ok
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 null"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(TO_DATE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID).withToDate(null), alertArg);

        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(DISPLAY_TO_DATE));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 12/12/2012-16:32-JST"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(8)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server, admin,  ok, notification sent
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 12/12/2112 16:32 Europe/Paris"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(9)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(TO_DATE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID).withToDate(Dates.parseLocalDateTime(Locale.UK, "12/12/2112-16:32").atZone(ZoneId.of("Europe/Paris"))), alertArg);
        verify(discord).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(DISPLAY_TO_DATE));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), any());

        // parse null, now
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(false);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", trend).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 now"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(10)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(4)).update(alertCaptor.capture(), eq(Set.of(TO_DATE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", trend).withServerId(TEST_SERVER_ID).withToDate(now), alertArg);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", range).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 null"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(11)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(5)).update(alertCaptor.capture(), eq(Set.of(TO_DATE)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", range).withServerId(TEST_SERVER_ID).withToDate(null), alertArg);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", remainder).withServerId(TEST_SERVER_ID)));
        var fc7 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 now"));
        assertExceptionContains(IllegalArgumentException.class, "future", () -> command.onCommand(fc7));

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserIdAndPairType(userId, "ETH/USD", trend).withServerId(TEST_SERVER_ID)));
        var fc8 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " to_date 123 null"));
        assertExceptionContains(IllegalArgumentException.class, "Missing", () -> command.onCommand(fc8));
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
        when(discord.guildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
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
        verify(discord).guildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_MARGIN));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 2"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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

        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(CHOICE_MARGIN));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " margin 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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
        verify(discord).guildServer(anyLong());
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
        when(discord.guildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
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
        verify(discord).guildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("repeat"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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

        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("repeat"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " repeat 123 1"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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
        verify(discord).guildServer(anyLong());
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
        when(discord.guildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
        when(guild.getName()).thenReturn("test server");

        var alert = createTestAlertWithUserId(userId);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 1"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(2)).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(SNOOZE)));
        var alertArg = alertCaptor.getValue();
        assertDeepEquals(alert.withSnooze((short) 1), alertArg);
        verify(discord).guildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("snooze"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // test with snooze alert, listening date updated
        assertFalse(alert.inSnooze(now));
        alert = alert.withListeningDateRepeat(now.plusMinutes(1L), alert.repeat);
        assertTrue(alert.inSnooze(now));
        assertEquals(DEFAULT_SNOOZE_HOURS, alert.snooze);
        var newSnooze = (short) 3;
        assertNotEquals(newSnooze, alert.snooze);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 " + newSnooze));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(LISTENING_DATE, SNOOZE)));
        alertArg = alertCaptor.getValue();
        var newListeningDate = alert.listeningDate.minusHours(alert.snooze).plusHours(newSnooze);
        assertDeepEquals(alert.withListeningDateSnooze(newListeningDate, (short) newSnooze), alertArg);
        verify(discord, times(2)).guildServer(TEST_SERVER_ID);
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("snooze"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 2"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(4)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

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
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(6)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, same user, same server  ok
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 77"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(SNOOZE)));
        verify(alertsDao, times(3)).update(any(), any());
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID).withSnooze((short) 77), alertArg);
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("snooze"));
        assertTrue(message.getDescriptionBuilder().toString().contains("updated"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 3"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(8)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(any(), any());
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server, admin,  ok, notification sent
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " snooze 123 7"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(9)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(SNOOZE)));
        verify(alertsDao, times(4)).update(any(), any());
        alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID).withSnooze((short) 7), alertArg);
        verify(discord, times(2)).guildServer(anyLong());
        verify(guild, times(3)).getName();

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

        var fc1 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " enable 123 invalid"));
        assertExceptionContains(IllegalArgumentException.class, CHOICE_ENABLE, () -> command.onCommand(fc1));

        var fc2 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " enable 123 -1"));
        assertExceptionContains(IllegalArgumentException.class, CHOICE_ENABLE, () -> command.onCommand(fc2));

        var fc3 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " enable 123 "));
        assertExceptionContains(IllegalArgumentException.class, "Missing", () -> command.onCommand(fc3));

        var fc4 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " enable 123 false too"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc4));

        var fc5 = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME +   " enable 123 true 23"));
        assertExceptionContains(IllegalArgumentException.class, TOO_MANY_ARGUMENTS, () -> command.onCommand(fc5));

        // not found
        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " enable 123 true"));
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
        when(discord.guildServer(TEST_SERVER_ID)).thenReturn(Optional.of(guild));
        when(guild.getName()).thenReturn("test server");

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " enable 123 0"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(2)).getAlertWithoutMessage(alertId);
        var alertCaptor = ArgumentCaptor.forClass(Alert.class);
        verify(alertsDao).update(alertCaptor.capture(), eq(Set.of(LISTENING_DATE, REPEAT)));
        var alertArg = alertCaptor.getValue();
        assertDeepEquals(createTestAlertWithUserId(userId).withListeningDateRepeat(null, DEFAULT_REPEAT), alertArg);
        verify(discord).guildServer(TEST_SERVER_ID);
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(UPDATE_DISABLED_HEADER));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // private channel, not same user, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " enable 123 false"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(3)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, same user, not same server, not found
        Member member = mock();
        when(member.getGuild()).thenReturn(guild);
        when(guild.getIdLong()).thenReturn(TEST_SERVER_ID);
        when(messageReceivedEvent.getMember()).thenReturn(member);

        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " enable 123 no"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(4)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, not same server, not found
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID + 1)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " enable 123 No"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(5)).getAlertWithoutMessage(alertId);
        verify(alertsDao).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not found"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, same user, same server  ok
        var alert = createTestAlertWithUserId(userId).withServerId(TEST_SERVER_ID).withListeningDateRepeat(null, (short)-1);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " enable 123 true"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(6)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(alertCaptor.capture(), eq(Set.of(LISTENING_DATE, REPEAT)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(alert.withListeningDateRepeat(alert.fromDate, DEFAULT_REPEAT), alertArg);

        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(UPDATE_ENABLED_HEADER));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server,  denied
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " enable 123 true"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(7)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(2)).update(any(), any());
        verify(discord).guildServer(anyLong());
        verify(guild).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains("not allowed"));
        verify(discord, never()).sendPrivateMessage(anyLong(), any(), any());

        // server channel, not same user, same server, admin,  ok, notification sent
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        alert = createTestAlertWithUserId(userId + 1).withServerId(TEST_SERVER_ID)
                .withListeningDateRepeat(null, (short) 2).withFromDate(null);
        when(alertsDao.getAlertWithoutMessage(alertId)).thenReturn(Optional.of(alert));
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, UpdateCommand.NAME + " enable 123 1"));
        doNothing().when(commandContext).reply(anyList(), anyInt());
        command.onCommand(commandContext);

        verify(commandContext).reply(messagesReply.capture(), anyInt());
        verify(alertsDao, times(8)).getAlertWithoutMessage(alertId);
        verify(alertsDao, times(3)).update(alertCaptor.capture(), eq(Set.of(LISTENING_DATE, REPEAT)));
        alertArg = alertCaptor.getValue();
        assertDeepEquals(alert.withListeningDateRepeat(now, (short) 2), alertArg);
        verify(discord).guildServer(anyLong());
        verify(guild, times(2)).getName();

        messages = messagesReply.getValue();
        assertEquals(1, messages.size());
        assertEquals(1, messages.get(0).embeds().size());
        message = messages.get(0).embeds().get(0);
        assertTrue(message.getDescriptionBuilder().toString().contains(UPDATE_ENABLED_HEADER));
        verify(discord).sendPrivateMessage(eq(userId + 1), any(), any());
    }

    @Test
    void notEquals() {
        ZonedDateTime now = Dates.nowUtc(Clock.systemUTC());
        assertFalse(UpdateCommand.notEquals(null, null));
        assertFalse(UpdateCommand.notEquals(now, now));
        assertTrue(UpdateCommand.notEquals(null, now));
        assertTrue(UpdateCommand.notEquals(now, null));
        assertTrue(UpdateCommand.notEquals(now, now.plusNanos(1L)));
    }

    @Test
    void validateDateArgument() {
        ZonedDateTime now = Dates.nowUtc(Clock.systemUTC());
        assertThrows(IllegalArgumentException.class, () -> UpdateCommand.validateDateArgument(now, trend, null, "name"));
        assertThrows(IllegalArgumentException.class, () -> UpdateCommand.validateDateArgument(now, remainder, null, "name"));
        assertDoesNotThrow(() -> UpdateCommand.validateDateArgument(now, range, null, "name"));

        assertDoesNotThrow(() -> UpdateCommand.validateDateArgument(now, trend, mock(), "name"));
        assertThrows(IllegalArgumentException.class, () -> UpdateCommand.validateDateArgument(now, remainder, now, "name"));
        assertDoesNotThrow(() -> UpdateCommand.validateDateArgument(now, remainder, now.plusHours(1L), "name"));
        assertDoesNotThrow(() -> UpdateCommand.validateDateArgument(now, range, now, "name"));
        assertThrows(IllegalArgumentException.class, () -> UpdateCommand.validateDateArgument(now, range, now.minusSeconds(1L), "name"));
        assertThrows(IllegalArgumentException.class, () -> UpdateCommand.validateDateArgument(now, range, now, DISPLAY_TO_DATE));
        assertDoesNotThrow(() -> UpdateCommand.validateDateArgument(now, range, now.plusHours(1L), DISPLAY_TO_DATE));
    }

    @Test
    void listeningDate() {
        assertThrows(NullPointerException.class, () -> UpdateCommand.listeningDate(null, mock()));
        assertThrows(NullPointerException.class, () -> UpdateCommand.listeningDate(mock(), null));

        ZonedDateTime now = Dates.nowUtc(Clock.systemUTC());
        ZonedDateTime fromDate = now.minusMinutes(146L);
        var alert = createTestAlertWithType(trend).withListeningDateRepeat(null, DEFAULT_REPEAT);
        alert = createTestAlertWithType(trend).withFromDate(fromDate).withListeningDateRepeat(null, DEFAULT_REPEAT);
        assertEquals(now, UpdateCommand.listeningDate(now, alert));
        alert = createTestAlertWithType(trend).withFromDate(fromDate).withListeningDateRepeat(fromDate.minusMinutes(45L), DEFAULT_REPEAT);
        assertEquals(fromDate.minusMinutes(45L), UpdateCommand.listeningDate(now, alert));

        assertEquals(fromDate, UpdateCommand.listeningDate(now, createTestAlertWithType(range).withFromDate(fromDate)));
        assertEquals(fromDate, UpdateCommand.listeningDate(now, createTestAlertWithType(remainder).withFromDate(fromDate)));
    }
}