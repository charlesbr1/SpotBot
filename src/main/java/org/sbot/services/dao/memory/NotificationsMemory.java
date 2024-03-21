package org.sbot.services.dao.memory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.notifications.Notification;
import org.sbot.entities.notifications.Notification.NotificationStatus;
import org.sbot.services.dao.BatchEntry;
import org.sbot.services.dao.NotificationsDao;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;
import static org.sbot.entities.notifications.Notification.RecipientType.DISCORD_USER;
import static org.sbot.services.dao.BatchEntry.longId;
import static org.sbot.utils.ArgumentValidator.requireStrictlyPositive;

public final class NotificationsMemory implements NotificationsDao {

    private static final Logger LOGGER = LogManager.getLogger(NotificationsMemory.class);

    private final AtomicLong idGenerator = new AtomicLong(1L);

    private final Map<Long, Notification> notifications = new ConcurrentHashMap<>();

    public NotificationsMemory() {
        LOGGER.debug("Loading memory storage for notifications");
    }

    @Override
    public void addNotification(@NotNull Notification notification) {
        LOGGER.debug("addNotification {}", notification);
        notification = notification.withId(idGenerator::getAndIncrement);
        notifications.put(notification.id, notification);
    }

    @Override
    @NotNull
    public List<Notification> getNewNotifications(long limit) {
        LOGGER.debug("getNewNotifications {}", limit);
        return notifications.values().stream().filter(Notification::isNew).limit(requireStrictlyPositive(limit)).toList();
    }

    @Override
    public long unblockStatusOfDiscordUser(@NotNull String userId) {
        LOGGER.debug("unblockStatusOfDiscordUser {}", userId);
        requireNonNull(userId);
        long[] updated = new long[] {0L};
        notifications.replaceAll((id, notification) ->
                notification.isBlocked() && DISCORD_USER.equals(notification.recipientType)
                                         && userId.equals(notification.recipientId)
                                         && ++updated[0] != 0 ?
                notification.withStatus(NEW) : notification);
        return updated[0];
    }

    @Override
    public void statusRecipientBatchUpdate(@NotNull NotificationStatus status, @NotNull String recipientId, @NotNull Notification.RecipientType recipientType, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("statusRecipientBatchUpdate {} {} {}", status, recipientId, recipientType);
        requireNonNull(status);
        requireNonNull(recipientId);
        requireNonNull(recipientType);
        updater.accept(ids -> notifications.computeIfPresent(longId(ids),
                (id, notification) -> notification.withStatusRecipient(status, recipientId, recipientType)));
    }

    @Override
    public void statusBatchUpdate(@NotNull NotificationStatus status, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("statusBatchUpdate {}", status);
        requireNonNull(status);
        updater.accept(ids -> notifications.computeIfPresent(longId(ids),
                (id, notification) -> notification.withStatus(status)));
    }

    @Override
    public void delete(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("batchDelete");
        deleter.accept(ids -> notifications.remove(longId(ids)));
    }

    @Override
    public long deleteHavingCreationDateBefore(@NotNull ZonedDateTime expirationDate) {
        LOGGER.debug("deleteHavingCreationDateBefore {}", expirationDate);
        requireNonNull(expirationDate);
        var toDelete = notifications.values().stream()
                .filter(notification -> notification.creationDate.isBefore(expirationDate)).toList();
        notifications.values().removeAll(toDelete);
        return toDelete.size();
    }
}
