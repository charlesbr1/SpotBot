package org.sbot.entities.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.FieldParser;
import org.sbot.entities.chart.Candlestick;
import org.sbot.entities.chart.DatedPrice;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;
import org.sbot.utils.Dates;
import org.sbot.utils.Tickers;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;
import static org.sbot.entities.FieldParser.Type.*;
import static org.sbot.entities.alerts.Alert.Field.*;
import static org.sbot.entities.alerts.Alert.Type.trend;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.NOT_MATCHING;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatDiscordRelative;
import static org.sbot.utils.Tickers.getSymbol;

public abstract class Alert {

    private static final Logger LOGGER = LogManager.getLogger(Alert.class);

    public enum Type {
        range("Range"),
        trend("Trend"),
        remainder("Remainder");

        public final String titleName;

        Type(@NotNull String titleName) {
            this.titleName = requireNonNull(titleName);
        }
    }

    public static final BigDecimal MARGIN_DISABLED = BigDecimal.ZERO;
    public static final short DEFAULT_REPEAT = 10;
    public static final short DEFAULT_SNOOZE_HOURS = 8;
    public static final long PRIVATE_MESSAGES = 0L;
    public static final long NEW_ALERT_ID = 0L;

    public static final Color DISABLED_COLOR = Color.black;
    public static final Color PRIVATE_COLOR = Color.blue;
    public static final Color PUBLIC_COLOR = Color.green;

    public enum Field implements FieldParser {
        ID(LONG), TYPE(ALERT_TYPE), USER_ID(LONG), SERVER_ID(LONG),
        CREATION_DATE(ZONED_DATE_TIME), LISTENING_DATE(ZONED_DATE_TIME), LAST_TRIGGER(ZONED_DATE_TIME),
        EXCHANGE(STRING), PAIR(STRING), MESSAGE(STRING),
        FROM_PRICE(DECIMAL), TO_PRICE(DECIMAL), FROM_DATE(ZONED_DATE_TIME), TO_DATE(ZONED_DATE_TIME),
        MARGIN(DECIMAL), REPEAT(SHORT), SNOOZE(SHORT);

        private final Type type;

        Field(@NotNull Type type) {
            this.type = requireNonNull(type);
        }

        @NotNull
        @Override
        public Object parse(@NotNull String value) {
            return type.parse(value);
        }
    }

    public final long id;

    public final Type type;

    public final long userId;

    public final long serverId; // = PRIVATE_ALERT for private channel
    public final ZonedDateTime creationDate;
    @Nullable // null when disabled
    public final ZonedDateTime listeningDate;

    @Nullable // updated when an alert is raised
    public final ZonedDateTime lastTrigger;
    public final String exchange;
    public final String pair;
    public final String message;

    public final BigDecimal fromPrice;
    public final BigDecimal toPrice;
    public final ZonedDateTime fromDate;
    public final ZonedDateTime toDate;

    public final BigDecimal margin;
    public final short repeat;
    public final short snooze;


    protected Alert(long id, @NotNull Type type, long userId, long serverId,
                    @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                    @NotNull String exchange, @NotNull String pair, @NotNull String message,
                    @Nullable BigDecimal fromPrice, @Nullable BigDecimal toPrice,
                    @Nullable ZonedDateTime fromDate, @Nullable ZonedDateTime toDate,
                    @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze) {
        this.id = id;
        this.type = requireNonNull(type, "missing Alert type");
        this.userId = userId;
        this.serverId = serverId;
        this.creationDate = requireNonNull(creationDate);
        if(null != listeningDate && listeningDate.isBefore(creationDate)) {
            throw new IllegalArgumentException("listeningDate before creationDate");
        }
        if(null != lastTrigger && lastTrigger.isBefore(creationDate)) {
            throw new IllegalArgumentException("lastTrigger before creationDate");
        }
        this.listeningDate = listeningDate;
        this.lastTrigger = lastTrigger;
        this.exchange = requireSupportedExchange(exchange.toLowerCase()).intern();
        this.pair = requirePairFormat(pair.toUpperCase()).intern();
        this.message = requireAlertMessageMaxLength(message);
        this.fromPrice = requirePriceLength(fromPrice);
        this.toPrice = requirePriceLength(toPrice);
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.margin = requirePrice(margin);
        this.repeat = repeat;
        this.snooze = requireSnooze(snooze);
    }

    protected Alert(@NotNull Map<FieldParser, Object> fields) {
        this((long) fields.get(ID), (Type) fields.get(TYPE), (long) fields.get(USER_ID), (long) fields.get(SERVER_ID),
                (ZonedDateTime) fields.get(CREATION_DATE), (ZonedDateTime) fields.get(LISTENING_DATE),
                (String) fields.get(EXCHANGE), (String) fields.get(PAIR), (String) fields.get(MESSAGE),
                (BigDecimal) fields.get(FROM_PRICE), (BigDecimal) fields.get(TO_PRICE),
                (ZonedDateTime) fields.get(FROM_DATE), (ZonedDateTime) fields.get(TO_DATE),
                (ZonedDateTime) fields.get(LAST_TRIGGER), (BigDecimal) fields.get(MARGIN),
                (short) fields.get(REPEAT), (short) fields.get(SNOOZE));
    }

    @NotNull
    protected abstract Alert build(long id, long userId, long serverId,
                               @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                               @NotNull String exchange, @NotNull String pair, @NotNull String message,
                               BigDecimal fromPrice, BigDecimal toPrice,
                               ZonedDateTime fromDate, ZonedDateTime toDate,
                               @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin,
                               short repeat, short snooze);

    public Map<Field, Object> fieldsMap() {
        var fields = new EnumMap<>(Field.class);
        fields.put(ID, id);
        fields.put(TYPE, type);
        fields.put(USER_ID, userId);
        fields.put(SERVER_ID, serverId);
        fields.put(CREATION_DATE, creationDate);
        fields.put(LISTENING_DATE, listeningDate);
        fields.put(LAST_TRIGGER, lastTrigger);
        fields.put(EXCHANGE, exchange);
        fields.put(PAIR, pair);
        fields.put(MESSAGE, message);
        fields.put(FROM_PRICE, fromPrice);
        fields.put(TO_PRICE, toPrice);
        fields.put(FROM_DATE, fromDate);
        fields.put(TO_DATE, toDate);
        fields.put(MARGIN, margin);
        fields.put(REPEAT, repeat);
        fields.put(SNOOZE, snooze);
        return fields;
    }

    public static Alert of(@NotNull Map<FieldParser, Object> fields) {
        return switch ((Type) fields.get(TYPE)) {
            case range -> new RangeAlert(fields);
            case trend -> new TrendAlert(fields);
            case remainder -> new RemainderAlert(fields);
        };
    }

    @NotNull
    public abstract EmbedBuilder asMessage(@NotNull MatchingStatus matchingStatus, @Nullable DatedPrice previousClose, @NotNull ZonedDateTime now);

    public static boolean isPrivate(long serverId) {
        return PRIVATE_MESSAGES == serverId;
    }


    public final long getId() {
        return id;
    }

    public final long getUserId() {
        return userId;
    }

    @NotNull
    public final String getExchange() {
        return exchange;
    }

    @NotNull
    public final String getPair() {
        return pair;
    }

    @NotNull
    public final String getTicker2() {
        return pair.substring(pair.indexOf('/') + 1);
    }

    @NotNull
    public final String getMessage() {
        return message;
    }

    @NotNull
    public final Alert withId(@NotNull LongSupplier idGenerator) {
        if(NEW_ALERT_ID != this.id) {
            throw new IllegalStateException("Can't update the id of an already stored alert");
        }
        return build(idGenerator.getAsLong(), userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withServerId(long serverId) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withFromPrice(@NotNull BigDecimal fromPrice) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withToPrice(@NotNull BigDecimal toPrice) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withFromDate(@Nullable ZonedDateTime fromDate) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withToDate(@Nullable ZonedDateTime toDate) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withMessage(@NotNull String message) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withMargin(@NotNull BigDecimal margin) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withLastTriggerMargin(@Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    public final Alert withSnooze(short snooze) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withRepeat(short repeat) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withListeningDateSnooze(@Nullable ZonedDateTime listeningDate, short snooze) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withListeningDateRepeat(@Nullable ZonedDateTime listeningDate, short repeat) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withListeningDateFromDate(@Nullable ZonedDateTime listeningDate, @Nullable ZonedDateTime fromDate) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withListeningDateLastTriggerMarginRepeat(@Nullable ZonedDateTime listeningDate, @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    public boolean isEnabled() {
        return null != listeningDate;
    }

    public boolean inSnooze(@NotNull ZonedDateTime now) {
        requireNonNull(now);
        return null != listeningDate && listeningDate.isAfter(now) && // alert is in snooze
                (trend == type || // trend alert always active
                        null != fromDate && listeningDate.isAfter(fromDate)); // alert should be active
    }

    protected boolean isListenableCandleStick(@NotNull Candlestick candlestick) {
        return null != listeningDate && (!listeningDate.isAfter(candlestick.openTime()));
    }

    protected static boolean isNewerCandleStick(@NotNull Candlestick candlestick, @Nullable Candlestick previousCandlestick) {
        return null == previousCandlestick ||
                candlestick.closeTime().isAfter(previousCandlestick.closeTime());
    }

    @Nullable
    protected Candlestick filterListenableCandleStick(@Nullable Candlestick previousCandlestick) {
        return null == previousCandlestick || isListenableCandleStick(previousCandlestick) ? previousCandlestick : null;
    }

    @NotNull
    public final EmbedBuilder descriptionMessage(@NotNull ZonedDateTime now, @Nullable String guildName) {
        return asMessage(NOT_MATCHING, null, now)
                .setColor(!isEnabled() ? DISABLED_COLOR : isPrivate(serverId) ? PRIVATE_COLOR : PUBLIC_COLOR)
                .appendDescription(null != guildName ? "\n\nguild : " + guildName : "");
    }

    @NotNull
    protected final String header(@NotNull MatchingStatus matchingStatus, @Nullable DatedPrice previousClose, @NotNull ZonedDateTime now) {
        String header = matchingStatus.notMatching() ? type.titleName + " Alert set by <@" + userId + '>' :
                "<@" + userId + ">\nYour " + type.name() + " set";
        return header + " on " + exchange + ' ' + pair +
                (matchingStatus.notMatching() ? "" : (matchingStatus.isMargin() ? " reached **margin** threshold. Set a new one using :\n\n" +
                        MarkdownUtil.quote("*update margin " + id + " 'amount in " + getSymbol(getTicker2()) + "'*") :
                        " was **tested !**") +  "\n\n:rocket: Check out the price !!") +
                (matchingStatus.notMatching() ? withSnoozeTime(now) : "") +
                (matchingStatus.notMatching() ? Optional.ofNullable(lastTrigger)
                        .map(Dates::formatDiscordRelative)
                        .map("\n\nRaised "::concat)
                        .orElse("") :
                        Optional.ofNullable(previousClose).map(DatedPrice::price)
                                .map(price -> Tickers.formatPrice(price, getTicker2()))
                                .map(price -> "\n\nLast close : " + price + " at " + formatDiscordRelative(previousClose.dateTime()))
                                .orElse(""));    }

    @NotNull
    protected final String footer(@NotNull MatchingStatus matchingStatus) {
        int nextRepeat = matchingStatus.notMatching() ? repeat : repeat - 1;
        return "\n* margin / repeat / snooze :\t" +
                (matchingStatus.notMatching() && hasMargin(margin) ? margin.toPlainString() + ' ' + getSymbol(getTicker2()) : "disabled") +
                " / " + (nextRepeat < 0 ? "disabled" : nextRepeat) +
                " / " + snooze + (snooze > 1 ? " hours" : " hour");
    }

    static boolean hasMargin(@NotNull BigDecimal margin) {
        return MARGIN_DISABLED.compareTo(margin) < 0;
    }

    public static final String DISABLED = "DISABLED";

    protected String withSnoozeTime(@NotNull ZonedDateTime now) {
        if(null == listeningDate) {
            return "\n\n" + MarkdownUtil.bold(DISABLED);
        }
        return listeningDate.isBefore(now.plusMinutes(1L)) ? "" :
                "\n\n" + MarkdownUtil.bold("SNOOZE until " + Dates.formatDiscordRelative(listeningDate));
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Alert alert)) return false;
        return id == alert.id;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Alert{" +
                "id=" + id +
                ", type=" + type +
                ", userId=" + userId +
                ", serverId=" + serverId +
                ", creationDate=" + creationDate +
                ", listeningDate=" + listeningDate +
                ", lastTrigger=" + lastTrigger +
                ", exchange='" + exchange + '\'' +
                ", pair='" + pair + '\'' +
                ", message='" + message + '\'' +
                ", fromPrice=" + fromPrice +
                ", toPrice=" + toPrice +
                ", fromDate=" + fromDate +
                ", toDate=" + toDate +
                ", margin=" + margin +
                ", repeat=" + repeat +
                ", snooze=" + snooze + '}';
    }
}
