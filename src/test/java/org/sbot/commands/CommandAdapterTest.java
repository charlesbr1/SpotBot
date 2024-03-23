package org.sbot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.sbot.commands.context.CommandContext;
import org.sbot.entities.Message;
import org.sbot.services.NotificationsService;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.DataServices;
import org.sbot.services.context.Context.Services;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.NotificationsDao;
import org.sbot.services.dao.UsersDao;
import org.sbot.utils.DatesTest;

import java.awt.*;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.commands.CommandAdapter.*;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.entities.alerts.Alert.Type.trend;
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.services.dao.AlertsDaoTest.assertDeepEquals;
import static org.sbot.utils.ArgumentValidator.requireOneItem;

public class CommandAdapterTest {

    public static void assertExceptionContains(@NotNull Class<? extends  Exception> type, @NotNull String message, @NotNull Runnable runnable) {
        try {
            runnable.run();
            fail("Expected exception " + type.getName() + " to be thrown with message : " + message);
        } catch (Exception e) {
            if(type.isAssignableFrom(e.getClass())) {
                assertTrue(e.getMessage().contains(message), "Exception " + type.getName() + " thrown, expected message : " + message + ", actual : " + e.getMessage());
            } else {
                fail("Expected exception " + type.getName() + " to be thrown with message : " + message + ", but got exception " + e);
            }
        }
    }

    @Test
    void constructor() {
        var adapter = new CommandAdapter("name", "description", Commands.slash("name", "desc"), 3) {
            @Override
            public void onCommand(@NotNull CommandContext context) {
            }
        };
        assertEquals("name", adapter.name());
        assertEquals("description", adapter.description());
        assertNotNull(adapter.options());
        assertEquals(3, adapter.responseTtlSeconds);

        assertThrows(NullPointerException.class, () -> new CommandAdapter(null, "description", mock(), 3) {
            @Override
            public void onCommand(@NotNull CommandContext context) {
            }
        });
        assertThrows(NullPointerException.class, () -> new CommandAdapter("name", null, mock(), 3) {
            @Override
            public void onCommand(@NotNull CommandContext context) {
            }
        });
        assertThrows(NullPointerException.class, () -> new CommandAdapter("name", "description", null, 3) {
            @Override
            public void onCommand(@NotNull CommandContext context) {
            }
        });
        assertThrows(IllegalArgumentException.class, () -> new CommandAdapter("name", "description", mock(), -1) {
            @Override
            public void onCommand(@NotNull CommandContext context) {
            }
        });
    }

    @Test
    void isPrivateChannel() {
        var context = mock(CommandContext.class);
        when(context.serverId()).thenReturn(123L);
        assertFalse(CommandAdapter.isPrivateChannel(context));
        when(context.serverId()).thenReturn(PRIVATE_MESSAGES);
        assertTrue(CommandAdapter.isPrivateChannel(context));
    }

    @Test
    void embedBuilder() {
        assertThrows(NullPointerException.class, () -> CommandAdapter.embedBuilder(null));
        var embed = CommandAdapter.embedBuilder("text");
        assertNotNull(embed);
        assertNull(embed.build().getFooter());
        assertNull(embed.build().getTitle());
        assertNull(embed.build().getColor());
        assertEquals("text", embed.build().getDescription());

        embed = CommandAdapter.embedBuilder("title", Color.green, "text");
        assertNotNull(embed);
        assertNull(embed.build().getFooter());
        assertEquals("title", embed.build().getTitle());
        assertEquals(Color.green, embed.build().getColor());
        assertEquals("text", embed.build().getDescription());
    }

    @Test
    void option() {
        assertThrows(IllegalArgumentException.class, () -> CommandAdapter.option(null, "name", "description", false));
        assertThrows(IllegalArgumentException.class, () -> CommandAdapter.option(OptionType.INTEGER, null, "description", false));
        assertThrows(IllegalArgumentException.class, () -> CommandAdapter.option(OptionType.INTEGER, "name", null, false));

        var option = CommandAdapter.option(OptionType.INTEGER, "name", "description", false);
        assertNotNull(option);
        assertEquals(OptionType.INTEGER, option.getType());
        assertEquals("name", option.getName());
        assertEquals("description", option.getDescription());
        assertFalse(option.isRequired());
        option = CommandAdapter.option(OptionType.INTEGER, "name", "description", true);
        assertTrue(option.isRequired());
    }

    @Test
    void securedAlertAccess() {
        assertThrows(NullPointerException.class, () -> CommandAdapter.securedAlertAccess(123L, null, mock()));
        assertThrows(NullPointerException.class, () -> CommandAdapter.securedAlertAccess(123L, mock(), null));

        var alertId = 123L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        var user = mock(User.class);
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        var member = mock(Member.class);
        var guild = mock(Guild.class);
        when(member.getGuild()).thenReturn(guild);
        when(messageReceivedEvent.getMember()).thenReturn(member);
        Context context = mock(Context.class);
        var alertsDao = mock(AlertsDao.class);
        var dataServices = mock(DataServices.class);
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);

        var alert = createTestAlertWithType(trend);

        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, "name"));
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.empty());

        var message = CommandAdapter.securedAlertAccess(alertId, commandContext, (a, dao) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao).getAlert(TEST_CLIENT_TYPE, alertId);
        assertNotNull(message);
        assertNotNull(message.embeds());
        assertEquals(1, message.embeds().size());
        assertTrue(message.embeds().get(0).build().getDescription().contains("not found"));
        assertEquals(NOT_FOUND_COLOR, message.embeds().get(0).build().getColor());

        // private channel, same user -> found
        when(alertsDao.getAlert(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(alert));
        when(commandContext.serverId()).thenReturn(PRIVATE_MESSAGES);
        when(user.getIdLong()).thenReturn(alert.userId);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, "name"));
        message = CommandAdapter.securedAlertAccess(alertId, commandContext, (a, dao) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao, times(2)).getAlert(TEST_CLIENT_TYPE, alertId);
        assertNotNull(message);
        assertNotNull(message.embeds());
        assertEquals(1, message.embeds().size());
        assertTrue(message.embeds().get(0).build().getDescription().contains("test"));
        assertEquals(OK_COLOR, message.embeds().get(0).build().getColor());

        // private channel, not same user -> not found
        when(user.getIdLong()).thenReturn(alert.userId + 3L);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, "name"));
        message = CommandAdapter.securedAlertAccess(alertId, commandContext, (a, dao) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao, times(3)).getAlert(TEST_CLIENT_TYPE, alertId);
        assertNotNull(message);
        assertNotNull(message.embeds());
        assertEquals(1, message.embeds().size());
        assertTrue(message.embeds().get(0).build().getDescription().contains("not found"));
        assertEquals(NOT_FOUND_COLOR, message.embeds().get(0).build().getColor());

        // public channel, same server -> found
        assertNotEquals(PRIVATE_MESSAGES, alert.serverId);
        when(guild.getIdLong()).thenReturn(alert.serverId);
        when(commandContext.serverId()).thenReturn(alert.serverId);
        message = CommandAdapter.securedAlertAccess(alertId, commandContext, (a, dao) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao, times(4)).getAlert(TEST_CLIENT_TYPE, alertId);
        assertNotNull(message);
        assertNotNull(message.embeds());
        assertEquals(1, message.embeds().size());
        assertTrue(message.embeds().get(0).build().getDescription().contains("test"));
        assertEquals(OK_COLOR, message.embeds().get(0).build().getColor());

        // public channel, not same server -> not found
        when(guild.getIdLong()).thenReturn(alert.serverId + 1L);
        when(commandContext.serverId()).thenReturn(alert.serverId + 1L);
        message = CommandAdapter.securedAlertAccess(alertId, commandContext, (a, dao) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao, times(5)).getAlert(TEST_CLIENT_TYPE, alertId);
        assertNotNull(message);
        assertNotNull(message.embeds());
        assertEquals(1, message.embeds().size());
        assertTrue(message.embeds().get(0).build().getDescription().contains("not found"));
        assertEquals(NOT_FOUND_COLOR, message.embeds().get(0).build().getColor());
    }

    @Test
    void securedAlertUpdate() {
        assertThrows(NullPointerException.class, () -> CommandAdapter.securedAlertUpdate(123L, null, mock()));
        assertThrows(NullPointerException.class, () -> CommandAdapter.securedAlertUpdate(123L, mock(), null));

        var alertId = 123L;
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        var user = mock(User.class);
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        var member = mock(Member.class);
        var guild = mock(Guild.class);
        when(member.getGuild()).thenReturn(guild);
        when(messageReceivedEvent.getMember()).thenReturn(member);
        Context context = mock(Context.class);
        var alertsDao = mock(AlertsDao.class);
        var notificationsDao = mock(NotificationsDao.class);
        var dataServices = mock(DataServices.class);
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        Services services = mock();
        when(context.services()).thenReturn(services);
        NotificationsService notificationsService = mock();
        when(services.notificationService()).thenReturn(notificationsService);

        var alert = createTestAlertWithType(trend);

        var commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, "name"));
        when(alertsDao.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.empty());

        var message = CommandAdapter.securedAlertUpdate(alertId, commandContext, (a, alerts, notifications) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        assertNotNull(message);
        assertTrue(requireOneItem(message.embeds()).build().getDescription().contains("not found"));
        assertEquals(NOT_FOUND_COLOR, requireOneItem(message.embeds()).build().getColor());

        // private channel, same user -> found
        when(alertsDao.getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId)).thenReturn(Optional.of(alert));
        when(user.getIdLong()).thenReturn(alert.userId);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, "name"));
        when(commandContext.serverId()).thenReturn(PRIVATE_MESSAGES);
        message = CommandAdapter.securedAlertUpdate(alertId, commandContext, (a, alerts, notifications) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao, times(2)).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(notificationsService, never()).sendNotifications();
        assertNotNull(message);
        assertTrue(requireOneItem(message.embeds()).build().getDescription().contains("test"));
        assertEquals(OK_COLOR, requireOneItem(message.embeds()).build().getColor());

        // private channel, not same user -> not found
        when(user.getIdLong()).thenReturn(alert.userId + 3L);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, "name"));
        message = CommandAdapter.securedAlertUpdate(alertId, commandContext, (a, alerts, notifications) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao, times(3)).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(notificationsService, never()).sendNotifications();
        assertNotNull(message);
        assertTrue(requireOneItem(message.embeds()).build().getDescription().contains("not found"));
        assertEquals(NOT_FOUND_COLOR, requireOneItem(message.embeds()).build().getColor());

        // public channel, same server, same user -> found
        assertNotEquals(PRIVATE_MESSAGES, alert.serverId);
        when(guild.getIdLong()).thenReturn(alert.serverId);
        when(user.getIdLong()).thenReturn(alert.userId);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, "name"));
        when(commandContext.serverId()).thenReturn(alert.serverId);

        message = CommandAdapter.securedAlertUpdate(alertId, commandContext, (a, alerts, notifications) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao, times(4)).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(notificationsService, never()).sendNotifications();
        assertNotNull(message);
        assertTrue(requireOneItem(message.embeds()).build().getDescription().contains("test"));
        assertEquals(OK_COLOR, requireOneItem(message.embeds()).build().getColor());

        // public channel, same server, not same user -> denied
        when(user.getIdLong()).thenReturn(alert.userId + 1);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, "name"));
        message = CommandAdapter.securedAlertUpdate(alertId, commandContext, (a, alerts, notifications) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao, times(5)).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(notificationsService, never()).sendNotifications();
        assertNotNull(message);
        assertTrue(requireOneItem(message.embeds()).build().getDescription().contains("not allowed"));
        assertEquals(DENIED_COLOR, requireOneItem(message.embeds()).build().getColor());

        // public channel, same server, not same user, admin -> found
        when(member.hasPermission(Permission.ADMINISTRATOR)).thenReturn(true);
        message = CommandAdapter.securedAlertUpdate(alertId, commandContext, (a, alerts, notifications) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao, times(6)).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(notificationsService, never()).sendNotifications();
        assertNotNull(message);
        assertTrue(requireOneItem(message.embeds()).build().getDescription().contains("test"));
        assertEquals(OK_COLOR, requireOneItem(message.embeds()).build().getColor());

        // public channel, not same server -> not found
        when(guild.getIdLong()).thenReturn(alert.serverId + 1L);
        when(commandContext.serverId()).thenReturn(alert.serverId + 1L);
        message = CommandAdapter.securedAlertUpdate(alertId, commandContext, (a, alerts, notifications) -> Message.of(CommandAdapter.embedBuilder("test")));
        verify(alertsDao, times(7)).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        verify(notificationsService, never()).sendNotifications();
        assertNotNull(message);
        assertTrue(requireOneItem(message.embeds()).build().getDescription().contains("not found"));
        assertEquals(NOT_FOUND_COLOR, requireOneItem(message.embeds()).build().getColor());
        verify(alertsDao, never()).getAlert(any(), anyLong());

        // test notifications
        when(user.getIdLong()).thenReturn(alert.userId);
        commandContext = spy(CommandContext.of(context, null, messageReceivedEvent, "name"));
        when(commandContext.serverId()).thenReturn(PRIVATE_MESSAGES);
        message = CommandAdapter.securedAlertUpdate(alertId, commandContext, (a, alerts, notifications) -> {
            var notificationDao = notifications.get();
            notificationDao.addNotification(mock());
            return Message.of(CommandAdapter.embedBuilder("test"));
        });
        verify(alertsDao, times(8)).getAlertWithoutMessage(TEST_CLIENT_TYPE, alertId);
        assertEquals(OK_COLOR, requireOneItem(message.embeds()).build().getColor());
        verify(notificationsDao).addNotification(any());
        verify(notificationsService).sendNotifications();
    }

    @Test
    void saveAlert() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        when(messageReceivedEvent.getAuthor()).thenReturn(mock());
        Context context = mock(Context.class);
        var usersDao = mock(UsersDao.class);
        var alertsDao = mock(AlertsDao.class);
        var dataServices = mock(DataServices.class);
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);

        var commandContext = CommandContext.of(context, null, messageReceivedEvent, "name");
        var alert = createTestAlert();
        when(usersDao.userExists(alert.userId)).thenReturn(false);
        assertEquals(Optional.empty(), CommandAdapter.saveAlert(commandContext, alert));
        verify(usersDao).userExists(alert.userId);
        verify(alertsDao, never()).addAlert(any());

        when(usersDao.userExists(alert.userId)).thenReturn(true);
        var newAlert = CommandAdapter.saveAlert(commandContext, alert);
        verify(usersDao, times(2)).userExists(alert.userId);
        verify(alertsDao).addAlert(any());
        assertTrue(newAlert.isPresent());
        assertDeepEquals(newAlert.get(), alert.withId(() -> newAlert.get().id));
    }

    @Test
    void createdAlertMessage() {
        ZonedDateTime now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> CommandAdapter.createdAlertMessage(null, now, mock()));
        assertThrows(NullPointerException.class, () -> CommandAdapter.createdAlertMessage(mock(), null, mock()));
        assertThrows(NullPointerException.class, () -> CommandAdapter.createdAlertMessage(mock(), now, null));

        var context = mock(CommandContext.class);
        when(context.serverId()).thenReturn(123L);
        var message = CommandAdapter.createdAlertMessage(context, now, createTestAlertWithType(remainder));
        assertNotNull(message);
        assertNotNull(message.embeds());
        assertEquals(1, message.embeds().size());
        assertNull(message.embeds().get(0).build().getFooter());
        assertNotNull(message.component());
        assertEquals(1, message.component().size());
        assertFalse(message.embeds().get(0).build().getDescription().contains("link"));

        message = CommandAdapter.createdAlertMessage(context, now, createTestAlertWithType(trend));
        assertNotNull(message);
        assertNotNull(message.embeds());
        assertEquals(1, message.embeds().size());
        assertNull(message.embeds().get(0).build().getFooter());
        assertNotNull(message.component());
        assertEquals(1, message.component().size());
        assertTrue(message.embeds().get(0).build().getDescription().contains("link"));
    }

    @Test
    void editableAlertMessage() {
        ZonedDateTime now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> CommandAdapter.editableAlertMessage(null, now, mock(), 0L, 0L));
        assertThrows(NullPointerException.class, () -> CommandAdapter.editableAlertMessage(mock(), null, mock(), 0L, 0L));
        assertThrows(NullPointerException.class, () -> CommandAdapter.editableAlertMessage(mock(), now, null, 0L, 0L));

        var context = mock(CommandContext.class);
        when(context.serverId()).thenReturn(123L);
        var message = CommandAdapter.editableAlertMessage(context, now, createTestAlert(), 1L, 0L);
        assertNotNull(message);
        assertNotNull(message.embeds());
        assertEquals(1, message.embeds().size());
        assertNotNull(message.component());
        assertEquals(1, message.component().size());
    }

    @Test
    void decoratedAlertEmbed() {
        ZonedDateTime now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> CommandAdapter.decoratedAlertEmbed(null, now, mock(), 0L, 0L));
        assertThrows(NullPointerException.class, () -> CommandAdapter.decoratedAlertEmbed(mock(), null, mock(), 0L, 0L));
        assertThrows(NullPointerException.class, () -> CommandAdapter.decoratedAlertEmbed(mock(), now, null, 0L, 0L));

        var context = mock(CommandContext.class);
        when(context.serverId()).thenReturn(123L);
        var embed = CommandAdapter.decoratedAlertEmbed(context, now, createTestAlert(), 1L, 0L);
        assertNotNull(embed);
        assertTrue(embed.build().getTitle().contains(createTestAlert().pair));
        assertNull(embed.build().getFooter());

        embed = CommandAdapter.decoratedAlertEmbed(context, now, createTestAlert(), 1L, 3L);
        assertNotNull(embed);
        assertTrue(embed.build().getTitle().contains(createTestAlert().pair));
        assertTrue(embed.build().getFooter().getText().contains("1/3"));
    }

    @Test
    void alertEmbed() {
        ZonedDateTime now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> CommandAdapter.alertEmbed(null, now, mock()));
        assertThrows(NullPointerException.class, () -> CommandAdapter.alertEmbed(mock(), null, mock()));
        assertThrows(NullPointerException.class, () -> CommandAdapter.alertEmbed(mock(), now, null));

        var context = mock(CommandContext.class);
        when(context.serverId()).thenReturn(123L);
        var embed = CommandAdapter.alertEmbed(context, now, createTestAlert());
        assertNotNull(embed);
        verify(context, never()).discord();

        when(context.serverId()).thenReturn(PRIVATE_MESSAGES);
        when(context.discord()).thenReturn(mock());
        embed = CommandAdapter.alertEmbed(context, now, createTestAlert());
        assertNotNull(embed);
        verify(context).discord();
    }

    @Test
    void userSetupNeeded() {
        assertThrows(NullPointerException.class, () -> CommandAdapter.userSetupNeeded(null, "message"));
        assertThrows(NullPointerException.class, () -> CommandAdapter.userSetupNeeded("title", null));
        var message = CommandAdapter.userSetupNeeded("title", "message");
        assertNotNull(message);
        assertEquals(1, message.embeds().size());
        assertEquals("title", message.embeds().get(0).build().getTitle());
        assertTrue(message.embeds().get(0).build().getDescription().contains("message"));

    }

    @Test
    void alertMessageTips() {
        assertThrows(NullPointerException.class, () -> CommandAdapter.alertMessageTips(null));
        assertNotNull(CommandAdapter.alertMessageTips("message"));
        assertTrue(CommandAdapter.alertMessageTips("message").contains("link"));
        assertFalse(CommandAdapter.alertMessageTips("http://").contains("link"));
        assertFalse(CommandAdapter.alertMessageTips("https://").contains("link"));
    }

    @Test
    void sendNotification() {
        MessageReceivedEvent messageReceivedEvent = mock(MessageReceivedEvent.class);
        when(messageReceivedEvent.getMessage()).thenReturn(mock());
        var user = mock(User.class);
        when(messageReceivedEvent.getAuthor()).thenReturn(user);
        var member = mock(Member.class);
        when(messageReceivedEvent.getMember()).thenReturn(member);
        when(member.getGuild()).thenReturn(mock());
        Context context = mock(Context.class);
        var commandContext = CommandContext.of(context, null, messageReceivedEvent, "name");
        assertTrue(CommandAdapter.sendNotification(commandContext, 123L, 1L));
        assertTrue(CommandAdapter.sendNotification(commandContext, 123L, 11L));
        assertFalse(CommandAdapter.sendNotification(commandContext, 123L, 0L));
        assertFalse(CommandAdapter.sendNotification(commandContext, 123L, -1L));
        when(user.getIdLong()).thenReturn(123L);
        commandContext = CommandContext.of(context, null, messageReceivedEvent, "name");
        assertFalse(CommandAdapter.sendNotification(commandContext, 123L, 1L));
        assertFalse(CommandAdapter.sendNotification(commandContext, 123L, 11L));
        assertFalse(CommandAdapter.sendNotification(commandContext, 123L, 0L));
        assertFalse(CommandAdapter.sendNotification(commandContext, 123L, -1L));
    }
}