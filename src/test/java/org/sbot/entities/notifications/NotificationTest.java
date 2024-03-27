package org.sbot.entities.notifications;

import org.junit.jupiter.api.Test;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.chart.DatedPrice;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;
import org.sbot.utils.DatesTest;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static java.math.BigDecimal.ONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.FieldParser.format;
import static org.sbot.entities.UserSettings.DEFAULT_LOCALE;
import static org.sbot.entities.alerts.AlertTest.createTestAlert;
import static org.sbot.entities.notifications.MatchingNotification.Field.*;
import static org.sbot.entities.notifications.Notification.NotificationStatus.*;
import static org.sbot.entities.notifications.RecipientType.DISCORD_SERVER;
import static org.sbot.entities.notifications.RecipientType.DISCORD_USER;
import static org.sbot.entities.notifications.Notification.SOH;

class NotificationTest {

    private static final ZonedDateTime now = DatesTest.nowUtc();

    private static Notification createMatchingNotification() {
        return MatchingNotification.of(now, DEFAULT_LOCALE, MatchingStatus.MATCHED, createTestAlert(), new DatedPrice(ONE, now));
    }
    @Test
    void isNew() {
        assertEquals(NEW, createMatchingNotification().status);
    }

    @Test
    void isBlocked() {
        assertEquals(BLOCKED, createMatchingNotification().withStatus(BLOCKED).status);
    }
    @Test
    void withId() {
        assertThrows(NullPointerException.class, () -> createMatchingNotification().withId(null));
        Notification notification = createMatchingNotification().withId(() -> 333L);
        assertEquals(333L, notification.id);
        assertThrows(IllegalStateException.class, () -> notification.withId(() -> 123L));
    }

    @Test
    void withStatus() {
        assertThrows(NullPointerException.class, () -> createMatchingNotification().withStatus(null));
        assertEquals(NEW, createMatchingNotification().withStatus(NEW).status);
        assertEquals(BLOCKED, createMatchingNotification().withStatus(BLOCKED).status);
        assertEquals(SENDING, createMatchingNotification().withStatus(SENDING).status);
    }

    @Test
    void withStatusRecipient() {
        assertThrows(NullPointerException.class, () -> createMatchingNotification().withStatusRecipient(null, "id", DISCORD_USER));
        assertThrows(NullPointerException.class, () -> createMatchingNotification().withStatusRecipient(SENDING, null, DISCORD_USER));
        assertThrows(NullPointerException.class, () -> createMatchingNotification().withStatusRecipient(SENDING, "id", null));

        assertEquals(SENDING, createMatchingNotification().withStatusRecipient(SENDING, "id", DISCORD_USER).status);
        assertEquals("id", createMatchingNotification().withStatusRecipient(NEW, "id", DISCORD_USER).recipientId);
        assertEquals(DISCORD_USER, createMatchingNotification().withStatusRecipient(NEW, "id", DISCORD_USER).recipientType);
        assertEquals(DISCORD_SERVER, createMatchingNotification().withStatusRecipient(NEW, "id", DISCORD_SERVER).recipientType);
    }

    @Test
    void serializedFields() {
        assertThrows(NullPointerException.class, () -> createMatchingNotification().serializedFields(null, true));

        var notification = createMatchingNotification();
        var fields = format(notification.fields.get(LAST_CLOSE)) + SOH;
        fields += format(notification.fields.get(LAST_CLOSE_TIME)) + SOH;
        fields += format(notification.fields.get(LAST_TRIGGER));
        assertEquals(fields, createMatchingNotification().serializedFields(MatchingNotification.Field.values(), false).toString());

        fields += SOH + format(notification.fields.get(Alert.Field.ID)) + SOH;
        fields += format(notification.fields.get(Alert.Field.TYPE)) + SOH;
        fields += format(notification.fields.get(Alert.Field.CLIENT_TYPE)) + SOH;
        fields += format(notification.fields.get(Alert.Field.USER_ID)) + SOH;
        fields += format(notification.fields.get(Alert.Field.SERVER_ID)) + SOH;
        fields += format(notification.fields.get(Alert.Field.CREATION_DATE)) + SOH;
        fields += format(notification.fields.get(Alert.Field.LISTENING_DATE)) + SOH;
        fields += format(notification.fields.get(Alert.Field.LAST_TRIGGER)) + SOH;
        fields += format(notification.fields.get(Alert.Field.EXCHANGE)) + SOH;
        fields += format(notification.fields.get(Alert.Field.PAIR)) + SOH;
        fields += format(notification.fields.get(Alert.Field.MESSAGE)) + SOH;
        fields += format(notification.fields.get(Alert.Field.FROM_PRICE)) + SOH;
        fields += format(notification.fields.get(Alert.Field.TO_PRICE)) + SOH;
        fields += format(notification.fields.get(Alert.Field.FROM_DATE)) + SOH;
        fields += format(notification.fields.get(Alert.Field.TO_DATE)) + SOH;
        fields += format(notification.fields.get(Alert.Field.MARGIN)) + SOH;
        fields += format(notification.fields.get(Alert.Field.REPEAT)) + SOH;
        fields += format(notification.fields.get(Alert.Field.SNOOZE));

        assertEquals(fields, createMatchingNotification().serializedFields(MatchingNotification.Field.values(), true).toString());
    }

    @Test
    void fieldsOf() {
        assertThrows(NullPointerException.class, () -> MatchingNotification.fieldsOf(null, MatchingNotification.Field.values(), true));
        assertThrows(NullPointerException.class, () -> MatchingNotification.fieldsOf("", null, true));

        var notification = createMatchingNotification();
        var serializedFields = createMatchingNotification().serializedFields(MatchingNotification.Field.values(), true).toString();
        var fields = Notification.fieldsOf(serializedFields, MatchingNotification.Field.values(), true);
        assertEquals(MatchingNotification.Field.values().length + Alert.Field.values().length, fields.size());
        var notificationFields = notification.fields;
        notificationFields.replaceAll((id, value) -> value instanceof ZonedDateTime dt ? dt.truncatedTo(ChronoUnit.MILLIS) : value);
        assertEquals(notificationFields, fields);

        var fields2 = Notification.fieldsOf(serializedFields, MatchingNotification.Field.values(), false);
        assertEquals(MatchingNotification.Field.values().length, fields2.size());
        assertTrue(fields2.entrySet().stream().allMatch(e -> notificationFields.get(e.getKey()).equals(e.getValue())));
    }

    @Test
    void testEquals() {
        Notification notification = createMatchingNotification();
        Notification otherNotification = notification.withId(() -> 1L);
        assertEquals(notification, notification);
        assertEquals(otherNotification, otherNotification);
        assertNotEquals(notification, otherNotification);
    }

    @Test
    void testHashCode() {
        Notification notification = createMatchingNotification();
        Notification otherNotification = notification.withId(() -> 1L);
        assertEquals(notification.hashCode(), notification.hashCode());
        assertEquals(otherNotification.hashCode(), otherNotification.hashCode());
        assertNotEquals(notification.hashCode(), otherNotification.hashCode());
    }

    @Test
    void testToString() {
        Notification notification = createMatchingNotification();
        assertTrue(notification.toString().contains(String.valueOf(notification.id)));
    }
}