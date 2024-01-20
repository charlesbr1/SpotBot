package org.sbot.services.dao.memory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sbot.alerts.Alert;
import org.sbot.services.dao.AlertsDao;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static java.math.BigDecimal.*;
import static java.time.ZonedDateTime.now;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.alerts.Alert.*;
import static org.sbot.alerts.Alert.Type.*;
import static org.sbot.alerts.AlertTest.*;

class AlertsMemoryTest {

    private AlertsDao alerts;

    @BeforeEach
    void init() {
        alerts = new AlertsMemory();
    }

    static void assertDeepEquals(@Nullable Alert alert, @Nullable Alert other) {
        if (other == alert) return;
        assertTrue(null != alert && null != other);
        assertTrue(alert.id == other.id &&
                alert.userId == other.userId &&
                alert.serverId == other.serverId &&
                alert.repeat == other.repeat &&
                alert.snooze == other.snooze &&
                alert.type == other.type &&
                Objects.equals(alert.exchange, other.exchange) &&
                Objects.equals(alert.pair, other.pair) &&
                Objects.equals(alert.message, other.message) &&
                Objects.equals(alert.fromPrice, other.fromPrice) &&
                Objects.equals(alert.toPrice, other.toPrice) &&
                Objects.equals(alert.fromDate, other.fromDate) &&
                Objects.equals(alert.toDate, other.toDate) &&
                Objects.equals(alert.lastTrigger, other.lastTrigger) &&
                Objects.equals(alert.margin, other.margin));
    }

    private static Alert setId(@NotNull Alert alert, long id) {
        return alert.withId(() -> id);
    }

    @Test
    void getAlert() {
        Alert alert = createTestAlert();
        assertEquals(0, alert.id);
        long alertId = alerts.addAlert(alert);
        assertEquals(1, alertId);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertNotEquals(alert, alerts.getAlert(alertId).get());
        assertDeepEquals(setId(alert, 1L), alerts.getAlert(alertId).get());
    }

    @Test
    void getAlertWithoutMessage() {
        Alert alert = createTestAlert().withMessage("message...");
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals(alert.message, alerts.getAlert(alertId).get().message);
        assertTrue(alerts.getAlertWithoutMessage(alertId).isPresent());
        assertNotEquals(alert.message, alerts.getAlertWithoutMessage(alertId).get().message);
        assertTrue(alerts.getAlertWithoutMessage(alertId).get().message.isEmpty());
        assertDeepEquals(setId(alert, 1L).withMessage(""), alerts.getAlertWithoutMessage(alertId).get());
    }

    @Test
    void getUserIdsByServerId() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserId(user1).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alertU2S1 = createTestAlertWithUserId(user2).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserId(user3).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        var userIds = alerts.getUserIdsByServerId(server1);
        assertNotEquals(new HashSet<>(userIds), Set.of(user1));
        assertEquals(new HashSet<>(userIds), Set.of(user1, user2));

        userIds = alerts.getUserIdsByServerId(server2);
        assertNotEquals(new HashSet<>(userIds), Set.of(user1));
        assertEquals(new HashSet<>(userIds), Set.of(user1, user3));
    }

    @Test
    void fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOverWithActiveRange() {
//        alerts.fetchAlertsWithoutMessageByExchangeAndPairHavingRepeatAndDelayOverWithActiveRange()
    }

    @Test
    void fetchAlertsHavingRepeatZeroAndLastTriggerBefore() {
        ZonedDateTime date = ZonedDateTime.now();
        Alert alert1 = createTestAlert().withLastTriggerMarginRepeat(date.minusMinutes(40L), MARGIN_DISABLED, (short) 0);
        long alertId1 = alerts.addAlert(alert1);
        Alert alert2 = createTestAlert().withLastTriggerMarginRepeat(date.minusHours(3L), MARGIN_DISABLED, (short) 0);
        long alertId2 = alerts.addAlert(alert2);
        Alert alert3 = createTestAlert().withLastTriggerMarginRepeat(date.minusHours(10L), MARGIN_DISABLED, (short) 20);
        long alertId3 = alerts.addAlert(alert3);
        Alert alert4 = createTestAlert().withLastTriggerMarginRepeat(date.minusDays(1L).minusMinutes(34L), MARGIN_DISABLED, (short) 0);
        long alertId4 = alerts.addAlert(alert4);
        alerts.addAlert(createTestAlert().withLastTriggerMarginRepeat(null, MARGIN_DISABLED, (short) 7));
        alerts.addAlert(createTestAlert().withLastTriggerMarginRepeat(null, MARGIN_DISABLED, (short) 0));
        alerts.addAlert(createTestAlert().withLastTriggerMarginRepeat(null, MARGIN_DISABLED, (short) 17));
        alerts.addAlert(createTestAlert().withLastTriggerMarginRepeat(null, MARGIN_DISABLED, (short) 0));
        alerts.addAlert(createTestAlert().withLastTriggerMarginRepeat(null, MARGIN_DISABLED, (short) 3));
        alerts.addAlert(createTestAlert().withLastTriggerMarginRepeat(null, MARGIN_DISABLED, (short) 0));

        assertEquals(10, alerts.countAlertsOfUser(TEST_USER_ID));
        assertEquals(0, alerts.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(date.minusDays(2L),
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(date.minusDays(1L).minusMinutes(34L),
                stream -> assertEquals(0, stream.count())));
        assertEquals(1, alerts.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(date.minusDays(1L).minusMinutes(33L),
                stream -> assertTrue(stream.allMatch(a -> alertId4 == a.id))));

        assertEquals(1, alerts.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(date.minusHours(3L),
                stream -> assertTrue(stream.allMatch(a -> alertId4 == a.id))));
        assertEquals(2, alerts.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(date.minusHours(3L).plusMinutes(1L),
                stream -> assertEquals(1, stream.filter(a -> a.id == alertId2).count())));
        assertEquals(2, alerts.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(date.minusHours(3L).plusMinutes(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId4, alertId2).contains(a.id)))));
        assertEquals(2, alerts.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(date.minusMinutes(40L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId4, alertId2).contains(a.id)))));
        assertEquals(3, alerts.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(date.minusMinutes(39L),
                stream -> assertEquals(1, stream.filter(a -> a.id == alertId1).count())));
        assertEquals(3, alerts.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(date.minusMinutes(39L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId4, alertId2, alertId1).contains(a.id)))));
        assertEquals(3, alerts.fetchAlertsHavingRepeatZeroAndLastTriggerBefore(date,
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId4, alertId2, alertId1).contains(a.id)))));
    }

    @Test
    void fetchRangeAlertsHavingToDateBefore() {
        ZonedDateTime date = ZonedDateTime.now();
        Alert alert1 = createTestAlertWithType(range).withToDate(date.plusMinutes(40L));
        long alertId1 = alerts.addAlert(alert1);
        Alert alert2 = createTestAlertWithType(range).withToDate(date.plusHours(3L));
        long alertId2 = alerts.addAlert(alert2);
        Alert alert3 = createTestAlertWithType(range).withToDate(date.plusHours(10L));
        long alertId3 = alerts.addAlert(alert3);
        Alert alert4 = createTestAlertWithType(range).withToDate(date.plusDays(1L).plusHours(2L));
        long alertId4 = alerts.addAlert(alert4);
        alerts.addAlert(createTestAlertWithType(range).withToDate(null));
        alerts.addAlert(createTestAlertWithType(remainder).withToDate(date.plusMinutes(40L)));
        alerts.addAlert(createTestAlertWithType(trend).withToDate(date.plusMinutes(10L)));
        alerts.addAlert(createTestAlertWithType(remainder).withToDate(date.plusMinutes(50L)));
        alerts.addAlert(createTestAlertWithType(trend).withToDate(date.plusHours(3L)));
        alerts.addAlert(createTestAlertWithType(remainder).withToDate(date.plusHours(2L)));
        alerts.addAlert(createTestAlertWithType(trend).withToDate(date.plusHours(4L)));
        alerts.addAlert(createTestAlertWithType(trend).withToDate(date.plusHours(10L)));
        alerts.addAlert(createTestAlertWithType(remainder).withToDate(date.plusHours(12L)));
        alerts.addAlert(createTestAlertWithType(remainder).withToDate(date.plusDays(1L).plusHours(2L)));
        alerts.addAlert(createTestAlertWithType(trend).withToDate(date.plusDays(1L).plusHours(2L).plusMinutes(13L)));

        assertEquals(15, alerts.countAlertsOfUser(TEST_USER_ID));
        assertEquals(0, alerts.fetchRangeAlertsHavingToDateBefore(date,
                stream -> assertEquals(0, stream.count())));
        assertEquals(0, alerts.fetchRangeAlertsHavingToDateBefore(date.plusMinutes(40L),
                stream -> assertEquals(0, stream.count())));

        assertEquals(1, alerts.fetchRangeAlertsHavingToDateBefore(date.plusMinutes(41L),
                stream -> assertTrue(stream.allMatch(a -> alertId1 == a.id))));
        assertEquals(1, alerts.fetchRangeAlertsHavingToDateBefore(date.plusHours(3L),
                stream -> assertTrue(stream.allMatch(a -> alertId1 == a.id))));
        assertEquals(2, alerts.fetchRangeAlertsHavingToDateBefore(date.plusHours(3L).plusMinutes(1L),
                stream -> assertEquals(1, stream.filter(a -> a.id == alertId2).count())));
        assertEquals(2, alerts.fetchRangeAlertsHavingToDateBefore(date.plusHours(3L).plusMinutes(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2).contains(a.id)))));
        assertEquals(2, alerts.fetchRangeAlertsHavingToDateBefore(date.plusHours(10L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2).contains(a.id)))));
        assertEquals(3, alerts.fetchRangeAlertsHavingToDateBefore(date.plusHours(10L).plusMinutes(1L),
                stream -> assertEquals(1, stream.filter(a -> a.id == alertId3).count())));
        assertEquals(3, alerts.fetchRangeAlertsHavingToDateBefore(date.plusHours(10L).plusMinutes(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3).contains(a.id)))));
        assertEquals(3, alerts.fetchRangeAlertsHavingToDateBefore(date.plusDays(1L).plusHours(2L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3).contains(a.id)))));
        assertEquals(4, alerts.fetchRangeAlertsHavingToDateBefore(date.plusDays(1L).plusHours(2L).plusMinutes(1L),
                stream -> assertEquals(1, stream.filter(a -> a.id == alertId4).count())));
        assertEquals(4, alerts.fetchRangeAlertsHavingToDateBefore(date.plusDays(1L).plusHours(2L).plusMinutes(1L),
                stream -> assertTrue(stream.allMatch(a -> Set.of(alertId1, alertId2, alertId3, alertId4).contains(a.id)))));
    }

    @Test
    void getAlertMessages() {
        Alert alert1 = createTestAlert().withMessage("AAAA");
        alert1 = setId(alert1, alerts.addAlert(alert1));
        Alert alert2 = createTestAlert().withMessage("BBBB");
        alert2 = setId(alert2, alerts.addAlert(alert2));
        Alert alert3 = createTestAlert().withMessage("CCCC");
        alert3 = setId(alert3, alerts.addAlert(alert3));
        Alert alert4 = createTestAlert().withMessage("DDDD");
        alert4 = setId(alert4, alerts.addAlert(alert4));

        var messages = alerts.getAlertMessages(new long[] {alert1.id});
        assertEquals(1, messages.size());
        assertEquals(alert1.message, messages.get(alert1.id));

        messages = alerts.getAlertMessages(new long[] {alert2.id});
        assertEquals(1, messages.size());
        assertEquals(alert2.message, messages.get(alert2.id));

        messages = alerts.getAlertMessages(new long[] {alert3.id});
        assertEquals(1, messages.size());
        assertEquals(alert3.message, messages.get(alert3.id));

        messages = alerts.getAlertMessages(new long[] {alert4.id});
        assertEquals(1, messages.size());
        assertEquals(alert4.message, messages.get(alert4.id));

        messages = alerts.getAlertMessages(new long[] {alert1.id, alert4.id});
        assertEquals(2, messages.size());
        assertEquals(alert1.message, messages.get(alert1.id));
        assertEquals(alert4.message, messages.get(alert4.id));


        messages = alerts.getAlertMessages(new long[] {alert1.id, alert2.id, alert4.id});
        assertEquals(3, messages.size());
        assertEquals(alert1.message, messages.get(alert1.id));
        assertEquals(alert2.message, messages.get(alert2.id));
        assertEquals(alert4.message, messages.get(alert4.id));

        messages = alerts.getAlertMessages(new long[] {alert1.id, alert2.id, alert3.id, alert4.id});
        assertEquals(4, messages.size());
        assertEquals(alert1.message, messages.get(alert1.id));
        assertEquals(alert2.message, messages.get(alert2.id));
        assertEquals(alert3.message, messages.get(alert3.id));
        assertEquals(alert4.message, messages.get(alert4.id));
    }

    @Test
    void getPairsByExchangesHavingRepeatAndDelayOverWithActiveRange() {
    }

    @Test
    void countAlertsOfUser() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserId(user1).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserId(user2).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserId(user3).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(3, alerts.countAlertsOfUser(user1));
        assertEquals(1, alerts.countAlertsOfUser(user2));
        assertEquals(1, alerts.countAlertsOfUser(user3));
    }

    @Test
    void countAlertsOfUserAndTickers() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(2, alerts.countAlertsOfUserAndTickers(user1, "ETH/BTC"));
        assertEquals(3, alerts.countAlertsOfUserAndTickers(user1, "ETH"));
        assertEquals(1, alerts.countAlertsOfUserAndTickers(user1, "USD"));
        assertEquals(2, alerts.countAlertsOfUserAndTickers(user1, "BTC"));
        assertEquals(0, alerts.countAlertsOfUserAndTickers(user2, "ETH/BTC"));
        assertEquals(1, alerts.countAlertsOfUserAndTickers(user2, "DOT"));
        assertEquals(1, alerts.countAlertsOfUserAndTickers(user2, "DOT/BTC"));
        assertEquals(0, alerts.countAlertsOfUserAndTickers(user3, "WAG/BNG"));
        assertEquals(0, alerts.countAlertsOfUserAndTickers(user3, "WAG/BTC"));
        assertEquals(1, alerts.countAlertsOfUserAndTickers(user3, "BTC"));
        assertEquals(1, alerts.countAlertsOfUserAndTickers(user3, "ETH/BTC"));
    }

    @Test
    void countAlertsOfServer() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserId(user1).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserId(user2).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserId(user3).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(2, alerts.countAlertsOfServer(server1));
        assertEquals(3, alerts.countAlertsOfServer(server2));
    }

    @Test
    void countAlertsOfServerAndUser() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserId(user1).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserId(user2).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserId(user3).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(1, alerts.countAlertsOfServerAndUser(server1, user1));
        assertEquals(1, alerts.countAlertsOfServerAndUser(server1, user2));
        assertEquals(0, alerts.countAlertsOfServerAndUser(server1, user3));
        assertEquals(2, alerts.countAlertsOfServerAndUser(server2, user1));
        assertEquals(0, alerts.countAlertsOfServerAndUser(server2, user2));
        assertEquals(1, alerts.countAlertsOfServerAndUser(server2, user3));
    }

    @Test
    void countAlertsOfServerAndTickers() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(1, alerts.countAlertsOfServerAndTickers(server1, "ETH/BTC"));
        assertEquals(1, alerts.countAlertsOfServerAndTickers(server1, "ETH"));
        assertEquals(0, alerts.countAlertsOfServerAndTickers(server1, "USD"));
        assertEquals(2, alerts.countAlertsOfServerAndTickers(server1, "BTC"));
        assertEquals(1, alerts.countAlertsOfServerAndTickers(server1, "DOT"));
        assertEquals(1, alerts.countAlertsOfServerAndTickers(server1, "DOT/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndTickers(server1, "WAG/BNG"));
        assertEquals(0, alerts.countAlertsOfServerAndTickers(server1, "WAG/BTC"));

        assertEquals(2, alerts.countAlertsOfServerAndTickers(server2, "ETH/BTC"));
        assertEquals(3, alerts.countAlertsOfServerAndTickers(server2, "ETH"));
        assertEquals(1, alerts.countAlertsOfServerAndTickers(server2, "USD"));
        assertEquals(2, alerts.countAlertsOfServerAndTickers(server2, "BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndTickers(server2, "DOT"));
        assertEquals(0, alerts.countAlertsOfServerAndTickers(server2, "DOT/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndTickers(server2, "WAG/BNG"));
        assertEquals(0, alerts.countAlertsOfServerAndTickers(server2, "WAG/BTC"));
    }

    @Test
    void countAlertsOfServerAndUserAndTickers() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server1, user1, "ETH/BTC"));
        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server1, user1, "ETH"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user1, "USD"));
        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server1, user1, "BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user1, "DOT"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user1, "DOT/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user1, "WAG/BNG"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user1, "WAG/BTC"));

        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server2, user1, "ETH/BTC"));
        assertEquals(2, alerts.countAlertsOfServerAndUserAndTickers(server2, user1, "ETH"));
        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server2, user1, "USD"));
        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server2, user1, "BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user1, "DOT"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user1, "DOT/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user1, "WAG/BNG"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user1, "WAG/BTC"));


        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user2, "ETH/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user2, "ETH"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user2, "USD"));
        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server1, user2, "BTC"));
        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server1, user2, "DOT"));
        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server1, user2, "DOT/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user2, "WAG/BNG"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user2, "WAG/BTC"));

        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user2, "ETH/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user2, "ETH"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user2, "USD"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user2, "BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user2, "DOT"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user2, "DOT/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user2, "WAG/BNG"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user2, "WAG/BTC"));


        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user3, "ETH/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user3, "ETH"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user3, "USD"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user3, "BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user3, "DOT"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user3, "DOT/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user3, "WAG/BNG"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server1, user3, "WAG/BTC"));

        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server2, user3, "ETH/BTC"));
        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server2, user3, "ETH"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user3, "USD"));
        assertEquals(1, alerts.countAlertsOfServerAndUserAndTickers(server2, user3, "BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user3, "DOT"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user3, "DOT/BTC"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user3, "WAG/BNG"));
        assertEquals(0, alerts.countAlertsOfServerAndUserAndTickers(server2, user3, "WAG/BTC"));
    }

    @Test
    void getAlertsOfUser() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserId(user1).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserId(user2).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserId(user3).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(3, alerts.getAlertsOfUser(user1, 0, 1000).size());
        assertEquals(3, alerts.getAlertsOfUser(user1, 0, 1000).stream().filter(alert -> alert.userId == user1).count());
        assertEquals(3, alerts.getAlertsOfUser(user1, 0, 3).size());
        assertEquals(2, alerts.getAlertsOfUser(user1, 0, 2).size());
        assertEquals(1, alerts.getAlertsOfUser(user1, 0, 1).size());
        assertEquals(0, alerts.getAlertsOfUser(user1, 0, 0).size());
        assertEquals(1, alerts.getAlertsOfUser(user1, 2, 3).size());
        assertEquals(0, alerts.getAlertsOfUser(user1, 3, 3).size());
        assertEquals(0, alerts.getAlertsOfUser(user1, 30, 3).size());

        assertEquals(1, alerts.getAlertsOfUser(user2, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOfUser(user2, 0, 1000).stream().filter(alert -> alert.userId == user2).count());
        assertEquals(0, alerts.getAlertsOfUser(user2, 1, 1000).size());
        assertEquals(1, alerts.getAlertsOfUser(user3, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOfUser(user3, 0, 1000).stream().filter(alert -> alert.userId == user3).count());
        assertEquals(0, alerts.getAlertsOfUser(user3, 0, 0).size());
    }

    @Test
    void getAlertsOfUserAndTickers() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(2, alerts.getAlertsOfUserAndTickers(user1, 0, 1000, "ETH/BTC").size());
        assertEquals(2, alerts.getAlertsOfUserAndTickers(user1, 0, 1000, "ETH/BTC").stream()
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .filter(alert -> alert.userId == user1).count());
        assertEquals(2, alerts.getAlertsOfUserAndTickers(user1, 0, 2, "ETH/BTC").size());
        assertEquals(1, alerts.getAlertsOfUserAndTickers(user1, 0, 1, "ETH/BTC").size());
        assertEquals(1, alerts.getAlertsOfUserAndTickers(user1, 1, 2, "ETH/BTC").size());
        assertEquals(0, alerts.getAlertsOfUserAndTickers(user1, 0, 0, "ETH/BTC").size());
        assertEquals(0, alerts.getAlertsOfUserAndTickers(user1, 2, 3, "ETH/BTC").size());
        assertEquals(0, alerts.getAlertsOfUserAndTickers(user1, 20, 3, "ETH/BTC").size());

        assertEquals(3, alerts.getAlertsOfUserAndTickers(user1, 0, 1000, "ETH").size());
        assertEquals(3, alerts.getAlertsOfUserAndTickers(user1, 0, 3, "ETH").stream()
                .filter(alert -> alert.pair.contains("ETH"))
                .filter(alert -> alert.userId == user1).count());

        assertEquals(1, alerts.getAlertsOfUserAndTickers(user1, 0, 1000, "USD").size());
        assertEquals(1, alerts.getAlertsOfUserAndTickers(user1, 0, 1, "USD").stream()
                .filter(alert -> alert.pair.contains("USD"))
                .filter(alert -> alert.userId == user1).count());

        assertEquals(2, alerts.getAlertsOfUserAndTickers(user1, 0, 1000, "BTC").size());
        assertEquals(2, alerts.getAlertsOfUserAndTickers(user1, 0, 2, "BTC").stream()
                .filter(alert -> alert.pair.contains("BTC"))
                .filter(alert -> alert.userId == user1).count());

        assertEquals(0, alerts.getAlertsOfUserAndTickers(user2, 0, 1000, "ETH/BTC").size());
        assertEquals(1, alerts.getAlertsOfUserAndTickers(user2, 0, 1000, "DOT").size());
        assertEquals(1, alerts.getAlertsOfUserAndTickers(user2, 0, 1000, "DOT").stream()
                .filter(alert -> alert.pair.contains("DOT"))
                .filter(alert -> alert.userId == user2).count());
        assertEquals(1, alerts.getAlertsOfUserAndTickers(user2, 0, 1000, "DOT/BTC").size());
        assertEquals(1, alerts.getAlertsOfUserAndTickers(user2, 0, 1000, "DOT/BTC").stream()
                .filter(alert -> alert.pair.contains("DOT/BTC"))
                .filter(alert -> alert.userId == user2).count());
        assertEquals(0, alerts.getAlertsOfUserAndTickers(user3, 0, 1000, "WAG/BNG").size());
        assertEquals(0, alerts.getAlertsOfUserAndTickers(user3, 0, 1000, "WAG/BTC").size());

        assertEquals(1, alerts.getAlertsOfUserAndTickers(user3, 0, 1000, "BTC").size());
        assertEquals(1, alerts.getAlertsOfUserAndTickers(user3, 0, 1000, "BTC").stream()
                .filter(alert -> alert.pair.contains("BTC"))
                .filter(alert -> alert.userId == user3).count());

        assertEquals(1, alerts.getAlertsOfUserAndTickers(user3, 0, 1000, "ETH/BTC").size());
        assertEquals(1, alerts.getAlertsOfUserAndTickers(user3, 0, 1000, "ETH/BTC").stream()
                .filter(alert -> alert.pair.contains("BTC"))
                .filter(alert -> alert.userId == user3).count());
    }

    @Test
    void getAlertsOfServer() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserId(user1).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserId(user2).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserId(user3).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(2, alerts.getAlertsOfServer(server1, 0, 1000).size());
        assertEquals(2, alerts.getAlertsOfServer(server1, 0, 1000).stream()
                .filter(alert -> alert.serverId == server1).count());
        assertEquals(2, alerts.getAlertsOfServer(server1, 0, 2).size());
        assertEquals(1, alerts.getAlertsOfServer(server1, 0, 1).size());
        assertEquals(1, alerts.getAlertsOfServer(server1, 1, 2).size());
        assertEquals(0, alerts.getAlertsOfServer(server1, 0, 0).size());
        assertEquals(0, alerts.getAlertsOfServer(server1, 2, 3).size());
        assertEquals(0, alerts.getAlertsOfServer(server1, 20, 3).size());

        assertEquals(3, alerts.getAlertsOfServer(server2, 0, 1000).size());
        assertEquals(3, alerts.getAlertsOfServer(server2, 0, 1000).stream()
                .filter(alert -> alert.serverId == server2).count());
        assertEquals(3, alerts.getAlertsOfServer(server2, 0, 3).size());
        assertEquals(2, alerts.getAlertsOfServer(server2, 0, 2).size());
        assertEquals(2, alerts.getAlertsOfServer(server2, 1, 2).size());
        assertEquals(1, alerts.getAlertsOfServer(server2, 2, 2).size());
        assertEquals(0, alerts.getAlertsOfServer(server2, 3, 2).size());
    }

    @Test
    void getAlertsOfServerAndUser() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserId(user1).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserId(user2).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserId(user3).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(1, alerts.getAlertsOfServerAndUser(server1, user1, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOfServerAndUser(server1, user1, 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user1)
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUser(server1, user1, 0, 1).size());

        assertEquals(1, alerts.getAlertsOfServerAndUser(server1, user2, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOfServerAndUser(server1, user2, 0, 1000).stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user2)
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUser(server1, user2, 0, 1).size());

        assertEquals(0, alerts.getAlertsOfServerAndUser(server1, user3, 0, 1000).size());

        assertEquals(2, alerts.getAlertsOfServerAndUser(server2, user1, 0, 1000).size());
        assertEquals(2, alerts.getAlertsOfServerAndUser(server2, user1, 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user1)
                .count());
        assertEquals(2, alerts.getAlertsOfServerAndUser(server2, user1, 0, 2).size());
        assertEquals(1, alerts.getAlertsOfServerAndUser(server2, user1, 0, 1).size());
        assertEquals(1, alerts.getAlertsOfServerAndUser(server2, user1, 1, 2).size());
        assertEquals(0, alerts.getAlertsOfServerAndUser(server2, user1, 0, 0).size());
        assertEquals(0, alerts.getAlertsOfServerAndUser(server2, user1, 2, 3).size());
        assertEquals(0, alerts.getAlertsOfServerAndUser(server2, user1, 20, 3).size());

        assertEquals(0, alerts.getAlertsOfServerAndUser(server2, user2, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOfServerAndUser(server2, user3, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOfServerAndUser(server2, user3, 0, 1000).stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user3)
                .count());
    }

    @Test
    void getAlertsOfServerAndTickers() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "ETH/BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "ETH/BTC").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1, "ETH/BTC").size());

        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "ETH").size());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "ETH").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.pair.contains("ETH"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1, "ETH").size());

        assertEquals(0, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "USD").size());

        assertEquals(2, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "BTC").size());
        assertEquals(2, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "BTC").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(2, alerts.getAlertsOfServerAndTickers(server1, 0, 2, "BTC").size());

        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "DOT").size());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "DOT").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.pair.contains("DOT"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1, "DOT").size());

        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "DOT/BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "DOT/BTC").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.pair.contains("DOT/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server1, 0, 1, "DOT/BTC").size());

        assertEquals(0, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "WAG/BNG").size());
        assertEquals(0, alerts.getAlertsOfServerAndTickers(server1, 0, 1000, "WAG/BTC").size());

        assertEquals(2, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "ETH/BTC").size());
        assertEquals(2, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "ETH/BTC").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .count());
        assertEquals(2, alerts.getAlertsOfServerAndTickers(server2, 0, 2, "ETH/BTC").size());

        assertEquals(3, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "ETH").size());
        assertEquals(3, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "ETH").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.pair.contains("ETH"))
                .count());
        assertEquals(3, alerts.getAlertsOfServerAndTickers(server2, 0, 3, "ETH").size());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server2, 2, 3, "ETH").size());
        assertEquals(2, alerts.getAlertsOfServerAndTickers(server2, 1, 2, "ETH").size());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server2, 2, 2, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndTickers(server2, 3, 2, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndTickers(server2, 0, 0, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndTickers(server2, 3, 4, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndTickers(server2, 30, 1, "ETH").size());

        assertEquals(1, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "USD").size());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "USD").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.pair.contains("USD"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndTickers(server2, 0, 1, "USD").size());

        assertEquals(2, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "BTC").size());
        assertEquals(2, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "BTC").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(2, alerts.getAlertsOfServerAndTickers(server2, 0, 2, "BTC").size());

        assertEquals(0, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "DOT").size());
        assertEquals(0, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "DOT/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "WAG/BNG").size());
        assertEquals(0, alerts.getAlertsOfServerAndTickers(server2, 0, 1000, "WAG/BTC").size());
    }

    @Test
    void getAlertsOfServerAndUserAndTickers() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "ETH/BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "ETH/BTC").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1, "ETH/BTC").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "ETH").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "ETH").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("ETH"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1, "ETH").size());

        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "USD").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "BTC").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1, "BTC").size());

        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "DOT").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "DOT/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "WAG/BNG").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "WAG/BTC").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH/BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH/BTC").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1, "ETH/BTC").size());

        assertEquals(2, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH").size());
        assertEquals(2, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("ETH"))
                .count());
        assertEquals(2, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 2, "ETH").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1, "ETH").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 1, 2, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 2, 1, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 3, 1, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 0, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 30, 0, "ETH").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "USD").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "USD").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("USD"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1, "USD").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "BTC").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user1)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1, "BTC").size());

        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "DOT").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "DOT/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "WAG/BNG").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "WAG/BTC").size());


        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "ETH/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "USD").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "BTC").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user2)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1, "BTC").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "DOT").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "DOT").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user2)
                .filter(alert -> alert.pair.contains("DOT"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1, "DOT").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "DOT/BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "DOT/BTC").stream()
                .filter(alert -> alert.serverId == server1)
                .filter(alert -> alert.userId == user2)
                .filter(alert -> alert.pair.contains("DOT/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1, "DOT/BTC").size());

        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "WAG/BNG").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "WAG/BTC").size());


        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user2, 0, 1000, "ETH/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user2, 0, 1000, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user2, 0, 1000, "USD").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user2, 0, 1000, "BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user2, 0, 1000, "DOT").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user2, 0, 1000, "DOT/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user2, 0, 1000, "WAG/BNG").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user2, 0, 1000, "WAG/BTC").size());


        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user3, 0, 1000, "ETH/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user3, 0, 1000, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user3, 0, 1000, "USD").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user3, 0, 1000, "BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user3, 0, 1000, "DOT").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user3, 0, 1000, "DOT/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user3, 0, 1000, "WAG/BNG").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user3, 0, 1000, "WAG/BTC").size());


        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "ETH/BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "ETH/BTC").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user3)
                .filter(alert -> alert.pair.contains("ETH/BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1, "ETH/BTC").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "ETH").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "ETH").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user3)
                .filter(alert -> alert.pair.contains("ETH"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1, "ETH").size());

        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "USD").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "BTC").stream()
                .filter(alert -> alert.serverId == server2)
                .filter(alert -> alert.userId == user3)
                .filter(alert -> alert.pair.contains("BTC"))
                .count());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1, "BTC").size());

        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "DOT").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "DOT/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "WAG/BNG").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "WAG/BTC").size());
    }

    @Test
    void addAlert() {
        Alert alert = createTestAlert();
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertDeepEquals(alert.withId(() -> alertId), alerts.getAlert(alertId).get());
    }

    @Test
    void updateServerId() {
        Alert alert = createTestAlert().withServerId(123L);
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals(123L, alerts.getAlert(alertId).get().serverId);
        alerts.updateServerId(alertId, 321L);
        assertEquals(321L, alerts.getAlert(alertId).get().serverId);
    }

    @Test
    void updateServerIdPrivate() {
        Alert alert = createTestAlert().withServerId(123L);
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals(123L, alerts.getAlert(alertId).get().serverId);
        assertEquals(1, alerts.updateServerIdPrivate(123L));
        assertEquals(PRIVATE_ALERT, alerts.getAlert(alertId).get().serverId);
    }

    @Test
    void updateServerIdOfUserAndServerId() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserId(user1).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserId(user2).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserId(user3).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertTrue(alerts.getAlertsOfServer(server1, 0, 1000).contains(alertU1S1));
        assertTrue(alerts.getAlertsOfServer(server2, 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOfServer(server2, 0, 1000).contains(alert2U1S2));
        assertTrue(alerts.getAlertsOfServer(server1, 0, 1000).contains(alertU2S1));
        assertTrue(alerts.getAlertsOfServer(server2, 0, 1000).contains(alertU3S2));

        long newServerId = 987654321L;
        assertEquals(1, alerts.updateServerIdOfUserAndServerId(user1, server1, newServerId));
        assertFalse(alerts.getAlertsOfServer(server1, 0, 1000).contains(alertU1S1));
        assertTrue(alerts.getAlertsOfServer(newServerId, 0, 1000).contains(alertU1S1));
        assertEquals(0, alerts.updateServerIdOfUserAndServerId(user1, server1, newServerId));
        assertEquals(1, alerts.updateServerIdOfUserAndServerId(user3, server2, newServerId));
        assertFalse(alerts.getAlertsOfServer(server1, 0, 1000).contains(alertU3S2));
        assertTrue(alerts.getAlertsOfServer(newServerId, 0, 1000).contains(alertU3S2));
        assertTrue(alerts.getAlertsOfServer(newServerId, 0, 1000).contains(alertU1S1));
        assertEquals(2, alerts.updateServerIdOfUserAndServerId(user1, server2, newServerId));
        assertFalse(alerts.getAlertsOfServer(server2, 0, 1000).contains(alertU1S2));
        assertFalse(alerts.getAlertsOfServer(server2, 0, 1000).contains(alert2U1S2));
        assertTrue(alerts.getAlertsOfServer(newServerId, 0, 1000).contains(alertU1S2));
        assertTrue(alerts.getAlertsOfServer(newServerId, 0, 1000).contains(alert2U1S2));
        assertEquals(1, alerts.updateServerIdOfUserAndServerId(user2, server1, newServerId));
        assertFalse(alerts.getAlertsOfServer(server1, 0, 1000).contains(alertU2S1));
        assertTrue(alerts.getAlertsOfServer(newServerId, 0, 1000).contains(alertU2S1));
    }

    @Test
    void updateServerIdOfUserAndServerIdAndTickers() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));


        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "ETH/BTC").contains(alertU1S1));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "BTC").contains(alertU1S1));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "BTC").contains(alertU2S1));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "DOT").contains(alertU2S1));

        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH").contains(alertU1S2));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "USD").contains(alertU1S2));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH").contains(alert2U1S2));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "ETH").contains(alertU3S2));

        long newServerId = 987654321L;
        assertEquals(0, alerts.updateServerIdOfUserAndServerIdAndTickers(user1, server1, "WAG/BSD", newServerId));
        assertEquals(1, alerts.updateServerIdOfUserAndServerIdAndTickers(user1, server1, "ETH/BTC", newServerId));
        assertFalse(alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "ETH/BTC").contains(alertU1S1));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(newServerId, user1, 0, 1000, "ETH/BTC").contains(alertU1S1));

        assertEquals(1, alerts.updateServerIdOfUserAndServerIdAndTickers(user2, server1, "DOT", newServerId));
        assertFalse(alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "DOT").contains(alertU2S1));
        assertFalse(alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "BTC").contains(alertU2S1));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(newServerId, user2, 0, 1000, "DOT").contains(alertU2S1));

        assertEquals(2, alerts.updateServerIdOfUserAndServerIdAndTickers(user1, server2, "ETH", newServerId));
        assertFalse(alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH").contains(alertU1S2));
        assertFalse(alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "USD").contains(alertU1S2));
        assertFalse(alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH").contains(alert2U1S2));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(newServerId, user1, 0, 1000, "ETH").contains(alertU1S2));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(newServerId, user1, 0, 1000, "USD").contains(alertU1S2));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(newServerId, user1, 0, 1000, "ETH").contains(alert2U1S2));
        assertEquals(3, alerts.getAlertsOfServerAndUserAndTickers(newServerId, user1, 0, 1000, "ETH").size());

        assertFalse(alerts.getAlertsOfServerAndUserAndTickers(newServerId, user3, 0, 1000, "ETH").contains(alertU3S2));
        assertFalse(alerts.getAlertsOfServerAndUserAndTickers(newServerId, user3, 0, 1000, "BTC").contains(alertU3S2));
        assertEquals(1, alerts.updateServerIdOfUserAndServerIdAndTickers(user3, server2, "BTC", newServerId));
        assertFalse(alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "ETH/BTC").contains(alertU3S2));
        assertFalse(alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "BTC").contains(alertU3S2));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(newServerId, user3, 0, 1000, "ETH").contains(alertU3S2));
        assertTrue(alerts.getAlertsOfServerAndUserAndTickers(newServerId, user3, 0, 1000, "BTC").contains(alertU3S2));
    }

    @Test
    void updateFromPrice() {
        Alert alert = createTestAlert().withFromPrice(ONE);
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals(ONE, alerts.getAlert(alertId).get().fromPrice);
        alerts.updateFromPrice(alertId, TWO);
        assertEquals(TWO, alerts.getAlert(alertId).get().fromPrice);
    }

    @Test
    void updateToPrice() {
        Alert alert = createTestAlert().withToPrice(ONE);
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals(ONE, alerts.getAlert(alertId).get().toPrice);
        alerts.updateToPrice(alertId, TWO);
        assertEquals(TWO, alerts.getAlert(alertId).get().toPrice);
    }

    @Test
    void updateFromDate() {
        ZonedDateTime date = now();
        Alert alert = createTestAlert().withFromDate(date);
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals(date, alerts.getAlert(alertId).get().fromDate);
        alerts.updateFromDate(alertId, date.plusHours(3L));
        assertEquals(date.plusHours(3L), alerts.getAlert(alertId).get().fromDate);
    }

    @Test
    void updateToDate() {
        ZonedDateTime date = now();
        Alert alert = createTestAlert().withToDate(date);
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals(date, alerts.getAlert(alertId).get().toDate);
        alerts.updateToDate(alertId, date.plusHours(3L));
        assertEquals(date.plusHours(3L), alerts.getAlert(alertId).get().toDate);
    }

    @Test
    void updateMessage() {
        Alert alert = createTestAlert().withMessage("message");
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals("message", alerts.getAlert(alertId).get().message);
        alerts.updateMessage(alertId, "new message");
        assertEquals("new message", alerts.getAlert(alertId).get().message);
    }

    @Test
    void updateMargin() {
        Alert alert = createTestAlert().withMargin(ONE);
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals(ONE, alerts.getAlert(alertId).get().margin);
        alerts.updateMargin(alertId, TWO);
        assertEquals(TWO, alerts.getAlert(alertId).get().margin);
    }

    @Test
    void updateRepeatAndLastTrigger() {
        ZonedDateTime date = now().minusMonths(1L);
        Alert alert = createTestAlert().withLastTriggerRepeatSnooze(date, (short) 13, DEFAULT_SNOOZE_HOURS);
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals(date, alerts.getAlert(alertId).get().lastTrigger);
        assertEquals(13, alerts.getAlert(alertId).get().repeat);
        alerts.updateRepeatAndLastTrigger(alertId, (short) 7, date.plusDays(3L));
        assertEquals(date.plusDays(3L), alerts.getAlert(alertId).get().lastTrigger);
        assertEquals(7, alerts.getAlert(alertId).get().repeat);
    }

    @Test
    void updateSnoozeAndLastTrigger() {
        ZonedDateTime date = now().minusMonths(1L);
        Alert alert = createTestAlert().withLastTriggerRepeatSnooze(date, DEFAULT_REPEAT, (short) 51);
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        assertEquals(date, alerts.getAlert(alertId).get().lastTrigger);
        assertEquals(51, alerts.getAlert(alertId).get().snooze);
        alerts.updateSnoozeAndLastTrigger(alertId, (short) 77, date.plusHours(123L));
        assertEquals(date.plusHours(123L), alerts.getAlert(alertId).get().lastTrigger);
        assertEquals(77, alerts.getAlert(alertId).get().snooze);
    }

    @Test
    void deleteAlert() {
        Alert alert = createTestAlert();
        long alertId = alerts.addAlert(alert);
        assertTrue(alerts.getAlert(alertId).isPresent());
        alerts.deleteAlert(alertId);
        assertTrue(alerts.getAlert(alertId).isEmpty());
    }

    @Test
    void deleteAlertsOfUserAndServerId() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserId(user1).withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserId(user1).withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserId(user2).withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserId(user3).withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));

        assertEquals(1, alerts.getAlertsOfServerAndUser(server1, user1, 0, 1000).size());
        assertEquals(2, alerts.getAlertsOfServerAndUser(server2, user1, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOfServerAndUser(server1, user2, 0, 1000).size());
        assertEquals(1, alerts.getAlertsOfServerAndUser(server2, user3, 0, 1000).size());

        assertEquals(1, alerts.deleteAlerts(server1, user1));
        assertEquals(0, alerts.deleteAlerts(server1, user1));
        assertEquals(0, alerts.getAlertsOfServerAndUser(server1, user1, 0, 1000).size());
        assertEquals(2, alerts.deleteAlerts(server2, user1));
        assertEquals(0, alerts.deleteAlerts(server2, user1));
        assertEquals(0, alerts.getAlertsOfServerAndUser(server2, user1, 0, 1000).size());
        assertEquals(1, alerts.deleteAlerts(server1, user2));
        assertEquals(0, alerts.deleteAlerts(server1, user2));
        assertEquals(0, alerts.getAlertsOfServerAndUser(server1, user2, 0, 1000).size());
        assertEquals(1, alerts.deleteAlerts(server2, user3));
        assertEquals(0, alerts.deleteAlerts(server2, user3));
        assertEquals(0, alerts.getAlertsOfServerAndUser(server2, user3, 0, 1000).size());
    }

    @Test
    void deleteAlertsOfUserAndServerIdAndTickers() {
        long user1 = 123L;
        long user2 = 222L;
        long user3 = 321L;
        long server1 = 789L;
        long server2 = 987L;
        Alert alertU1S1 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server1);
        alertU1S1 = setId(alertU1S1, alerts.addAlert(alertU1S1));
        Alert alertU1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/USD").withServerId(server2);
        alertU1S2 = setId(alertU1S2, alerts.addAlert(alertU1S2));
        Alert alert2U1S2 = createTestAlertWithUserIdAndPair(user1, "ETH/BTC").withServerId(server2);
        alert2U1S2 = setId(alert2U1S2, alerts.addAlert(alert2U1S2));
        Alert alertU2S1 = createTestAlertWithUserIdAndPair(user2, "DOT/BTC").withServerId(server1);
        alertU2S1 = setId(alertU2S1, alerts.addAlert(alertU2S1));
        Alert alertU3S2 = createTestAlertWithUserIdAndPair(user3, "ETH/BTC").withServerId(server2);
        alertU3S2 = setId(alertU3S2, alerts.addAlert(alertU3S2));


        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "ETH/BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH/BTC").size());
        assertEquals(2, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "USD").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "BTC").size());
        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "DOT").size());

        assertEquals(1, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "ETH").size());


        assertEquals(1, alerts.deleteAlerts(server1, user1, "ETH/BTC"));
        assertEquals(0, alerts.deleteAlerts(server1, user1, "ETH/BTC"));
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "ETH/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user1, 0, 1000, "BTC").size());

        assertEquals(2, alerts.deleteAlerts(server2, user1, "ETH"));
        assertEquals(0, alerts.deleteAlerts(server2, user1, "ETH"));
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "ETH/BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "USD").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user1, 0, 1000, "BTC").size());

        assertEquals(1, alerts.deleteAlerts(server1, user2, "DOT"));
        assertEquals(0, alerts.deleteAlerts(server1, user2, "DOT"));
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server1, user2, 0, 1000, "DOT").size());

        assertEquals(1, alerts.deleteAlerts(server2, user3, "BTC"));
        assertEquals(0, alerts.deleteAlerts(server2, user3, "BTC"));
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "BTC").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "ETH").size());
        assertEquals(0, alerts.getAlertsOfServerAndUserAndTickers(server2, user3, 0, 1000, "ETH/BTC").size());
    }

    @Test
    void matchedAlertBatchUpdates() {
        ZonedDateTime lastTrigger = ZonedDateTime.now().minusDays(3L);
        Alert alert1 = createTestAlert().withLastTriggerMarginRepeat(lastTrigger, ONE, (short) 18);
        alert1 = setId(alert1, alerts.addAlert(alert1));
        Alert alert2 = createTestAlert().withLastTriggerMarginRepeat(lastTrigger, TEN, (short) 19);
        alert2 = setId(alert2, alerts.addAlert(alert2));
        Alert alert3 = createTestAlert().withLastTriggerMarginRepeat(lastTrigger, TWO, (short) 0);
        alert3 = setId(alert3, alerts.addAlert(alert3));
        Alert alert4 = createTestAlert().withLastTriggerMarginRepeat(lastTrigger, TEN, (short) 21);
        alert4 = setId(alert4, alerts.addAlert(alert4));

        assertTrue(alerts.getAlert(alert1.id).isPresent());
        assertEquals(lastTrigger, alerts.getAlert(alert1.id).get().lastTrigger);
        assertEquals(ONE, alerts.getAlert(alert1.id).get().margin);
        assertEquals(18, alerts.getAlert(alert1.id).get().repeat);
        assertTrue(alerts.getAlert(alert2.id).isPresent());
        assertEquals(lastTrigger, alerts.getAlert(alert2.id).get().lastTrigger);
        assertEquals(TEN, alerts.getAlert(alert2.id).get().margin);
        assertEquals(19, alerts.getAlert(alert2.id).get().repeat);
        assertTrue(alerts.getAlert(alert3.id).isPresent());
        assertEquals(lastTrigger, alerts.getAlert(alert3.id).get().lastTrigger);
        assertEquals(TWO, alerts.getAlert(alert3.id).get().margin);
        assertEquals(0, alerts.getAlert(alert3.id).get().repeat);
        assertTrue(alerts.getAlert(alert4.id).isPresent());
        assertEquals(lastTrigger, alerts.getAlert(alert4.id).get().lastTrigger);
        assertEquals(TEN, alerts.getAlert(alert4.id).get().margin);
        assertEquals(21, alerts.getAlert(alert4.id).get().repeat);

        var alertIds = List.of(alert2.id, alert3.id, alert4.id);
        alerts.matchedAlertBatchUpdates(updater -> alertIds.forEach(updater::batchId));

        assertEquals(lastTrigger, alerts.getAlert(alert1.id).get().lastTrigger);
        assertEquals(ONE, alerts.getAlert(alert1.id).get().margin);
        assertEquals(18, alerts.getAlert(alert1.id).get().repeat);

        assertNotEquals(lastTrigger, alerts.getAlert(alert2.id).get().lastTrigger);
        assertNotNull(alerts.getAlert(alert2.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert2.id).get().margin);
        assertEquals(19-1, alerts.getAlert(alert2.id).get().repeat);

        assertNotEquals(lastTrigger, alerts.getAlert(alert3.id).get().lastTrigger);
        assertNotNull(alerts.getAlert(alert3.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert3.id).get().margin);
        assertEquals(0, alerts.getAlert(alert3.id).get().repeat);

        assertNotEquals(lastTrigger, alerts.getAlert(alert4.id).get().lastTrigger);
        assertNotNull(alerts.getAlert(alert4.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert4.id).get().margin);
        assertEquals(21-1, alerts.getAlert(alert4.id).get().repeat);

        long alertId1 = alert1.id;
        long alertId2 = alert2.id;

        alerts.matchedAlertBatchUpdates(updater -> {
            updater.batchId(alertId1);
            updater.batchId(alertId2);
        });

        assertNotEquals(lastTrigger, alerts.getAlert(alert1.id).get().lastTrigger);
        assertNotNull(alerts.getAlert(alert1.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert1.id).get().margin);
        assertEquals(18-1, alerts.getAlert(alert1.id).get().repeat);

        assertNotEquals(lastTrigger, alerts.getAlert(alert2.id).get().lastTrigger);
        assertNotNull(alerts.getAlert(alert2.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert2.id).get().margin);
        assertEquals(19-2, alerts.getAlert(alert2.id).get().repeat);

        assertNotEquals(lastTrigger, alerts.getAlert(alert3.id).get().lastTrigger);
        assertNotNull(alerts.getAlert(alert3.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert3.id).get().margin);
        assertEquals(0, alerts.getAlert(alert3.id).get().repeat);

        assertNotEquals(lastTrigger, alerts.getAlert(alert4.id).get().lastTrigger);
        assertNotNull(alerts.getAlert(alert4.id).get().lastTrigger);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert4.id).get().margin);
        assertEquals(21-1, alerts.getAlert(alert4.id).get().repeat);
    }

    @Test
    void marginAlertBatchUpdates() {
        Alert alert1 = createTestAlert().withMargin(ONE);
        alert1 = setId(alert1, alerts.addAlert(alert1));
        Alert alert2 = createTestAlert().withMargin(TWO);
        alert2 = setId(alert2, alerts.addAlert(alert2));
        Alert alert3 = createTestAlert().withMargin(TEN);
        alert3 = setId(alert3, alerts.addAlert(alert3));
        Alert alert4 = createTestAlert().withMargin(TEN);
        alert4 = setId(alert4, alerts.addAlert(alert4));

        assertTrue(alerts.getAlert(alert1.id).isPresent());
        assertEquals(ONE, alerts.getAlert(alert1.id).get().margin);
        assertTrue(alerts.getAlert(alert2.id).isPresent());
        assertEquals(TWO, alerts.getAlert(alert2.id).get().margin);
        assertTrue(alerts.getAlert(alert3.id).isPresent());
        assertEquals(TEN, alerts.getAlert(alert3.id).get().margin);
        assertTrue(alerts.getAlert(alert4.id).isPresent());
        assertEquals(TEN, alerts.getAlert(alert4.id).get().margin);

        var alertIds = List.of(alert2.id, alert3.id, alert4.id);
        alerts.marginAlertBatchUpdates(updater -> alertIds.forEach(updater::batchId));

        assertEquals(ONE, alerts.getAlert(alert1.id).get().margin);
        assertNotEquals(TWO, alerts.getAlert(alert2.id).get().margin);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert2.id).get().margin);
        assertNotEquals(TEN, alerts.getAlert(alert3.id).get().margin);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert3.id).get().margin);
        assertNotEquals(TEN, alerts.getAlert(alert4.id).get().margin);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert4.id).get().margin);

        long alertId1 = alert1.id;
        alerts.marginAlertBatchUpdates(updater -> updater.batchId(alertId1));
        assertNotEquals(ONE, alerts.getAlert(alert1.id).get().margin);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert1.id).get().margin);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert2.id).get().margin);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert3.id).get().margin);
        assertEquals(MARGIN_DISABLED, alerts.getAlert(alert4.id).get().margin);
    }

    @Test
    void alertBatchDeletes() {
        long alertId1 = alerts.addAlert(createTestAlert());
        long alertId2 = alerts.addAlert(createTestAlert());
        long alertId3 = alerts.addAlert(createTestAlert());
        long alertId4 = alerts.addAlert(createTestAlert());

        assertTrue(alerts.getAlert(alertId1).isPresent());
        assertTrue(alerts.getAlert(alertId2).isPresent());
        assertTrue(alerts.getAlert(alertId3).isPresent());
        assertTrue(alerts.getAlert(alertId4).isPresent());

        alerts.alertBatchDeletes(deleter -> deleter.batchId(alertId2));
        assertTrue(alerts.getAlert(alertId2).isEmpty());
        assertTrue(alerts.getAlert(alertId1).isPresent());
        assertFalse(alerts.getAlert(alertId2).isPresent());
        assertTrue(alerts.getAlert(alertId3).isPresent());
        assertTrue(alerts.getAlert(alertId4).isPresent());

        alerts.alertBatchDeletes(deleter -> {
            deleter.batchId(alertId1);
            deleter.batchId(alertId3);
            deleter.batchId(alertId4);
        });
        assertTrue(alerts.getAlert(alertId1).isEmpty());
        assertTrue(alerts.getAlert(alertId3).isEmpty());
        assertTrue(alerts.getAlert(alertId4).isEmpty());
        assertFalse(alerts.getAlert(alertId1).isPresent());
        assertFalse(alerts.getAlert(alertId2).isPresent());
        assertFalse(alerts.getAlert(alertId3).isPresent());
        assertFalse(alerts.getAlert(alertId4).isPresent());
    }
}