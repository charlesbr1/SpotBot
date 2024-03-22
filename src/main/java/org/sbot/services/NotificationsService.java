package org.sbot.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.notifications.MatchingNotification;
import org.sbot.entities.notifications.MigratedNotification.Reason;
import org.sbot.entities.notifications.Notification;
import org.sbot.entities.notifications.Notification.RecipientType;
import org.sbot.services.context.Context;
import org.sbot.services.context.ThreadSafeTxContext;
import org.sbot.services.discord.Discord;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_MEMBER;
import static net.dv8tion.jda.api.requests.ErrorResponse.UNKNOWN_USER;
import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.READ_UNCOMMITTED;
import static org.sbot.SpotBot.appProperties;
import static org.sbot.commands.MigrateCommand.migrateServerAlertsToPrivateChannel;
import static org.sbot.commands.MigrateCommand.migrateUserAlertsToPrivateChannel;
import static org.sbot.entities.alerts.Alert.Field.USER_ID;
import static org.sbot.entities.notifications.Notification.NotificationStatus.*;

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
        Thread.ofVirtual().name("Notifications thread").start(this::notificationsThread);
    }

    void notificationsThread() {
        try {
            while (!Thread.interrupted()) {
                semaphore.acquire();
                semaphore.drainPermits();
                sendNewNotifications();
            }
        } catch (InterruptedException e) {
            LOGGER.info("Notifications thread get interrupted", e);
        } finally {
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

    private void sendDiscordUserNotifications(@NotNull ThreadSafeTxContext txContext, @NotNull String recipientId, @NotNull List<Notification> notifications) {
        requireNonNull(txContext);
        requireNonNull(notifications);
        context.discord().userPrivateChannel(recipientId,
                sendDiscordNotifications(txContext, notifications),
                errorHandler(txContext, recipientId, notifications));
    }

    void sendDiscordServerNotifications(@NotNull ThreadSafeTxContext txContext, @NotNull String recipientId, @NotNull List<Notification> notifications) {
        requireNonNull(txContext);
        requireNonNull(notifications);
        var guild = context.discord().guildServer(recipientId);
        var spotBotChannel = guild.flatMap(Discord::spotBotChannel).orElse(null);
        if(null != spotBotChannel) {
            sendDiscordServerNotifications(spotBotChannel, txContext, guild.orElseThrow(), notifications);
        } else { // server does not exist, migrate server alerts to private messages
            try {
                LOGGER.info("Unable to retrieve guild channel {}, migrating user alerts to private channel", recipientId);
                migrateServerAlertsToPrivateChannel(txContext, Long.parseLong(recipientId), guild.orElse(null));
            } finally {
                txContext.commit(notifications.size());
            }
        }
    }

    private void sendDiscordServerNotifications(@NotNull MessageChannel channel, @NotNull ThreadSafeTxContext txContext, @NotNull Guild guild, @NotNull List<Notification> notifications) {
        // actually, all discord server notifications should be of type MatchingNotification...
        notifications.stream().filter(not(MatchingNotification.class::isInstance))
                .forEach(notification -> sendDiscordNotification(channel, txContext, notification));
        // process matching notifications, group them by their alert userId to minimize guild::retrieveMemberById calls
        notifications.stream().filter(MatchingNotification.class::isInstance)
                .collect(groupingBy(notification -> (Long) notification.fields.get(USER_ID)))
                // check user is still a guild member (we may have miss an event onGuildMemberRemove, or the notification is old and the user leaved since)
                .forEach((userId, matchingNotifications) ->
                        // no use of cache GatewayIntent.GUILD_MEMBERS for retrieveMemberById (REST call) as it looks quite costly to cache members just for this check, and jda rate limiter make sends already quite slow
                        guild.retrieveMemberById(userId).queue(
                                member -> matchingNotifications.forEach(notification -> sendDiscordNotification(channel, txContext, notification)),
                                discordMemberErrorHandler(txContext, guild, userId, notifications)));
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
            migrateUserNotifications(txContext, guild, userId, notifications);
        });
    }

    void migrateUserNotifications(@NotNull ThreadSafeTxContext txContext, @NotNull Guild guild, long userId, @NotNull List<Notification> notifications) {
        try {
            txContext.notificationsDao.statusRecipientBatchUpdate(NEW, String.valueOf(userId), RecipientType.DISCORD_USER,
                    updater -> notifications.forEach(notification -> updater.batchId(notification.id)));
            migrateUserAlertsToPrivateChannel(txContext, userId, notifications.getFirst().locale, guild, Reason.LEAVED);
            txContext.afterCommit(this::sendNotifications);
        } finally {
            if(!notifications.isEmpty()) {
                txContext.commit(notifications.size());
            }
        }
    }

    @NotNull
    private Consumer<MessageChannel> sendDiscordNotifications(@NotNull ThreadSafeTxContext txContext, @NotNull List<Notification> notifications) {
        requireNonNull(notifications);
        return channel -> notifications.forEach(notification -> sendDiscordNotification(channel, txContext, notification));
    }

    private void sendDiscordNotification(@NotNull MessageChannel channel, @NotNull ThreadSafeTxContext txContext, @NotNull Notification notification) {
        try {
            LOGGER.debug("Sending notification {}", notification);
            Runnable onSuccess = () -> deleteCallback(txContext, List.of(notification));
            Consumer<Boolean> onFailure = blocked -> updateStatusCallback(txContext, List.of(notification), blocked);
            Discord.sendMessages(channel, List.of(notification.asMessage(context)), onSuccess, onFailure);
        } catch (RuntimeException e) {
            var error = "Unable to send notification " + notification;
            LOGGER.error(error, e);
            updateStatusCallback(txContext, List.of(notification), false);
        }
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
            if(!notifications.isEmpty()) {
                txContext.commit(notifications.size());
            }
        }
    }

    static void deleteCallback(@NotNull ThreadSafeTxContext txContext, @NotNull List<Notification> notifications) {
        try {
            txContext.notificationsDao.delete(deleter -> notifications.forEach(notification -> deleter.batchId(notification.id)));
        } finally {
            if(!notifications.isEmpty()) {
                txContext.commit(notifications.size());
            }
        }
    }
}
