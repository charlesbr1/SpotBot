package org.sbot.entities.notifications;

import org.junit.jupiter.api.Test;
import org.sbot.utils.DatesTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.commands.DeleteCommand.DELETE_ALL;
import static org.sbot.entities.FieldParser.format;
import static org.sbot.entities.User.DEFAULT_LOCALE;
import static org.sbot.entities.alerts.Alert.Type.range;
import static org.sbot.entities.notifications.DeletedNotification.Field.*;
import static org.sbot.entities.notifications.Notification.*;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;
import static org.sbot.entities.notifications.Notification.NotificationStatus.SENDING;
import static org.sbot.entities.notifications.Notification.RecipientType.DISCORD_USER;
import static org.sbot.utils.ArgumentValidator.requireOneItem;

class DeletedNotificationTest {

    @Test
    void of() {
        var now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> DeletedNotification.of(null, DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "guildName", 1L, true));
        assertThrows(NullPointerException.class, () -> DeletedNotification.of(now, null, 123L, 321L, range, "tickerOrPair", "guildName", 1L, true));
        assertThrows(NullPointerException.class, () -> DeletedNotification.of(now, DEFAULT_LOCALE, 123L, 321L, range, null, "guildName", 1L, true));

        var notification = DeletedNotification.of(now, DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "guildName", 7L, true);
        assertEquals(NEW_NOTIFICATION_ID, notification.id);
        assertEquals(now, notification.creationDate);
        assertEquals(NEW, notification.status);
        assertEquals("123", notification.recipientId);
        assertEquals(DISCORD_USER, notification.recipientType);
        assertEquals(DEFAULT_LOCALE, notification.locale);

        assertEquals(321L, notification.fields.get(ALERT_ID));
        assertEquals(range, notification.fields.get(TYPE));
        assertEquals("tickerOrPair", notification.fields.get(TICKER_OR_PAIR));
        assertEquals((short) 1, notification.fields.get(EXPIRED));
        assertEquals("guildName", notification.fields.get(GUILD_NAME));
        assertEquals(7L, notification.fields.get(NB_DELETED));
    }

    @Test
    void build() {
        var notification = DeletedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "guildName", 1L, true);
        assertEquals(notification, notification.withStatus(SENDING));
    }

    @Test
    void serializedFields() {
        var notification = DeletedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "guildName", 1L, true);
        var fields = format(notification.fields.get(ALERT_ID)) + SOH;
        fields += format(notification.fields.get(TYPE)) + SOH;
        fields += format(notification.fields.get(TICKER_OR_PAIR)) + SOH;
        fields += format(notification.fields.get(EXPIRED)) + SOH;
        fields += format(notification.fields.get(GUILD_NAME)) + SOH;
        fields += format(notification.fields.get(NB_DELETED));
        assertEquals(fields, notification.serializedFields().toString());
    }

    @Test
    void asMessage() {
        var notification = DeletedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "guildName", 1L, true);
        var message = notification.asMessage(null);
        var embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alert deletion", embed.getTitle());
        assertEquals("Your range alert #321 on tickerOrPair has expired and was deleted on guild guildName", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());

        notification = DeletedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, range, "tickerOrPair", "guildName", 1L, false);
        message = notification.asMessage(null);
        embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alert deletion", embed.getTitle());
        assertEquals("Your range alert #321 on tickerOrPair was deleted on guild guildName", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());

        notification = DeletedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, null, range, "tickerOrPair", "guildName", 1L, false);
        message = notification.asMessage(null);
        embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alert deletion", embed.getTitle());
        assertEquals("Your range alert was deleted on guild guildName", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());

        notification = DeletedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, null, range, "tickerOrPair", "guildName", 2L, false);
        message = notification.asMessage(null);
        embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alerts deletion", embed.getTitle());
        assertEquals("2 of your range alerts having pair or ticker 'tickerOrPair' were deleted on guild guildName", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());

        notification = DeletedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, null, range, DELETE_ALL, "guildName", 2L, false);
        message = notification.asMessage(null);
        embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alerts deletion", embed.getTitle());
        assertEquals("All your range alerts were deleted on guild guildName", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());
    }
}