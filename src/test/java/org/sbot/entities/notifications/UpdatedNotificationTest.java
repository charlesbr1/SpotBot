package org.sbot.entities.notifications;

import org.junit.jupiter.api.Test;
import org.sbot.utils.DatesTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.sbot.entities.FieldParser.format;
import static org.sbot.entities.User.DEFAULT_LOCALE;
import static org.sbot.entities.notifications.Notification.*;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;
import static org.sbot.entities.notifications.Notification.NotificationStatus.SENDING;
import static org.sbot.entities.notifications.Notification.RecipientType.DISCORD_USER;
import static org.sbot.entities.notifications.UpdatedNotification.Field.*;
import static org.sbot.utils.ArgumentValidator.requireOneItem;

class UpdatedNotificationTest {

    @Test
    void of() {
        var now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> UpdatedNotification.of(null, DEFAULT_LOCALE, 123L, 321L, "field", "newValue", "guildName"));
        assertThrows(NullPointerException.class, () -> UpdatedNotification.of(now, null, 123L, 321L, "field", "newValue", "guildName"));
        assertThrows(NullPointerException.class, () -> UpdatedNotification.of(now, DEFAULT_LOCALE, 123L, null, "field", "newValue", "guildName"));
        assertThrows(NullPointerException.class, () -> UpdatedNotification.of(now, DEFAULT_LOCALE, 123L, 321L, null, "newValue", "guildName"));
        assertThrows(NullPointerException.class, () -> UpdatedNotification.of(now, DEFAULT_LOCALE, 123L, 321L, "field", null, "guildName"));
        assertThrows(NullPointerException.class, () -> UpdatedNotification.of(now, DEFAULT_LOCALE, 123L, 321L, "field", "newValue", null));

        var notification = UpdatedNotification.of(now, DEFAULT_LOCALE, 123L, 321L, "field", "newValue", "guildName");
        assertEquals(NEW_NOTIFICATION_ID, notification.id);
        assertEquals(now, notification.creationDate);
        assertEquals(NEW, notification.status);
        assertEquals("123", notification.recipientId);
        assertEquals(DISCORD_USER, notification.recipientType);
        assertEquals(DEFAULT_LOCALE, notification.locale);

        assertEquals(321L, notification.fields.get(ALERT_ID));
        assertEquals("field", notification.fields.get(FIELD));
        assertEquals("newValue", notification.fields.get(NEW_VALUE));
        assertEquals("guildName", notification.fields.get(GUILD_NAME));
    }

    @Test
    void build() {
        var notification = UpdatedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, "field", "newValue", "guildName");
        assertEquals(notification, notification.withStatus(SENDING));
    }

    @Test
    void serializedFields() {
        var notification = UpdatedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, "field", "newValue", "guildName");
        var fields = format(notification.fields.get(ALERT_ID)) + SOH;
        fields += format(notification.fields.get(FIELD)) + SOH;
        fields += format(notification.fields.get(NEW_VALUE)) + SOH;
        fields += format(notification.fields.get(GUILD_NAME));
        assertEquals(fields, notification.serializedFields().toString());
    }

    @Test
    void asMessage() {
        var notification = UpdatedNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, 123L, 321L, "field", "newValue", "guildName");
        var message = notification.asMessage(null);
        var embed = requireOneItem(message.embeds()).build();
        assertEquals("Notice of alert update", embed.getTitle());
        assertEquals("Your alert #321 was updated on guild guildName, field = newValue", embed.getDescription());
        assertEquals(NOTIFICATION_COLOR, embed.getColor());
    }
}