package org.sbot.entities.notifications;

import org.junit.jupiter.api.Test;
import org.sbot.entities.notifications.MigratedNotification.Reason;
import org.sbot.utils.DatesTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sbot.entities.FieldParser.format;
import static org.sbot.entities.UserSettings.DEFAULT_LOCALE;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.alerts.AlertTest.TEST_CLIENT_TYPE;
import static org.sbot.entities.notifications.MigratedNotification.Field.*;
import static org.sbot.entities.notifications.MigratedNotification.Field.ALERT_ID;
import static org.sbot.entities.notifications.Notification.*;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;
import static org.sbot.entities.notifications.Notification.NotificationStatus.SENDING;
import static org.sbot.entities.notifications.RecipientType.DISCORD_USER;
import static org.sbot.utils.ArgumentValidator.requireOneItem;

class MigratedNotificationTest {

    @Test
    void of() {
        var now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> MigratedNotification.of(TEST_CLIENT_TYPE, null, DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromServer", "toServer", Reason.ADMIN, 1L));
        assertThrows(NullPointerException.class, () -> MigratedNotification.of(TEST_CLIENT_TYPE, now, null, 123L, 321L, range, "tickerOrPair", "fromServer", "toServer", Reason.ADMIN, 1L));
        assertThrows(NullPointerException.class, () -> MigratedNotification.of(TEST_CLIENT_TYPE, now, DEFAULT_LOCALE, 123L, 321L, range, null, "fromServer", "toServer", Reason.ADMIN, 1L));
        assertThrows(NullPointerException.class, () -> MigratedNotification.of(TEST_CLIENT_TYPE, now, DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", null, "toServer", Reason.ADMIN, 1L));
        assertThrows(NullPointerException.class, () -> MigratedNotification.of(TEST_CLIENT_TYPE, now, DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromServer", "toServer", null, 1L));

        var notification = MigratedNotification.of(TEST_CLIENT_TYPE, now, DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromServer", "toServer", Reason.ADMIN, 3L);
        assertEquals(NEW_NOTIFICATION_ID, notification.id);
        assertEquals(now, notification.creationDate);
        assertEquals(NEW, notification.status);
        assertEquals("123", notification.recipientId);
        assertEquals(DISCORD_USER, notification.recipientType);
        assertEquals(DEFAULT_LOCALE, notification.locale);

        assertEquals(321L, notification.fields.get(ALERT_ID));
        assertEquals(range, notification.fields.get(TYPE));
        assertEquals("tickerOrPair", notification.fields.get(TICKER_OR_PAIR));
        assertEquals((short) Reason.ADMIN.ordinal(), notification.fields.get(REASON));
        assertEquals("fromServer", notification.fields.get(FROM_SERVER));
        assertEquals("toServer", notification.fields.get(TO_SERVER));
        assertEquals(3L, notification.fields.get(NB_MIGRATED));
    }

    @Test
    void build() {
        var notification = MigratedNotification.of(TEST_CLIENT_TYPE, DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromServer", "toServer", Reason.ADMIN, 1L);
        assertEquals(notification, notification.withStatus(SENDING));
    }

    @Test
    void serializedFields() {
        var notification = MigratedNotification.of(TEST_CLIENT_TYPE, DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromServer", "toServer", Reason.ADMIN, 1L);
        var fields = format(notification.fields.get(ALERT_ID)) + SOH;
        fields += format(notification.fields.get(TYPE)) + SOH;
        fields += format(notification.fields.get(TICKER_OR_PAIR)) + SOH;
        fields += format(notification.fields.get(REASON)) + SOH;
        fields += format(notification.fields.get(FROM_SERVER)) + SOH;
        fields += format(notification.fields.get(TO_SERVER)) + SOH;
        fields += format(notification.fields.get(NB_MIGRATED));
        assertEquals(fields, notification.serializedFields().toString());
    }

    @Test
    void asMessage() {
        var notification = MigratedNotification.of(TEST_CLIENT_TYPE, DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromServer", "toServer", Reason.ADMIN, 1L);
        var message = notification.asMessage();
        var embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alert migration", embed.getTitle());
        assertEquals("An admin on server fromServer made a change, your range alert #321 on tickerOrPair was migrated to server toServer\n\nuse /migrate command to migrate it back", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());

        notification = MigratedNotification.of(TEST_CLIENT_TYPE, DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromServer", null, Reason.SERVER_LEAVED, 1L);
        message = notification.asMessage();
        embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alert migration", embed.getTitle());
        assertEquals("Server fromServer removed this bot, your range alert #321 on tickerOrPair was migrated to your private channel\n\nuse /migrate command to migrate it back", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());

        notification = MigratedNotification.of(TEST_CLIENT_TYPE, DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromServer", "toServer", Reason.LEAVED, 1L);
        message = notification.asMessage();
        embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alert migration", embed.getTitle());
        assertEquals("You leaved server fromServer, your range alert #321 on tickerOrPair was migrated to server toServer\n\nuse /migrate command to migrate it back", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());

        notification = MigratedNotification.of(TEST_CLIENT_TYPE, DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "fromServer", "toServer", Reason.BANNED, 1L);
        message = notification.asMessage();
        embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alert migration", embed.getTitle());
        assertEquals("You were banned from server fromServer, your range alert #321 on tickerOrPair was migrated to server toServer\n\nuse /migrate command to migrate it back", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());

        notification = MigratedNotification.of(TEST_CLIENT_TYPE, DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, null, range, "all", "fromServer", "toServer", Reason.BANNED, 3L);
        message = notification.asMessage();
        embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alerts migration", embed.getTitle());
        assertEquals("You were banned from server fromServer, all your range alerts were migrated to server toServer\n\nuse /migrate command to migrate them back", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());

        notification = MigratedNotification.of(TEST_CLIENT_TYPE, DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, null, range, "eth", "fromServer", "toServer", Reason.BANNED, 3L);
        message = notification.asMessage();
        embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alerts migration", embed.getTitle());
        assertEquals("You were banned from server fromServer, 3 of your range alerts having pair or ticker 'eth' were migrated to server toServer\n\nuse /migrate command to migrate them back", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());
    }
}