package org.sbot.entities.alerts;

import net.dv8tion.jda.api.EmbedBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.chart.Candlestick;
import org.sbot.entities.chart.Ticker;
import org.sbot.services.MatchingService.MatchingAlert;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;
import org.sbot.utils.Dates;

import java.awt.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;
import static net.dv8tion.jda.api.entities.MessageEmbed.TITLE_MAX_LENGTH;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.entities.chart.Ticker.getSymbol;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.NOT_MATCHING;
import static org.sbot.services.discord.Discord.SINGLE_LINE_BLOCK_QUOTE_MARKDOWN;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.formatDiscordRelative;

public abstract class Alert {

    private static final Logger LOGGER = LogManager.getLogger(Alert.class);

    public enum Type {
        range("Range"),
        trend("Trend"),
        remainder("Remainder");

        public final String titleName;

        Type(String titleName) {
            this.titleName = requireNonNull(titleName);
        }
    }

    public static final BigDecimal MARGIN_DISABLED = BigDecimal.ZERO;
    public static final short DEFAULT_REPEAT = 10;
    public static final short DEFAULT_SNOOZE_HOURS = 8;
    public static final long PRIVATE_ALERT = 0L;
    public static final long NEW_ALERT_ID = 0L;

    public static final BigDecimal ONE_HOUR_SECONDS = new BigDecimal(Duration.ofHours(1L).toSeconds());


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
        this.fromPrice = fromPrice;
        this.toPrice = toPrice;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.margin = requirePositive(margin);
        this.repeat = requirePositiveShort(repeat);
        this.snooze = requirePositiveShort(snooze);
    }

    @NotNull
    protected abstract Alert build(long id, long userId, long serverId,
                               @NotNull ZonedDateTime creationDate, @Nullable ZonedDateTime listeningDate,
                               @NotNull String exchange, @NotNull String pair, @NotNull String message,
                               BigDecimal fromPrice, BigDecimal toPrice,
                               ZonedDateTime fromDate, ZonedDateTime toDate,
                               @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat, short snooze);

    @NotNull
    protected abstract EmbedBuilder asMessage(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick, @NotNull ZonedDateTime now);

    public static boolean isPrivate(long serverId) {
        return PRIVATE_ALERT == serverId;
    }

    public static boolean hasRepeat(long repeat) {
        return repeat > 0;
    }

    public static boolean hasMargin(@NotNull BigDecimal margin) {
        return MARGIN_DISABLED.compareTo(margin) < 0;
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

    @NotNull
    public final Alert withListeningDateFromDate(@Nullable ZonedDateTime listeningDate, @Nullable ZonedDateTime fromDate) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    public final Alert withSnooze(short snooze) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withListeningDateRepeat(@Nullable ZonedDateTime listeningDate, short repeat) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    @NotNull
    public final Alert withListeningDateLastTriggerMarginRepeat(@Nullable ZonedDateTime listeningDate, @Nullable ZonedDateTime lastTrigger, @NotNull BigDecimal margin, short repeat) {
        return build(id, userId, serverId, creationDate, listeningDate, exchange, pair, message, fromPrice, toPrice, fromDate, toDate, lastTrigger, margin, repeat, snooze);
    }

    public boolean isEnabled() {
        return null != listeningDate;
    }

    boolean isQuietOrDisabled(@NotNull ZonedDateTime now) {
        return null == listeningDate || listeningDate.compareTo(now) > 0;
    }

    protected boolean isListenableCandleStick(@NotNull Candlestick candlestick) {
        return null != listeningDate && listeningDate.compareTo(candlestick.openTime()) <= 0;
    }

    protected static boolean isNewerCandleStick(@NotNull Candlestick candlestick, @Nullable Candlestick previousCandlestick) {
        return null == previousCandlestick ||
                candlestick.closeTime().isAfter(previousCandlestick.closeTime());
    }

    @Nullable
    protected Candlestick filterListenableCandleStick(@Nullable Candlestick previousCandlestick) {
        return null == previousCandlestick || (isListenableCandleStick(previousCandlestick)) ? previousCandlestick : null;
    }

    @NotNull
    public final EmbedBuilder onRaiseMessage(@NotNull MatchingAlert matchingAlert, @NotNull ZonedDateTime now) {
        return asMessage(matchingAlert.status(), matchingAlert.matchingCandlestick(), now)
                .setTitle(raiseTitle(matchingAlert.alert(), matchingAlert.status()))
                .setColor(matchingAlert.status().isMatched() ? Color.green : Color.orange);
    }

    @NotNull
    public final EmbedBuilder descriptionMessage(@NotNull ZonedDateTime now, @Nullable String guildName) {
        return asMessage(NOT_MATCHING, null, now)
                .setColor(!hasRepeat(repeat) ? Color.black : isPrivate(serverId) ? Color.blue : Color.green)
                .appendDescription(null != guildName ? "\n\nguild : " + guildName : "");
    }

    @NotNull
    protected final String header(@NotNull MatchingStatus matchingStatus, @Nullable Candlestick previousCandlestick, @NotNull ZonedDateTime now) {
        String header = matchingStatus.notMatching() ? type.titleName + " Alert set by <@" + userId + '>' :
                "<@" + userId + ">\nYour " + type.name() + " set";
        return header + " on " + exchange + ' ' + pair +
                (matchingStatus.notMatching() ? "" : (matchingStatus.isMargin() ? " reached **margin** threshold. Set a new one using :\n\n" +
                        SINGLE_LINE_BLOCK_QUOTE_MARKDOWN + "*margin " + id + " 'amount in " + getSymbol(getTicker2()) + "'*" :
                        " was **tested !**") +  "\n\n:rocket: Check out the price !!") +
                (matchingStatus.notMatching() && isQuietOrDisabled(now) ? "\n\n** " + withQuietTime(now) + "**" : "") +
                (matchingStatus.notMatching() ? Optional.ofNullable(lastTrigger)
                        .map(Dates::formatDiscordRelative)
                        .map("\n\nRaised "::concat)
                        .orElse("") :
                        Optional.ofNullable(previousCandlestick).map(Candlestick::close)
                                .map(price -> Ticker.formatPrice(price, getTicker2()))
                                .map(price -> "\n\nLast close : " + price + " at " + formatDiscordRelative(previousCandlestick.closeTime()))
                                .orElse(""));    }

    @NotNull
    protected final String footer(@NotNull MatchingStatus matchingStatus) {
        int nextRepeat = matchingStatus.notMatching() ? repeat : Math.max(0, repeat - 1);
        return "\n* margin / repeat / snooze :\t" +
                (matchingStatus.notMatching() && hasMargin(margin) ? margin.toPlainString() + ' ' + getSymbol(getTicker2()) : "disabled") +
                " / " + (!hasRepeat(nextRepeat) ? "disabled" : nextRepeat) +
                " / " + snooze + (snooze > 1 ? " hours" : " hour");
    }

    public static final String DISABLED = "DISABLED";

    private String withQuietTime(@NotNull ZonedDateTime now) {
        if(!isEnabled()) {
            return DISABLED;
        }
        return !isQuietOrDisabled(now) ? "" :
                "QUIET for " + Dates.formatDiscordRelative(listeningDate);
    }

    //TODO test
    private static String raiseTitle(@NotNull Alert alert, @NotNull MatchingStatus matchingStatus) {
        String title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + alert.type.titleName + (alert.type != remainder ? " ALERT !!!" : "") + " - [" + alert.pair + "] ";
        if(TITLE_MAX_LENGTH - alert.message.length() < title.length()) {
            LOGGER.warn("Alert name '{}' will be truncated from the title because it is too long : {}", alert.type.titleName, title);
            title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + "ALERT !!! - [" + alert.pair + "] ";
            if(TITLE_MAX_LENGTH - alert.message.length() < title.length()) {
                LOGGER.warn("Pair '{}' will be truncated from the title because it is too long : {}", alert.pair, title);
                title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + " ALERT !!! - ";
            }
        }
        return title + (alert.type != remainder ? alert.message : "");
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
                ", snooze=" + snooze +
                '}';
    }
}
