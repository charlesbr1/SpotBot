package org.sbot.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.Message;
import org.sbot.entities.settings.ServerSettings;
import org.sbot.entities.alerts.ClientType;
import org.sbot.entities.notifications.MigratedNotification.Reason;
import org.sbot.entities.notifications.Notification;
import org.sbot.entities.notifications.RecipientType;
import org.sbot.services.context.Context;
import org.sbot.services.context.ThreadSafeTxContext;
import org.sbot.services.dao.NotificationsDao;
import org.sbot.services.discord.Discord;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_MEMBER;
import static net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_USER;
import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.READ_UNCOMMITTED;
import static org.sbot.SpotBot.appProperties;
import static org.sbot.commands.MigrateCommand.migrateServerAlertsToPrivateChannel;
import static org.sbot.commands.MigrateCommand.migrateUserAlertsToPrivateChannel;
import static org.sbot.entities.settings.ServerSettings.DEFAULT_BOT_CHANNEL;
import static org.sbot.entities.settings.ServerSettings.DEFAULT_BOT_ROLE;
import static org.sbot.entities.alerts.Alert.Field.USER_ID;
import static org.sbot.entities.alerts.ClientType.DISCORD;
import static org.sbot.entities.notifications.Notification.NotificationStatus.*;
import static org.sbot.entities.notifications.RecipientType.DISCORD_USER;

public final class NotificationsService {

    private static final Logger LOGGER = LogManager.getLogger(NotificationsService.class);

    private static final int RESEND_DELAY_MINUTES = Math.max(1, appProperties.getIntOr("notifications.resend.delay.minutes", 60));

    private static final int BATCH_SIZE = 1000;


    private final Context context;
    private final Semaphore semaphore = new Semaphore(0);
    private final Runnable waitAndSendNotifications;

    public NotificationsService(@NotNull Context context) {
        this.context = requireNonNull(context);
        waitAndSendNotifications = this::waitAndSendNotifications; // store a single instance of this method ref
        sendNewNotifications(); // process any pending notifications on app start
        // this can't be done directly by notificationsThread due to a jdbi init thread sync issue (he should sleep a bit otherwise)
        Thread.ofVirtual().name("Notifications handler").start(this::notificationsThread);
    }

    void notificationsThread() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                semaphore.acquire();
                semaphore.drainPermits();
                sendNewNotifications();
            }
        } catch (InterruptedException e) {
            LOGGER.info("Notifications handler get interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    public void sendNotifications() {
        LOGGER.debug("Wake up Notifications thread");
        semaphore.release();
    }

    private void waitAndSendNotifications() {
        Thread.ofVirtual().name("Notifications retry thread").start(() -> {
            LockSupport.parkNanos(Duration.ofMinutes(RESEND_DELAY_MINUTES).toNanos());
            sendNotifications();
        });
    }

    void sendNewNotifications() {
        record Recipient(RecipientType type, String id) {}
        try {
            List<Notification> notifications;
            while(!(notifications = loadNewNotifications()).isEmpty()) {
                LOGGER.debug("Found {} notifications to send", notifications.size());
                var txContext = context.asThreadSafeTxContext(READ_UNCOMMITTED, notifications.size());
                notifications.stream() // group by recipient type and id
                        .collect(groupingBy(notification -> new Recipient(notification.recipientType, notification.recipientId)))
                        .forEach((recipient, notificationList) -> sendNotifications(txContext, recipient.type, recipient.id, notificationList));
            }
        } catch (RuntimeException e) {
            LOGGER.error("notificationsThread thrown exception", e);
        }
    }

    @NotNull
    List<Notification> loadNewNotifications() {
        return context.transactional(txCtx -> {
            var dao = txCtx.notificationsDao();
            var newNotifications = dao.getNewNotifications(BATCH_SIZE);
            dao.statusBatchUpdate(SENDING, updater -> newNotifications.forEach(notification -> updater.batchId(notification.id)));
            return newNotifications;
        }, READ_UNCOMMITTED, false);
    }

    private void sendNotifications(@NotNull ThreadSafeTxContext txContext, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull List<Notification> notifications) {
        // expect all notifications having same recipientType and recipientId
        try {
            switch (recipientType) {
                case DISCORD_USER:
                    sendDiscordUserNotifications(txContext, recipientId, notifications);
                    break;
                case DISCORD_SERVER:
                    sendDiscordServerNotifications(txContext, recipientId, notifications);
            }
        } catch (RuntimeException e) {
            var error = "Unexpected error occurred, deleting malformed notifications : " + notifications.stream().map(Object::toString).collect(joining(", "));
            LOGGER.error(error, e);
            deleteCallback(txContext, notifications);
        }
    }

    void sendDiscordUserNotifications(@NotNull ThreadSafeTxContext txContext, @NotNull String recipientId, @NotNull List<Notification> notifications) {
        requireNonNull(txContext);
        requireNonNull(notifications);
        context.discord().userPrivateChannel(recipientId,
                channel -> notifications.forEach(notification -> sendDiscordNotification(txContext, channel, null, null, notification)),
                errorHandler(txContext, recipientId, notifications));
    }

    void sendDiscordServerNotifications(@NotNull ThreadSafeTxContext txContext, @NotNull String recipientId, @NotNull List<Notification> notifications) {
        requireNonNull(txContext);
        requireNonNull(notifications);
        var guild = context.discord().guildServer(recipientId);
        if(null != guild) {
            var serverSettings = txContext.serverSettingsDao().getServerSettings(DISCORD, Long.parseLong(recipientId));
            var channelName = serverSettings.map(ServerSettings::spotBotChannel).orElse(DEFAULT_BOT_CHANNEL);
            var spotBotChannel = Discord.spotBotChannel(guild, channelName).orElse(null);
            if (null != spotBotChannel) {
                var spotBotRole = serverSettings.map(ServerSettings::spotBotRole).orElse(DEFAULT_BOT_ROLE);
                var spotBotRoleId = Discord.spotBotRole(guild, spotBotRole).map(Role::getId).orElse(null);
                sendDiscordServerNotifications(txContext, spotBotChannel, guild, spotBotRoleId, notifications);
                return;
            }
        }
        // server does not exist or channel is unreachable, migrate all server alerts to private messages
        migrateServerAlerts(txContext, guild, recipientId, notifications);
    }

    private void sendDiscordServerNotifications(@NotNull ThreadSafeTxContext txContext, @NotNull MessageChannel channel, @NotNull Guild guild, @Nullable String spotBotRoleId, @NotNull List<Notification> notifications) {
        var havingUserIdNotifications = notifications.stream().collect(groupingBy(notification -> notification.fields.containsKey(USER_ID)));
        // actually, all discord server notifications should be of type MatchingNotification and have USER_ID set...
        havingUserIdNotifications.getOrDefault(false, emptyList())
                .forEach(notification -> sendDiscordNotification(txContext, channel, spotBotRoleId, null, notification));
        // check USER_ID is still member of guild, group by USER_ID to minimize guild::retrieveMemberById calls
        havingUserIdNotifications.getOrDefault(true, emptyList()).stream()
                .collect(groupingBy(notification -> (Long) notification.fields.get(USER_ID)))
                // user may not be a guild member if we have miss an event onGuildMemberRemove, or if the notification is old and the user has leaved since
                .forEach((userId, userNotifications) ->
                        // no use of cache GatewayIntent.GUILD_MEMBERS for retrieveMemberById (REST call) as it looks quite costly to cache members just for this check, and jda rate limiter make sends already quite slow
                        guild.retrieveMemberById(userId).queue(
                                member -> userNotifications.forEach(notification ->
                                        sendDiscordNotification(txContext, channel, spotBotRoleId, userId, notification)),
                                discordMemberErrorHandler(txContext, guild, userId, userNotifications)));
    }

    private void sendDiscordNotification(@NotNull ThreadSafeTxContext txContext, @NotNull MessageChannel channel, @Nullable String spotBotRoleId, @Nullable Long userId, @NotNull Notification notification) {
        try {
            LOGGER.debug("Sending notification {}", notification);
            Runnable onSuccess = () -> deleteCallback(txContext, List.of(notification));
            Consumer<Boolean> onFailure = blocked -> updateStatusCallback(txContext, List.of(notification), blocked);
            Discord.sendMessage(channel, asMessageWithRoleAndUser(spotBotRoleId, userId, notification), onSuccess, onFailure);
        } catch (RuntimeException e) {
            var error = "Unable to send notification " + notification;
            LOGGER.error(error, e);
            updateStatusCallback(txContext, List.of(notification), false);
        }
    }

    @NotNull
    static Message asMessageWithRoleAndUser(@Nullable String spotBotRoleId, @Nullable Long userId, @NotNull Notification notification) {
        return null != spotBotRoleId || null != userId ?
                notification.asMessage().withRoleUser(spotBotRoleId, userId) : // mentioned role and user, they get notified this way
                notification.asMessage();
    }

    ErrorHandler errorHandler(@NotNull ThreadSafeTxContext txContext, @NotNull String recipientId, @NotNull List<Notification> notifications) {
        requireNonNull(txContext);
        requireNonNull(recipientId);
        requireNonNull(notifications);
        return new ErrorHandler(ex -> {
            // network error, retry later
            var error = "Network error occurred while retrieving discord user " + recipientId;
            LOGGER.info(error, ex);
            updateStatusCallback(txContext, notifications, false);
        }).handle(List.of(UNKNOWN_USER), e -> {
            LOGGER.info("Failed to send message, user {} was deleted, error : {}", recipientId, e.getMessage());
            deleteCallback(txContext, notifications);
        });
    }

    @NotNull
    ErrorHandler discordMemberErrorHandler(@NotNull ThreadSafeTxContext txContext, @NotNull Guild guild, long userId, @NotNull List<Notification> notifications) {
        requireNonNull(txContext);
        requireNonNull(guild);
        requireNonNull(notifications);
        return errorHandler(txContext, String.valueOf(userId), notifications).handle(List.of(UNKNOWN_MEMBER), e -> {
            LOGGER.info("User {} is no more a member of guild {}, migrating alerts to its private channel", userId, guild.getIdLong());
            migrateUserAlerts(txContext, DISCORD, guild, userId, notifications);
        });
    }

    void migrateServerAlerts(@NotNull ThreadSafeTxContext txContext, @Nullable Guild guild, @NotNull String recipientId, @NotNull List<Notification> notifications) {
        requireNonNull(notifications);
        List<Notification> toDelete = emptyList();
        try {
            LOGGER.info("Unable to retrieve guild channel {}, migrating server alerts to private channel", recipientId);
            migrateServerAlertsToPrivateChannel(txContext, DISCORD, Long.parseLong(recipientId), guild);
            // delete notifications that can't be redirected to user private channel
            toDelete = notifications.stream().filter(n -> !n.fields.containsKey(USER_ID)).toList();
            if(!toDelete.isEmpty()) { // actually, toDelete should always be empty (only matching notification type is on server)
                deleteCallback(txContext, toDelete);
            }
            notifications.stream().filter(notification -> notification.fields.containsKey(USER_ID)) // migrate notifications that have USER_ID to user private channel
                    .collect(groupingBy(notification -> (Long) notification.fields.get(USER_ID)))
                    .forEach((userId, userNotifications) -> migrateUserNotifications(txContext.notificationsDao, DISCORD, userId, userNotifications));
        } finally {
            txContext.commit(notifications.size() - toDelete.size()); // if not empty, toDelete have been committed already
        }
    }

    void migrateUserAlerts(@NotNull ThreadSafeTxContext txContext, @NotNull ClientType clientType, @NotNull Object server, long userId, @NotNull List<Notification> notifications) {
        try {
            migrateUserAlertsToPrivateChannel(txContext, clientType, userId, notifications.getFirst().locale, server, Reason.LEAVED);
            migrateUserNotifications(txContext.notificationsDao, clientType, userId, notifications);
            txContext.afterCommit(this::sendNotifications);
        } finally {
            txContext.commit(notifications.size());
        }
    }

    void migrateUserNotifications(@NotNull NotificationsDao notificationsDao, @NotNull ClientType clientType, long userId, @NotNull List<Notification> notifications) {
        requireNonNull(notifications);
        var recipientType = switch (clientType) { case DISCORD -> DISCORD_USER; };
        notificationsDao.statusRecipientBatchUpdate(NEW, String.valueOf(userId), recipientType,
                updater -> notifications.forEach(notification -> updater.batchId(notification.id)));
    }

    void updateStatusCallback(@NotNull ThreadSafeTxContext txContext, List<Notification> notifications, boolean blocked) {
        try {
            // blocked notification should be of type DISCORD_USER
            txContext.notificationsDao.statusBatchUpdate(blocked ? BLOCKED : NEW,
                    updater -> notifications.forEach(notification -> updater.batchId(notification.id)));
            if(!blocked) {
                txContext.afterCommit(waitAndSendNotifications);
            }
        } finally {
            txContext.commit(notifications.size());
        }
    }

    static void deleteCallback(@NotNull ThreadSafeTxContext txContext, @NotNull List<Notification> notifications) {
        try {
            txContext.notificationsDao.delete(deleter -> notifications.forEach(notification -> deleter.batchId(notification.id)));
        } finally {
            txContext.commit(notifications.size());
        }
    }
}
