package org.sbot.entities.notifications;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.chart.DatedPrice;
import org.sbot.entities.notifications.Notification.NotificationType;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;
import org.sbot.services.context.Context;
import org.sbot.services.discord.Discord;
import org.sbot.utils.DatesTest;

import java.util.List;
import java.util.Optional;

import static java.math.BigDecimal.ONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.sbot.entities.FieldParser.format;
import static org.sbot.entities.User.DEFAULT_LOCALE;
import static org.sbot.entities.alerts.Alert.PRIVATE_MESSAGES;
import static org.sbot.entities.alerts.Alert.Type.*;
import static org.sbot.entities.alerts.AlertTest.*;
import static org.sbot.entities.notifications.MatchingNotification.Field.*;
import static org.sbot.entities.notifications.MatchingNotification.MARGIN_COLOR;
import static org.sbot.entities.notifications.MatchingNotification.MATCHED_COLOR;
import static org.sbot.entities.notifications.Notification.NEW_NOTIFICATION_ID;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;
import static org.sbot.entities.notifications.Notification.NotificationStatus.SENDING;
import static org.sbot.entities.notifications.Notification.RecipientType.DISCORD_SERVER;
import static org.sbot.entities.notifications.Notification.RecipientType.DISCORD_USER;
import static org.sbot.entities.notifications.Notification.SOH;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.MARGIN;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.MATCHED;
import static org.sbot.utils.ArgumentValidator.requireOneItem;

class MatchingNotificationTest {

    @Test
    void of() {
        var now = DatesTest.nowUtc();
        assertThrows(NullPointerException.class, () -> MatchingNotification.of(null, DEFAULT_LOCALE, MatchingStatus.MARGIN, createTestAlert(), new DatedPrice(ONE, DatesTest.nowUtc())));
        assertThrows(NullPointerException.class, () -> MatchingNotification.of(now, null, MatchingStatus.MARGIN, createTestAlert(), new DatedPrice(ONE, DatesTest.nowUtc())));
        assertThrows(NullPointerException.class, () -> MatchingNotification.of(now, DEFAULT_LOCALE, null, createTestAlert(), new DatedPrice(ONE, DatesTest.nowUtc())));
        assertThrows(NullPointerException.class, () -> MatchingNotification.of(now, DEFAULT_LOCALE, MatchingStatus.MARGIN, null, new DatedPrice(ONE, DatesTest.nowUtc())));

        var alert = createTestAlert();
        var notification = MatchingNotification.of(now, DEFAULT_LOCALE, MatchingStatus.MARGIN, alert, new DatedPrice(ONE, now));
        assertEquals(NEW_NOTIFICATION_ID, notification.id);
        assertEquals(now, notification.creationDate);
        assertEquals(NEW, notification.status);
        assertEquals(String.valueOf(alert.serverId), notification.recipientId);
        assertEquals(DISCORD_SERVER, notification.recipientType);
        assertEquals(DEFAULT_LOCALE, notification.locale);

        assertEquals(ONE, notification.fields.get(LAST_CLOSE));
        assertEquals(now, notification.fields.get(LAST_CLOSE_TIME));
        assertEquals(alert.lastTrigger, notification.fields.get(LAST_TRIGGER));
        assertTrue(alert.fieldsMap().entrySet().stream().allMatch(e -> notification.fields.get(e.getKey()).equals(e.getValue())));

        alert = alert.withServerId(PRIVATE_MESSAGES);
        var notification2 = MatchingNotification.of(now, DEFAULT_LOCALE, MatchingStatus.MARGIN, alert, new DatedPrice(ONE, now));
        assertEquals(NEW_NOTIFICATION_ID, notification2.id);
        assertEquals(now, notification2.creationDate);
        assertEquals(NEW, notification2.status);
        assertEquals(String.valueOf(alert.userId), notification2.recipientId);
        assertEquals(DISCORD_USER, notification2.recipientType);
        assertEquals(DEFAULT_LOCALE, notification2.locale);
        assertTrue(alert.fieldsMap().entrySet().stream().allMatch(e -> notification2.fields.get(e.getKey()).equals(e.getValue())));
    }

    @Test
    void build() {
        var notification = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingStatus.MARGIN, createTestAlert(), new DatedPrice(ONE, DatesTest.nowUtc()));
        assertEquals(notification, notification.withStatus(SENDING));
        assertEquals(NotificationType.MARGIN, notification.type);
        notification = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MATCHED, createTestAlert(), new DatedPrice(ONE, DatesTest.nowUtc()));
        assertEquals(NotificationType.MATCHED, notification.type);
    }

    @Test
    void serializedFields() {
        var notification = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingStatus.MARGIN, createTestAlert(), new DatedPrice(ONE, DatesTest.nowUtc()));
        var fields = format(notification.fields.get(LAST_CLOSE)) + SOH;
        fields += format(notification.fields.get(LAST_CLOSE_TIME)) + SOH;
        fields += format(notification.fields.get(LAST_TRIGGER)) + SOH;
        fields += format(notification.fields.get(Alert.Field.ID)) + SOH;
        fields += format(notification.fields.get(Alert.Field.TYPE)) + SOH;
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
        assertEquals(fields, notification.serializedFields().toString());
    }

    @Test
    void asMessage() {
        var alert = createTestAlert();
        Context context = mock();
        Discord discord = mock();
        when(context.discord()).thenReturn(discord);
        when(discord.guildServer(alert.serverId)).thenReturn(Optional.empty());

        var notification = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MatchingStatus.MARGIN, alert, new DatedPrice(ONE, DatesTest.nowUtc()));
        var message = notification.asMessage(context);
        var embed = requireOneItem(message.embeds()).build();
        assertEquals("!!! MARGIN Range ALERT !!! - [BTC/USD] test message", embed.getTitle());
        assertTrue(embed.getDescription().startsWith("<@1234>\nYour range set on binance BTC/USD reached **margin** threshold"));
        assertEquals(MARGIN_COLOR, embed.getColor());
        verify(context).discord();
        verify(discord).guildServer(alert.serverId);
        assertEquals(List.of(String.valueOf(alert.userId)), message.mentionUsers());
        assertEquals(List.of(), message.mentionRoles());

        notification = MatchingNotification.of(DatesTest.nowUtc(), DEFAULT_LOCALE, MATCHED, alert.withServerId(PRIVATE_MESSAGES), new DatedPrice(ONE, DatesTest.nowUtc()));
        message = notification.asMessage(context);
        embed = requireOneItem(message.embeds()).build();
        assertEquals("!!! Range ALERT !!! - [BTC/USD] test message", embed.getTitle());
        assertTrue(embed.getDescription().startsWith("<@1234>\nYour range set on binance BTC/USD was **tested !**"));
        assertEquals(MATCHED_COLOR, embed.getColor());
        verify(context).discord();
        verify(discord).guildServer(alert.serverId);
        assertNull(message.mentionUsers());
        assertNull(message.mentionRoles());
    }

    @Test
    void raiseTitle() {
        var alert = createTestAlert();
        assertEquals("!!! MARGIN Range ALERT !!! - [BTC/USD] test message", MatchingNotification.raiseTitle(alert, MARGIN));
        assertEquals("!!! Range ALERT !!! - [BTC/USD] test message", MatchingNotification.raiseTitle(alert, MATCHED));
        var longMess = "1234567890".repeat(21);
        alert = alert.withMessage(longMess);
        assertThrows(IllegalArgumentException.class, () -> createTestAlert().withMessage(longMess + "1"));

        assertEquals("!!! MARGIN Range ALERT !!! - [BTC/USD] " + longMess, MatchingNotification.raiseTitle(alert, MARGIN));
        assertEquals("!!! Range ALERT !!! - [BTC/USD] " + longMess, MatchingNotification.raiseTitle(alert, MATCHED));

        assertThrows(IllegalArgumentException.class, () -> createTestAlertWithUserIdAndPairType(TEST_USER_ID, "123456/12346", range));
        alert = createTestAlertWithUserIdAndPairType(TEST_USER_ID, "12345/12346", range).withMessage(longMess);
        assertEquals("!!! MARGIN Range ALERT !!! - [12345/12346] " + longMess, MatchingNotification.raiseTitle(alert, MARGIN));
        assertEquals("!!! Range ALERT !!! - [12345/12346] " + longMess, MatchingNotification.raiseTitle(alert, MATCHED));
        assertTrue(MatchingNotification.raiseTitle(alert, MARGIN).length() <= MessageEmbed.TITLE_MAX_LENGTH);

        alert = createTestAlertWithUserIdAndPairType(TEST_USER_ID, "12345/12346", trend).withMessage(longMess);
        assertEquals("!!! MARGIN Trend ALERT !!! - [12345/12346] " + longMess, MatchingNotification.raiseTitle(alert, MARGIN));
        assertEquals("!!! Trend ALERT !!! - [12345/12346] " + longMess, MatchingNotification.raiseTitle(alert, MATCHED));

        alert = createTestAlertWithUserIdAndPairType(TEST_USER_ID, "12345/12346", remainder).withMessage(longMess);
        assertEquals("!!! MARGIN Remainder !!! - [12345/12346]", MatchingNotification.raiseTitle(alert, MARGIN));
        assertEquals("!!! Remainder !!! - [12345/12346]", MatchingNotification.raiseTitle(alert, MATCHED));
    }
}