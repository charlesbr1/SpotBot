package org.sbot.entities.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.MatchingService.MatchingAlert;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;
import org.sbot.utils.Dates;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import static java.math.BigDecimal.ONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.sbot.entities.alerts.Alert.*;
import static org.sbot.exchanges.Exchanges.SUPPORTED_EXCHANGES;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.*;
import static org.sbot.utils.DatesTest.nowUtc;

public class AlertTest {

    public static final Type TEST_TYPE = Type.range;
    public static final long TEST_USER_ID = 1234L;
    public static final long TEST_SERVER_ID = 4321L;
    public static final Locale TEST_LOCALE = Locale.JAPAN;
    public static final String TEST_EXCHANGE = SUPPORTED_EXCHANGES.get(0);
    public static final String TEST_PAIR = "btc/usd";
    public static final String TEST_MESSAGE = "test message";
    public static final BigDecimal TEST_FROM_PRICE = BigDecimal.valueOf(10L);
    public static final BigDecimal TEST_TO_PRICE = BigDecimal.valueOf(20L);
    public static final ZonedDateTime TEST_FROM_DATE = Dates.parseUTC("01/01/2000-20:00");
    public static final ZonedDateTime TEST_TO_DATE = TEST_FROM_DATE.plusDays(1L);
    public static final ZonedDateTime TEST_LAST_TRIGGER = TEST_FROM_DATE.plusHours(1L);
    public static final BigDecimal TEST_MARGIN = BigDecimal.TEN;


    private static final class TestAlert extends Alert {

        TestAlert(long id, @NotNull Type type, long userId, long serverId, @NotNull Locale locale, @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate, @NotNull String exchange, @NotNull String pair, @NotNull String message, @Nullable BigDecimal fromPrice, @Nullable BigDecimal toPrice, @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate, @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
            super(id, type, userId, serverId, locale, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
        }
        @NotNull
        @Override
        protected TestAlert build(long id, long userId, long serverId, @NotNull Locale locale, @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate, @NotNull String exchange, @NotNull String pair, @NotNull String message, BigDecimal fromPrice, BigDecimal toPrice, ZonedDateTime fromDate, ZonedDateTime toDate, ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
            return new TestAlert(id, type, userId, serverId, locale, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
        }

        @NotNull
        @Override
        protected EmbedBuilder asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick, @NotNull ZonedDateTime now) {
            return new EmbedBuilder().setDescription("TestAlert{" +
                    "id=" + id +
                    ", type=" + type +
                    ", userId=" + userId +
                    ", serverId=" + serverId +
                    ", exchange='" + exchange + '\'' +
                    ", pair='" + pair + '\'' +
                    ", message='" + message + '\'' +
                    ", fromPrice=" + fromPrice +
                    ", toPrice=" + toPrice +
                    ", fromDate=" + fromDate +
                    ", toDate=" + toDate +
                    ", lastTrigger=" + lastTrigger +
                    ", margin=" + margin +
                    ", repeat=" + repeat +
                    ", snooze=" + snooze +
                    ", now=" + now.getYear() +
                    ", status =" + matchingStatus.name() + '}');
        }
    }

    public static Alert createTestAlert() {
        return new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    public static Alert createTestAlertWithCreationDate(ZonedDateTime creationDate) {
        return new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, creationDate, TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    public static Alert createTestAlertWithType(Type type) {
        return new TestAlert(NEW_ALERT_ID, type, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    public static Alert createTestAlertWithUserId(long userId) {
        return new TestAlert(NEW_ALERT_ID, TEST_TYPE, userId, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    public static Alert createTestAlertWithUserIdAndPair(long userId, String pair) {
        return new TestAlert(NEW_ALERT_ID, TEST_TYPE, userId, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, pair, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    public static Alert createTestAlertWithExchangeAndPairAndType(String exchange, String pair, Type type) {
        return new TestAlert(NEW_ALERT_ID, type, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, exchange, pair, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS);
    }

    @Test
    void constructor() {
        Alert alert = createTestAlert();

        assertEquals(NEW_ALERT_ID, alert.id);
        assertEquals(NEW_ALERT_ID, alert.getId());
        assertEquals(TEST_TYPE, alert.type);
        assertEquals(TEST_USER_ID, alert.userId);
        assertEquals(TEST_USER_ID, alert.getUserId());
        assertEquals(TEST_SERVER_ID, alert.serverId);
        assertEquals(TEST_LOCALE, alert.locale);
        assertEquals(TEST_FROM_DATE.minusMinutes(1L), alert.creationDate);
        assertEquals(TEST_FROM_DATE, alert.listeningDate);
        assertEquals(TEST_EXCHANGE, alert.exchange);
        assertEquals(TEST_EXCHANGE, alert.getExchange());
        assertNotEquals(TEST_PAIR, alert.pair);
        assertEquals(TEST_PAIR.toUpperCase(), alert.pair);
        assertEquals(TEST_PAIR.toUpperCase(), alert.getPair());
        assertEquals(TEST_MESSAGE, alert.message);
        assertEquals(TEST_MESSAGE, alert.getMessage());
        assertEquals(TEST_FROM_PRICE, alert.fromPrice);
        assertEquals(TEST_TO_PRICE, alert.toPrice);
        assertEquals(TEST_FROM_DATE, alert.fromDate);
        assertEquals(TEST_TO_DATE, alert.toDate);
        assertEquals(TEST_LAST_TRIGGER, alert.lastTrigger);
        assertEquals(TEST_MARGIN, alert.margin);
        assertEquals(DEFAULT_REPEAT, alert.repeat);
        assertEquals(DEFAULT_SNOOZE_HOURS, alert.snooze);
    }

    @Test
    void constructorCheck() {
        assertDoesNotThrow(() -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        assertDoesNotThrow(() -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), null, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // creation locale not null
        assertThrows(NullPointerException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, null, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // creation date not null
        assertThrows(NullPointerException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, null, TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // type not null
        assertThrows(NullPointerException.class, () -> new TestAlert(NEW_ALERT_ID, null, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // known exchange
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, "bad exchange", TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // well formatted pair
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, "bad pair", TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // message not null
        assertThrows(NullPointerException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, null,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // message not too long
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, "null".repeat(10000),
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // creation date not in the future
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, nowUtc().plusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // last trigger not in the future
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, nowUtc().plusMinutes(1L),
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // margin positive
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                BigDecimal.valueOf(-1L), DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS));
        // repeat positive
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, (short) -1, DEFAULT_SNOOZE_HOURS));
        // snooze positive
        assertThrows(IllegalArgumentException.class, () -> new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, TEST_PAIR, TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, TEST_LAST_TRIGGER,
                TEST_MARGIN, DEFAULT_REPEAT, (short) -1));
    }

    @Test
    void isPrivate() {
        assertTrue(Alert.isPrivate(Alert.PRIVATE_ALERT));
        assertFalse(Alert.isPrivate(1234L));
    }

    @Test
    void hasRepeat() {
        assertTrue(Alert.hasRepeat(10L));
        assertTrue(Alert.hasRepeat(1L));
        assertFalse(Alert.hasRepeat(0L));
        assertFalse(Alert.hasRepeat(-1L));
    }

    @Test
    void hasMargin() {
        assertFalse(Alert.hasMargin(MARGIN_DISABLED));
        assertFalse(Alert.hasMargin(MARGIN_DISABLED.subtract(BigDecimal.valueOf(1L))));
        assertFalse(Alert.hasMargin(MARGIN_DISABLED.subtract(new BigDecimal("0.000001"))));
        assertTrue(Alert.hasMargin(MARGIN_DISABLED.add(BigDecimal.valueOf(1L))));
        assertTrue(Alert.hasMargin(MARGIN_DISABLED.add(new BigDecimal("0.000001"))));
    }

    @Test
    void isQuietOrDisabled() {
        var now = nowUtc();
        Alert alert = createTestAlert();
        assertFalse(alert.isQuietOrDisabled(now));
        assertTrue(alert.withListeningDateRepeat(null, alert.repeat).isQuietOrDisabled(now));

        assertFalse(alert.withListeningDateRepeat(now, alert.repeat).isQuietOrDisabled(now));
        assertFalse(alert.withListeningDateRepeat(now.minusSeconds(1L), alert.repeat).isQuietOrDisabled(now));
        assertFalse(alert.withListeningDateRepeat(now.minusMinutes(1L), alert.repeat).isQuietOrDisabled(now));
        assertFalse(alert.withListeningDateRepeat(now.minusHours(1L), alert.repeat).isQuietOrDisabled(now));
        assertFalse(alert.withListeningDateRepeat(now.minusDays(3L), alert.repeat).isQuietOrDisabled(now));

        assertTrue(alert.withListeningDateRepeat(now.plusSeconds(1L), alert.repeat).isQuietOrDisabled(now));
        assertTrue(alert.withListeningDateRepeat(now.plusMinutes(1L), alert.repeat).isQuietOrDisabled(now));
        assertTrue(alert.withListeningDateRepeat(now.plusMinutes(31L), alert.repeat).isQuietOrDisabled(now));
        assertTrue(alert.withListeningDateRepeat(now.plusDays(2L), alert.repeat).isQuietOrDisabled(now));
    }

    @Test
    void isEnabled() {
        var now = nowUtc();
        Alert alert = createTestAlert();
        assertTrue(alert.isEnabled());
        assertFalse(alert.withListeningDateRepeat(null, alert.repeat).isEnabled());
        assertTrue(alert.withListeningDateRepeat(now.plusSeconds(1L), alert.repeat).isEnabled());
        assertTrue(alert.withListeningDateRepeat(now, alert.repeat).isEnabled());
        assertTrue(alert.withListeningDateRepeat(now.minusMinutes(1L), alert.repeat).isEnabled());
        assertTrue(alert.withListeningDateRepeat(now.plusMinutes(1L), alert.repeat).isEnabled());
    }

    @Test
    void isListenableCandleStick() {
        Alert alert = createTestAlert();
        Candlestick candlestick = new Candlestick(TEST_FROM_DATE, TEST_FROM_DATE.plusHours(1L),
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, ONE);
        assertTrue(alert.isListenableCandleStick(candlestick));
        assertFalse(alert.withListeningDateRepeat(null, alert.repeat).isListenableCandleStick(candlestick));

        candlestick = new Candlestick(TEST_FROM_DATE.minusSeconds(1L), TEST_FROM_DATE.plusHours(1L),
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, ONE);
        assertFalse(alert.isListenableCandleStick(candlestick));

        candlestick = new Candlestick(TEST_FROM_DATE.plusSeconds(1L), TEST_FROM_DATE.plusHours(1L),
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, ONE);
        assertTrue(alert.isListenableCandleStick(candlestick));

        candlestick = new Candlestick(TEST_FROM_DATE.plusMinutes(17L), TEST_FROM_DATE.plusHours(1L),
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, ONE);
        assertTrue(alert.isListenableCandleStick(candlestick));
    }

    @Test
    void filterListenableCandleStick() {
        Alert alert = createTestAlert();
        assertNull(alert.filterListenableCandleStick(null));
        assertEquals(3, Stream.of(new Candlestick(TEST_FROM_DATE, TEST_FROM_DATE.plusHours(1L),
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, ONE),
                null, null,
                new Candlestick(TEST_FROM_DATE.minusSeconds(1L), TEST_FROM_DATE.plusHours(1L),
                        BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, ONE),
                new Candlestick(TEST_FROM_DATE.plusSeconds(1L), TEST_FROM_DATE.plusHours(1L),
                        BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, ONE),
                new Candlestick(TEST_FROM_DATE.plusMinutes(17L), TEST_FROM_DATE.plusHours(1L),
                        BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, ONE))
                .map(alert::filterListenableCandleStick)
                .filter(Objects::nonNull).count());
    }

    @Test
    void getTicker2() {
        Alert alert = createTestAlert();
        assertEquals("USD", alert.getTicker2());
        assertEquals("TEST", new TestAlert(NEW_ALERT_ID, TEST_TYPE, TEST_USER_ID, TEST_SERVER_ID, TEST_LOCALE, TEST_FROM_DATE.minusMinutes(1L), TEST_FROM_DATE, TEST_EXCHANGE, "PAIR/TEST", TEST_MESSAGE,
                TEST_FROM_PRICE, TEST_TO_PRICE, TEST_FROM_DATE, TEST_TO_DATE, null,
                TEST_MARGIN, DEFAULT_REPEAT, DEFAULT_SNOOZE_HOURS).getTicker2());
    }

    @Test
    void withId() {
        Alert alert = createTestAlert().withId(() -> 333L);
        assertEquals(333L, alert.getId());
        assertThrows(IllegalStateException.class, () -> alert.withId(() -> 123L));
    }

    @Test
    void withServerId() {
        Alert alert = createTestAlert();
        assertEquals(333L, alert.withServerId(333L).serverId);
    }

    @Test
    void withFromPrice() {
        Alert alert = createTestAlert();
        BigDecimal fromPrice = BigDecimal.valueOf(1234L);
        assertEquals(fromPrice, alert.withFromPrice(fromPrice).fromPrice);
    }

    @Test
    void withToPrice() {
        Alert alert = createTestAlert();
        BigDecimal toPrice = BigDecimal.valueOf(1234L);
        assertEquals(toPrice, alert.withToPrice(toPrice).toPrice);
    }

    @Test
    void withFromDate() {
        Alert alert = createTestAlert();
        ZonedDateTime fromDate = nowUtc();
        assertEquals(fromDate, alert.withFromDate(fromDate).fromDate);
        assertNull(alert.withFromDate(null).fromDate);
    }

    @Test
    void withToDate() {
        Alert alert = createTestAlert();
        ZonedDateTime toDate = nowUtc();
        assertEquals(toDate, alert.withToDate(toDate).toDate);
        assertNull(alert.withToDate(null).toDate);
    }

    @Test
    void withMessage() {
        Alert alert = createTestAlert();
        assertEquals("new message", alert.withMessage("new message").getMessage());
        assertThrows(IllegalArgumentException.class, () -> alert.withMessage("abcd".repeat(10000)));
    }

    @Test
    void withMargin() {
        Alert alert = createTestAlert();
        assertEquals(BigDecimal.ZERO, alert.withMargin(BigDecimal.ZERO).margin);
        BigDecimal margin = BigDecimal.valueOf(1234L);
        assertEquals(margin, alert.withMargin(margin).margin);
        assertThrows(IllegalArgumentException.class, () -> alert.withMargin(BigDecimal.valueOf(-1L)));
    }

    @Test
    void withLastTriggerMargin() {
        Alert alert = createTestAlert();
        ZonedDateTime now = nowUtc();
        assertEquals(BigDecimal.ZERO, alert.withLastTriggerMargin(now, BigDecimal.ZERO).margin);
        assertEquals(now, alert.withLastTriggerMargin(now, BigDecimal.ZERO).lastTrigger);
        assertNull(alert.withLastTriggerMargin(null, BigDecimal.ZERO).lastTrigger);
        BigDecimal margin = BigDecimal.valueOf(1234L);
        assertEquals(margin, alert.withLastTriggerMargin(now, margin).margin);
        assertThrows(NullPointerException.class, () -> alert.withLastTriggerMargin(now, null));
        assertThrows(IllegalArgumentException.class, () -> alert.withLastTriggerMargin(now, BigDecimal.valueOf(-1L)));
        assertDoesNotThrow(() -> alert.withLastTriggerMargin(now, ONE));
        assertDoesNotThrow(() -> alert.withLastTriggerMargin(now.minusMinutes(1L), ONE));
        assertThrows(IllegalArgumentException.class, () -> alert.withLastTriggerMargin(now.plusSeconds(1L), ONE));
    }

    @Test
    void withListeningDateSnooze() {
        Alert alert = createTestAlert();
        assertNull(alert.withListeningDateSnooze(null, (short) 0).listeningDate);
        assertEquals(0, alert.withListeningDateSnooze(null, (short) 0).snooze);

        ZonedDateTime listeningDate = nowUtc();
        short snooze = 2;
        assertEquals(listeningDate, alert.withListeningDateSnooze(listeningDate, snooze).listeningDate);
        assertEquals(snooze, alert.withListeningDateSnooze(listeningDate, snooze).snooze);
        assertThrows(IllegalArgumentException.class, () -> alert.withListeningDateSnooze(listeningDate, (short) -1));
    }

    @Test
    void withListeningDateRepeat() {
        Alert alert = createTestAlert();
        assertNull(alert.withListeningDateRepeat(null, (short) 0).listeningDate);
        assertEquals(0, alert.withListeningDateRepeat(null, (short) 0).repeat);

        ZonedDateTime listeningDate = nowUtc();
        short repeat = 1;
        assertEquals(listeningDate, alert.withListeningDateRepeat(listeningDate, repeat).listeningDate);
        assertEquals(repeat, alert.withListeningDateRepeat(listeningDate, repeat).repeat);
        assertThrows(IllegalArgumentException.class, () -> alert.withListeningDateRepeat(listeningDate, (short) -1));
    }

    @Test
    void withListeningDateLastTriggerMarginRepeat() {
        Alert alert = createTestAlert();
        ZonedDateTime now = nowUtc();
        assertNull(alert.withListeningDateLastTriggerMarginRepeat(null, now, BigDecimal.ZERO, (short) 0).listeningDate);
        assertNull(alert.withListeningDateLastTriggerMarginRepeat(now, null, BigDecimal.ZERO, (short) 0).lastTrigger);
        assertThrows(NullPointerException.class, () -> alert.withListeningDateLastTriggerMarginRepeat(now, null, null, (short) 0));
        assertEquals(0, alert.withListeningDateLastTriggerMarginRepeat(now, null, BigDecimal.ZERO, (short) 0).repeat);

        ZonedDateTime lastTrigger = now.minusMinutes(1L);
        BigDecimal margin = BigDecimal.valueOf(120L);
        short repeat = 1;
        assertEquals(now.plusHours(3L), alert.withListeningDateLastTriggerMarginRepeat(now.plusHours(3L), lastTrigger, margin, repeat).listeningDate);
        assertEquals(lastTrigger, alert.withListeningDateLastTriggerMarginRepeat(now, lastTrigger, margin, repeat).lastTrigger);
        assertEquals(margin, alert.withListeningDateLastTriggerMarginRepeat(now, lastTrigger, margin, repeat).margin);
        assertEquals(repeat, alert.withListeningDateLastTriggerMarginRepeat(now, lastTrigger, margin, repeat).repeat);
        assertThrows(IllegalArgumentException.class, () -> alert.withListeningDateLastTriggerMarginRepeat(now, nowUtc().plusMinutes(1), margin, repeat));
        assertThrows(IllegalArgumentException.class, () -> alert.withListeningDateLastTriggerMarginRepeat(now, lastTrigger, BigDecimal.valueOf(-1L), repeat));
        assertThrows(IllegalArgumentException.class, () -> alert.withListeningDateLastTriggerMarginRepeat(now, lastTrigger, margin, (short) -1));
    }

    @Test
    void isNewerCandleStick() {
        ZonedDateTime closeTime = nowUtc();
        Candlestick candlestick = new Candlestick(nowUtc().minusMinutes(1L), closeTime,
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, ONE);

        assertThrows(NullPointerException.class, () -> Alert.isNewerCandleStick(null, candlestick));
        assertTrue(Alert.isNewerCandleStick(candlestick, null));
        assertFalse(Alert.isNewerCandleStick(candlestick, candlestick));

        Candlestick olderCandlestick = new Candlestick(candlestick.openTime().minusMinutes(2L), closeTime.minusMinutes(1L),
                candlestick.open(), candlestick.close(), candlestick.high(), candlestick.low());
        assertTrue(Alert.isNewerCandleStick(candlestick, olderCandlestick));

        Candlestick newerCandlestick = new Candlestick(candlestick.openTime(), closeTime.plusMinutes(1L),
                candlestick.open(), candlestick.close(), candlestick.high(), candlestick.low());
        assertFalse(Alert.isNewerCandleStick(candlestick, newerCandlestick));
    }

    @Test
    void onRaiseMessage() {
        var now = nowUtc();
        Alert alert = createTestAlert();
        MatchingAlert matchingAlert = new MatchingAlert(alert, NOT_MATCHING, null);
        assertTrue(alert.onRaiseMessage(matchingAlert, now).getDescriptionBuilder().toString().contains(NOT_MATCHING.name()));
        Candlestick candlestick = new Candlestick(now.minusMinutes(1L), now,
                BigDecimal.TWO, BigDecimal.TWO, BigDecimal.TEN, ONE);
        matchingAlert = new MatchingAlert(alert, MATCHED, candlestick);
        assertTrue(alert.onRaiseMessage(matchingAlert, now).getDescriptionBuilder().toString().contains(MATCHED.name()));
        matchingAlert = new MatchingAlert(alert, MARGIN, candlestick);
        assertTrue(alert.onRaiseMessage(matchingAlert, now).getDescriptionBuilder().toString().contains(MARGIN.name()));
    }

    @Test
    void descriptionMessage() {
        Alert alert = createTestAlert();
        assertTrue(alert.descriptionMessage(nowUtc(), "guildName").getDescriptionBuilder().toString().contains(NOT_MATCHING.name()));
        assertTrue(alert.descriptionMessage(nowUtc(), "guildName").getDescriptionBuilder().toString().contains("guildName"));
    }

    @Test
    void testEquals() {
        Alert alert = createTestAlert();
        Alert otherAlert = alert.withId(() -> 1L);
        assertEquals(alert, alert);
        assertEquals(otherAlert, otherAlert);
        assertNotEquals(alert, otherAlert);
    }

    @Test
    void testHashCode() {
        Alert alert = createTestAlert();
        Alert otherAlert = alert.withId(() -> 1L);
        assertEquals(alert.hashCode(), alert.hashCode());
        assertEquals(otherAlert.hashCode(), otherAlert.hashCode());
        assertNotEquals(alert.hashCode(), otherAlert.hashCode());
    }

    @Test
    void testToString() {
        Alert alert = createTestAlert();
        assertTrue(alert.toString().contains(String.valueOf(alert.id)));
    }
}