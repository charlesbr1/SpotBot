package org.sbot.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import org.junit.jupiter.api.Test;
import org.sbot.entities.chart.DatedPrice;
import org.sbot.entities.notifications.MatchingNotification;
import org.sbot.entities.notifications.Notification;
import org.sbot.services.context.Context;
import org.sbot.services.context.Context.DataServices;
import org.sbot.services.context.ThreadSafeTxContext;
import org.sbot.services.context.TransactionalContext;
import org.sbot.services.dao.AlertsDao;
import org.sbot.services.dao.NotificationsDao;
import org.sbot.services.dao.UsersDao;
import org.sbot.services.discord.Discord;
import org.sbot.utils.DatesTest;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

import static java.math.BigDecimal.ONE;
import static java.util.Collections.emptyMap;
import static net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_MEMBER;
import static net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_USER;
import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.READ_COMMITTED;
import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.READ_UNCOMMITTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.sbot.entities.User.DEFAULT_LOCALE;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.entities.alerts.AlertTest.TEST_CLIENT_TYPE;
import static org.sbot.entities.alerts.AlertTest.createTestAlertWithUserId;
import static org.sbot.entities.notifications.Notification.NotificationStatus.*;
import static org.sbot.entities.notifications.RecipientType.DISCORD_USER;
import static org.sbot.services.discord.Discord.DISCORD_BOT_CHANNEL;

class NotificationsServiceTest {

    @Test
    void constructor() {
        assertThrows(NullPointerException.class, () -> new NotificationsService(null));
    }

    @Test
    void sendNotifications() {
        Context context = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(notificationsDao.getNewNotifications(anyLong())).thenAnswer(a -> {
            LockSupport.parkNanos(Duration.ofMillis(250L).toNanos());
            return List.of();
        });
        var txCtx = new TransactionalContext(context, READ_COMMITTED);
        var notificationService = new NotificationsService(txCtx);
        LockSupport.parkNanos(Duration.ofMillis(500L).toNanos());
        verify(notificationsDao).getNewNotifications(anyLong());
        notificationService.sendNotifications(); // check sendNotification is not done for each call
        notificationService.sendNotifications();
        notificationService.sendNotifications();
        notificationService.sendNotifications();
        notificationService.sendNotifications();
        LockSupport.parkNanos(Duration.ofMillis(700L).toNanos());
        verify(notificationsDao, atMost(3)).getNewNotifications(anyLong());
    }

    @Test
    void sendNewNotifications() {
        var alert = createTestAlertWithUserId(111L).withServerId(PRIVATE_MESSAGES);
        var notification1 = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingService.MatchingAlert.MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));
        var notification2 = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingService.MatchingAlert.MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));
        alert = createTestAlertWithUserId(222L).withServerId(555L);
        var notification3 = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingService.MatchingAlert.MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));
        var notification4 = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingService.MatchingAlert.MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));
        alert = createTestAlertWithUserId(333L).withServerId(777);
        var notification5 = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingService.MatchingAlert.MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));
        alert = createTestAlertWithUserId(999L).withServerId(PRIVATE_MESSAGES);
        var notification6 = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingService.MatchingAlert.MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));
        var notification7 = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingService.MatchingAlert.MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));
        alert = createTestAlertWithUserId(999L).withServerId(555L);
        var notification8 = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingService.MatchingAlert.MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));

        Context context = mock();
        Discord discord = mock();
        when(discord.guildServer(anyLong())).thenReturn(Optional.empty());
        Context.Services services = mock();
        when(context.services()).thenReturn(services);
        when(services.discord()).thenReturn(discord);
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(notificationsDao.getNewNotifications(anyLong()))
                .thenReturn(List.of(notification1, notification2, notification3, notification4, notification5, notification6, notification7, notification8))
                .thenReturn(List.of());
        var txCtx = new TransactionalContext(context, READ_COMMITTED);
        var notificationService = new NotificationsService(txCtx);

        notificationService.sendNewNotifications();

        verify(discord).userPrivateChannel(eq("111"), any(), any());
        verify(discord, never()).userPrivateChannel(eq("222"), any(), any());
        verify(discord, never()).userPrivateChannel(eq("333"), any(), any());
        verify(discord).userPrivateChannel(eq("999"), any(), any());

        verify(discord, never()).guildServer("" + PRIVATE_MESSAGES);
        verify(discord).guildServer("555");
        verify(discord).guildServer("777");
    }

    @Test
    void loadNewNotifications() {
        Context context = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        when(notificationsDao.getNewNotifications(anyLong())).thenReturn(List.of());
        var txCtx = new TransactionalContext(context, READ_COMMITTED);
        var notificationService = new NotificationsService(txCtx);
        LockSupport.parkNanos(Duration.ofMillis(500L).toNanos());
        verify(notificationsDao).getNewNotifications(anyLong());
        verify(notificationsDao).statusBatchUpdate(eq(SENDING), any());

        assertEquals(0, notificationService.loadNewNotifications().size());
        verify(notificationsDao, times(2)).getNewNotifications(anyLong());
        verify(notificationsDao, times(2)).statusBatchUpdate(eq(SENDING), any());
    }

    @Test
    void sendDiscordServerNotifications() {
        var alert = createTestAlertWithUserId(123L).withServerId(555L);
        var notification1 = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingService.MatchingAlert.MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));
        var notification2 = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingService.MatchingAlert.MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));

        Context context = mock();
        when(context.clock()).thenReturn(Clock.systemUTC());
        Discord discord = mock();
        when(discord.guildServer(anyLong())).thenReturn(Optional.empty());
        Context.Services services = mock();
        when(context.services()).thenReturn(services);
        when(services.discord()).thenReturn(discord);
        UsersDao usersDao = mock();
        AlertsDao alertsDao = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.usersDao()).thenReturn(v -> usersDao);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        var txCtx = new TransactionalContext(context, READ_COMMITTED);
        var notificationService = new NotificationsService(txCtx);

        assertThrows(NullPointerException.class, () -> notificationService.sendDiscordServerNotifications(null, "555", List.of(notification1, notification2)));
        assertThrows(NumberFormatException.class, () -> notificationService.sendDiscordServerNotifications(new ThreadSafeTxContext(context, READ_COMMITTED, 2), null, List.of(notification1, notification2)));
        assertThrows(NullPointerException.class, () -> notificationService.sendDiscordServerNotifications(new ThreadSafeTxContext(context, READ_COMMITTED, 2), "555", null));

        when(discord.guildServer("555")).thenReturn(Optional.empty());
        when(alertsDao.getUserIdsByServerId(TEST_CLIENT_TYPE, 555L)).thenReturn(List.of(123L));
        when(usersDao.getLocales(List.of(123L))).thenReturn(emptyMap());

        notificationService.sendDiscordServerNotifications(new ThreadSafeTxContext(context, READ_COMMITTED, 2), "555", List.of(notification1, notification2));
        // check server alerts are migrated to user private channel
        verify(discord).guildServer("555");
        verify(alertsDao).getUserIdsByServerId(TEST_CLIENT_TYPE, 555L);
        verify(usersDao).getLocales(List.of(123L));
        verify(notificationsDao).addNotification(any());

        // check notifications are send
        Guild guild = mock();
        when(discord.guildServer("555")).thenReturn(Optional.of(guild));
        TextChannel textChannel = mock();
        when(guild.getTextChannelsByName(DISCORD_BOT_CHANNEL, true)).thenReturn(List.of(textChannel));
        when(guild.retrieveMemberById(123L)).thenReturn(mock());
        notificationService.sendDiscordServerNotifications(new ThreadSafeTxContext(context, READ_COMMITTED, 2), "555", List.of(notification1, notification2));

        verify(discord, times(2)).guildServer("555");
        verify(alertsDao).getUserIdsByServerId(TEST_CLIENT_TYPE, 555L);
        verify(usersDao).getLocales(List.of(123L));
        verify(notificationsDao).addNotification(any());
        verify(guild).retrieveMemberById(123L);
    }

    @Test
    void errorHandler() {
        Context context = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        var txCtx = new TransactionalContext(context, READ_COMMITTED);
        var notificationService = new NotificationsService(txCtx);

        assertThrows(NullPointerException.class, () -> notificationService.errorHandler(null, "123", List.of()));
        assertThrows(NullPointerException.class, () -> notificationService.errorHandler(txCtx.asThreadSafeTxContext(READ_UNCOMMITTED, 1), null, List.of()));
        assertThrows(NullPointerException.class, () -> notificationService.errorHandler(txCtx.asThreadSafeTxContext(READ_UNCOMMITTED, 1), "123", null));

        var errorHandler = notificationService.errorHandler(txCtx.asThreadSafeTxContext(READ_UNCOMMITTED, 1), "123", List.of());

        errorHandler.accept(new Exception()); // network error, should set status NEW
        verify(notificationsDao).statusBatchUpdate(eq(NEW), any());

        ErrorResponseException error = mock();
        when(error.getErrorResponse()).thenReturn(UNKNOWN_USER);
        errorHandler.accept(error); // user removed, should delete the notification
        verify(notificationsDao).delete(any());
    }

    @Test
    void discordMemberErrorHandler() {
        Context context = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        var txCtx = new TransactionalContext(context, READ_COMMITTED);
        var notificationService = new NotificationsService(txCtx);
        Guild guild = mock();

        assertThrows(NullPointerException.class, () -> notificationService.discordMemberErrorHandler(null, mock(), 123L, List.of()));
        assertThrows(NullPointerException.class, () -> notificationService.discordMemberErrorHandler(txCtx.asThreadSafeTxContext(READ_UNCOMMITTED, 1), null, 123L, List.of()));
        assertThrows(NullPointerException.class, () -> notificationService.discordMemberErrorHandler(txCtx.asThreadSafeTxContext(READ_UNCOMMITTED, 1), mock(), 123L, null));

        Notification notification = mock();
        var txContext = spy(txCtx.asThreadSafeTxContext(READ_UNCOMMITTED, 3));
        var errorHandler = notificationService.discordMemberErrorHandler(txContext, guild, 123L, List.of(notification));

        errorHandler.accept(new Exception()); // network error, should set status NEW
        verify(notificationsDao).statusBatchUpdate(eq(NEW), any());
        verify(notificationsDao, never()).statusRecipientBatchUpdate(any(), any(), any(), any());
        verify(notificationsDao, never()).delete(any());
        verify(txContext).commit(1);

        ErrorResponseException error = mock();
        when(error.getErrorResponse()).thenReturn(UNKNOWN_USER);
        errorHandler.accept(error); // user removed, should delete the notification
        verify(notificationsDao).statusBatchUpdate(eq(NEW), any());
        verify(notificationsDao, never()).statusRecipientBatchUpdate(any(), any(), any(), any());
        verify(notificationsDao).delete(any());
        verify(txContext, times(2)).commit(1);

        when(error.getErrorResponse()).thenReturn(UNKNOWN_MEMBER);
        AlertsDao alertsDao = mock();
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        errorHandler.accept(error); // user leaved guild, should resend the notification in private and  migrate its alerts
        verify(notificationsDao).delete(any());
        verify(notificationsDao).statusRecipientBatchUpdate(eq(NEW), eq("123"), eq(DISCORD_USER), any());
        verify(alertsDao).updateServerIdOf(any(), eq(PRIVATE_MESSAGES));
        verify(txContext, times(3)).commit(1);
    }

    @Test
    void migrateUserNotifications() {
        Context context = mock();
        AlertsDao alertsDao = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.alertsDao()).thenReturn(v -> alertsDao);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        var txCtx = new TransactionalContext(context, READ_COMMITTED);
        var notificationService = new NotificationsService(txCtx);
        Guild guild = mock();
        Notification notification = mock();

        var txContext = spy(txCtx.asThreadSafeTxContext(READ_UNCOMMITTED, 1));
        notificationService.migrateUserNotifications(TEST_CLIENT_TYPE, txContext, guild, 123L, List.of(notification));
        verify(notificationsDao).statusRecipientBatchUpdate(eq(NEW), eq("123"), eq(DISCORD_USER), any());
        verify(alertsDao).updateServerIdOf(any(), eq(PRIVATE_MESSAGES));
        verify(txContext).commit(1);

        var ftx = spy(txCtx.asThreadSafeTxContext(READ_UNCOMMITTED, 1));
        assertThrows(IllegalStateException.class, () -> notificationService.migrateUserNotifications(TEST_CLIENT_TYPE, ftx, guild, 123L, List.of(notification, notification)));
        verify(notificationsDao, times(2)).statusRecipientBatchUpdate(eq(NEW), eq("123"), eq(DISCORD_USER), any());
        verify(alertsDao, times(2)).updateServerIdOf(any(), eq(PRIVATE_MESSAGES));
        verify(ftx).commit(2);

        txContext = spy(txCtx.asThreadSafeTxContext(READ_UNCOMMITTED, 2));
        notificationService.migrateUserNotifications(TEST_CLIENT_TYPE, txContext, guild, 123L, List.of(notification, notification));
        verify(notificationsDao, times(3)).statusRecipientBatchUpdate(eq(NEW), eq("123"), eq(DISCORD_USER), any());
        verify(alertsDao, times(3)).updateServerIdOf(any(), eq(PRIVATE_MESSAGES));
        verify(txContext).commit(2);
    }

    @Test
    void updateStatusCallback() {
        Context context = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        var txCtx = new TransactionalContext(context, READ_COMMITTED);
        var notificationService = new NotificationsService(txCtx);
        var txContext = spy(new ThreadSafeTxContext(context, READ_COMMITTED, 1));
        Notification notification = mock();

        notificationService.updateStatusCallback(txContext, List.of(notification), false);
        verify(notificationsDao).statusBatchUpdate(eq(NEW), any());
        verify(txContext).afterCommit(any());
        verify(txContext).commit(1);

        txContext = spy(new ThreadSafeTxContext(context, READ_COMMITTED, 2));
        notificationService.updateStatusCallback(txContext, List.of(notification, notification), true);
        verify(notificationsDao).statusBatchUpdate(eq(BLOCKED), any());
        verify(txContext, never()).afterCommit(any());
        verify(txContext).commit(2);
    }

    @Test
    void deleteCallback() {
        Context context = mock();
        NotificationsDao notificationsDao = mock();
        DataServices dataServices = mock();
        when(context.dataServices()).thenReturn(dataServices);
        when(dataServices.notificationsDao()).thenReturn(v -> notificationsDao);
        var txContext = spy(new ThreadSafeTxContext(context, READ_COMMITTED, 1));
        Notification notification = mock();

        NotificationsService.deleteCallback(txContext, List.of(notification));
        verify(notificationsDao).delete(any());
        verify(txContext).commit(1);

        txContext = spy(new ThreadSafeTxContext(context, READ_COMMITTED, 2));
        NotificationsService.deleteCallback(txContext, List.of(notification, notification));
        verify(notificationsDao, times(2)).delete(any());
        verify(txContext).commit(2);
    }
}