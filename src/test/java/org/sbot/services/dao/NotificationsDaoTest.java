package org.sbot.services.dao;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.sbot.entities.notifications.MigratedNotification;
import org.sbot.entities.notifications.Notification;
import org.sbot.entities.notifications.Notification.NotificationStatus;
import org.sbot.entities.notifications.Notification.RecipientType;
import org.sbot.utils.DatesTest;

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.User.DEFAULT_LOCALE;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.notifications.Notification.NotificationStatus.*;
import static org.sbot.utils.ArgumentValidator.requireOneItem;

public abstract class NotificationsDaoTest {

    public static void assertDeepEquals(@Nullable Notification notification, @Nullable Notification other) {
        if (other == notification) return;
        assertTrue(null != notification && null != other);
        assertTrue(notification.id == other.id &&
                notification.creationDate.truncatedTo(ChronoUnit.MILLIS).isEqual(other.creationDate.truncatedTo(ChronoUnit.MILLIS)) &&
                notification.status == other.status &&
                notification.type == other.type &&
                notification.recipientType == other.recipientType &&
                Objects.equals(notification.recipientId, other.recipientId) &&
                Objects.equals(notification.locale, other.locale) &&
                Objects.equals(notification.fields, other.fields));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void addNotification(NotificationsDao notifications) {
        assertThrows(NullPointerException.class, () -> notifications.addNotification(null));

        var notification = MigratedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notification);
        assertEquals(1, notifications.getNewNotifications(100).size());
        assertDeepEquals(notification.withId(() -> 1L), notifications.getNewNotifications(1).getFirst());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void getNewNotifications(NotificationsDao notifications) {
        assertThrows(IllegalArgumentException.class, () -> notifications.getNewNotifications(0L));
        assertThrows(IllegalArgumentException.class, () -> notifications.getNewNotifications(-1L));

        assertEquals(0, notifications.getNewNotifications(100).size());
        var notification = MigratedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        assertEquals(3, notifications.getNewNotifications(100).size());
        notifications.statusBatchUpdate(NotificationStatus.SENDING, updater -> updater.batchId(3));
        assertEquals(2, notifications.getNewNotifications(100).size());
        notifications.statusBatchUpdate(BLOCKED, updater -> updater.batchId(1));
        assertDeepEquals(notification.withId(() -> 2), requireOneItem(notifications.getNewNotifications(100)));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void unblockStatusOfDiscordUser(NotificationsDao notifications) {
        assertThrows(NullPointerException.class, () -> notifications.unblockStatusOfDiscordUser(null));

        var notificationU1 = MigratedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 111L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notificationU1);
        var notificationU2 = MigratedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 222L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notificationU2.withStatus(BLOCKED));
        notifications.addNotification(notificationU2);
        var notificationU3 = MigratedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 333L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notificationU3.withStatus(BLOCKED));
        notifications.addNotification(notificationU3.withStatus(BLOCKED));
        notifications.addNotification(notificationU3);

        var notifs = notifications.getNewNotifications(100);
        assertEquals(3, notifs.size());
        assertTrue(notifs.contains(notificationU1.withId(() -> 1)));
        assertTrue(notifs.contains(notificationU2.withId(() -> 3)));
        assertTrue(notifs.contains(notificationU3.withId(() -> 6)));

        assertEquals(1, notifications.unblockStatusOfDiscordUser("222"));
        notifs = notifications.getNewNotifications(100);
        assertEquals(4, notifs.size());
        assertTrue(notifs.contains(notificationU1.withId(() -> 1)));
        assertTrue(notifs.contains(notificationU2.withId(() -> 2)));
        assertTrue(notifs.contains(notificationU2.withId(() -> 3)));
        assertTrue(notifs.contains(notificationU3.withId(() -> 6)));

        assertEquals(0, notifications.unblockStatusOfDiscordUser("111"));
        notifs = notifications.getNewNotifications(100);
        assertEquals(4, notifs.size());
        assertTrue(notifs.contains(notificationU1.withId(() -> 1)));
        assertTrue(notifs.contains(notificationU2.withId(() -> 2)));
        assertTrue(notifs.contains(notificationU2.withId(() -> 3)));
        assertTrue(notifs.contains(notificationU3.withId(() -> 6)));

        assertEquals(2, notifications.unblockStatusOfDiscordUser("333"));
        notifs = notifications.getNewNotifications(100);
        assertEquals(6, notifs.size());
        assertTrue(notifs.contains(notificationU1.withId(() -> 1)));
        assertTrue(notifs.contains(notificationU2.withId(() -> 2)));
        assertTrue(notifs.contains(notificationU2.withId(() -> 3)));
        assertTrue(notifs.contains(notificationU2.withId(() -> 4)));
        assertTrue(notifs.contains(notificationU2.withId(() -> 5)));
        assertTrue(notifs.contains(notificationU3.withId(() -> 6)));
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void statusRecipientBatchUpdate(NotificationsDao notifications) {
        assertThrows(NullPointerException.class, () -> notifications.statusRecipientBatchUpdate(null, "123", RecipientType.DISCORD_USER, u -> {}));
        assertThrows(NullPointerException.class, () -> notifications.statusRecipientBatchUpdate(SENDING, null, RecipientType.DISCORD_USER, u -> {}));
        assertThrows(NullPointerException.class, () -> notifications.statusRecipientBatchUpdate(SENDING, "123", null, u -> {}));
        assertThrows(NullPointerException.class, () -> notifications.statusRecipientBatchUpdate(SENDING, "123", RecipientType.DISCORD_USER, null));

        var notification = MigratedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        assertEquals(3, notifications.getNewNotifications(100).size());

        notifications.statusRecipientBatchUpdate(SENDING, "newId", RecipientType.DISCORD_SERVER, updater -> updater.batchId(2));
        assertEquals(2, notifications.getNewNotifications(100).size());
        assertEquals(Set.of(1L, 3L), notifications.getNewNotifications(100).stream().map(n -> n.id).collect(toSet()));

        notifications.statusRecipientBatchUpdate(SENDING, "newId", RecipientType.DISCORD_SERVER, updater -> { updater.batchId(1); updater.batchId(3);});
        assertEquals(0, notifications.getNewNotifications(100).size());

        notifications.statusRecipientBatchUpdate(NEW, "newId", RecipientType.DISCORD_SERVER, updater -> { updater.batchId(2); updater.batchId(3);});
        assertEquals(2, notifications.getNewNotifications(100).size());
        assertEquals(Set.of(2L, 3L), notifications.getNewNotifications(100).stream().map(n -> n.id).collect(toSet()));
        assertEquals(RecipientType.DISCORD_SERVER, notifications.getNewNotifications(2).getFirst().recipientType);
        assertEquals("newId", notifications.getNewNotifications(2).getFirst().recipientId);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void statusBatchUpdate(NotificationsDao notifications) {
        assertThrows(NullPointerException.class, () -> notifications.statusBatchUpdate(null, u -> {}));
        assertThrows(NullPointerException.class, () -> notifications.statusBatchUpdate(SENDING, null));

        var notification = MigratedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        assertEquals(3, notifications.getNewNotifications(100).size());

        notifications.statusBatchUpdate(SENDING, updater -> { updater.batchId(2); updater.batchId(3); });
        assertEquals(1, notifications.getNewNotifications(100).size());
        assertEquals(1L, notifications.getNewNotifications(100).getFirst().id);
        assertEquals(NEW, notifications.getNewNotifications(100).getFirst().status);

        notifications.statusBatchUpdate(BLOCKED, updater -> { updater.batchId(1); updater.batchId(3); });
        assertEquals(0, notifications.getNewNotifications(100).size());

        notifications.statusBatchUpdate(NEW, updater -> { updater.batchId(2); updater.batchId(1); });
        assertEquals(2, notifications.getNewNotifications(100).size());
        assertEquals(Set.of(2L, 1L), notifications.getNewNotifications(100).stream().map(n -> n.id).collect(toSet()));
        assertEquals(NEW, notifications.getNewNotifications(100).getFirst().status);
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void delete(NotificationsDao notifications) {
        assertThrows(NullPointerException.class, () -> notifications.delete(null));

        var notification = MigratedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        assertEquals(3, notifications.getNewNotifications(100).size());

        notifications.delete(deleter -> { deleter.batchId(2); deleter.batchId(3); });
        assertEquals(1, notifications.getNewNotifications(100).size());
        assertEquals(1L, notifications.getNewNotifications(100).getFirst().id);
        assertEquals(NEW, notifications.getNewNotifications(100).getFirst().status);

        notifications.delete(deleter -> { deleter.batchId(1); deleter.batchId(3); });
        assertEquals(0, notifications.getNewNotifications(100).size());

        notifications.statusBatchUpdate(NEW, updater -> { updater.batchId(1); updater.batchId(2); });
        assertEquals(0, notifications.getNewNotifications(100).size());
    }

    @ParameterizedTest
    @MethodSource("provideDao")
    void deleteHavingCreationDateBefore(NotificationsDao notifications) {
        assertThrows(NullPointerException.class, () -> notifications.deleteHavingCreationDateBefore(null));

        var now = DatesTest.nowUtc();
        var notification = MigratedNotification.of(now, DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        assertEquals(3, notifications.getNewNotifications(100).size());

        notification = MigratedNotification.of(now.minusHours(3L), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notification);
        notifications.addNotification(notification);
        assertEquals(5, notifications.getNewNotifications(100).size());

        notification = MigratedNotification.of(now.minusDays(1L), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notification);
        assertEquals(6, notifications.getNewNotifications(100).size());

        notification = MigratedNotification.of(now.minusDays(2L), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromGuild", "toGuild", MigratedNotification.Reason.ADMIN, 1L);
        notifications.addNotification(notification);
        assertEquals(7, notifications.getNewNotifications(100).size());

        assertEquals(0L, notifications.deleteHavingCreationDateBefore(now.minusDays(3L)));
        assertEquals(2L, notifications.deleteHavingCreationDateBefore(now.minusDays(1L).plusSeconds(1L)));
        assertEquals(5, notifications.getNewNotifications(100).size());
        assertEquals(0L, notifications.deleteHavingCreationDateBefore(now.minusDays(1L).plusSeconds(1L)));
        assertEquals(0L, notifications.deleteHavingCreationDateBefore(now.minusHours(8L)));
        assertEquals(5, notifications.getNewNotifications(100).size());
        assertEquals(0L, notifications.deleteHavingCreationDateBefore(now.minusHours(3L)));
        assertEquals(2L, notifications.deleteHavingCreationDateBefore(now.minusHours(3L).plusSeconds(1L)));
        assertEquals(3, notifications.getNewNotifications(100).size());
        assertEquals(0L, notifications.deleteHavingCreationDateBefore(now.minusHours(3L).plusSeconds(1L)));
        assertEquals(0L, notifications.deleteHavingCreationDateBefore(now.minusMinutes(1L)));
        assertEquals(0L, notifications.deleteHavingCreationDateBefore(now.minusSeconds(1L)));
        assertEquals(0L, notifications.deleteHavingCreationDateBefore(now));
        assertEquals(3, notifications.getNewNotifications(100).size());
        assertEquals(3L, notifications.deleteHavingCreationDateBefore(now.plusSeconds(1L)));
        assertEquals(0, notifications.getNewNotifications(100).size());
    }
}