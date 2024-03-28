package org.sbot.services.dao;

import org.jetbrains.annotations.NotNull;
import org.sbot.entities.notifications.Notification;
import org.sbot.entities.notifications.Notification.NotificationStatus;
import org.sbot.entities.notifications.RecipientType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Consumer;

public interface NotificationsDao {

    void addNotification(@NotNull Notification notification);

    @NotNull
    List<Notification> getNewNotifications(long limit);

    long unblockStatusOfRecipient(@NotNull RecipientType recipientType, @NotNull String userId);

    void statusRecipientBatchUpdate(@NotNull NotificationStatus status, @NotNull String recipientId, @NotNull RecipientType recipientType, @NotNull Consumer<BatchEntry> updater);

    void statusBatchUpdate(@NotNull NotificationStatus status, @NotNull Consumer<BatchEntry> updater);

    void delete(@NotNull Consumer<BatchEntry> deleter);

    long deleteHavingCreationDateBefore(@NotNull ZonedDateTime expirationDate);
}
