package org.sbot.entities.notifications;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.FieldParser;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.chart.DatedPrice;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;

import java.awt.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static java.util.function.Predicate.not;
import static org.sbot.entities.FieldParser.Type.DECIMAL;
import static org.sbot.entities.FieldParser.Type.ZONED_DATE_TIME;
import static org.sbot.entities.alerts.Alert.Type.remainder;
import static org.sbot.entities.alerts.Alert.isPrivate;
import static org.sbot.entities.notifications.MatchingNotification.Field.*;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;
import static org.sbot.entities.notifications.RecipientType.DISCORD_SERVER;
import static org.sbot.entities.notifications.RecipientType.DISCORD_USER;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.MARGIN;
import static org.sbot.services.MatchingService.MatchingAlert.MatchingStatus.MATCHED;

public final class MatchingNotification extends Notification {

    public enum Field implements FieldParser {
        LAST_CLOSE(DECIMAL),
        LAST_CLOSE_TIME(ZONED_DATE_TIME),
        LAST_TRIGGER(ZONED_DATE_TIME);

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

    public static final Color MATCHED_COLOR = Color.green;
    public static final Color MARGIN_COLOR = Color.orange;

    private MatchingNotification(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull MatchingStatus matchingStatus, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields) {
        super(id, creationDate, status, Optional.of(matchingStatus).filter(not(MatchingStatus::notMatching)).orElseThrow(() -> new IllegalArgumentException("Not in matching status, recipient :" + recipientId + ", fields : " + fields)).isMatched() ?
                NotificationType.MATCHED : NotificationType.MARGIN, recipientType, recipientId, locale, fields);
    }

    public MatchingNotification(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull MatchingStatus matchingStatus, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull String fields) {
        this(id, creationDate, status, matchingStatus, recipientType, recipientId, locale, fieldsOf(fields, Field.values(), true));
    }

    public static MatchingNotification of(@NotNull ZonedDateTime now, @NotNull Locale locale, @NotNull MatchingStatus matchingStatus, @NotNull Alert alert, @Nullable DatedPrice previousClose) {
        Map<FieldParser, Object> fields = new HashMap<>();
        if(null != previousClose) {
            fields.put(LAST_CLOSE, previousClose.price());
            fields.put(LAST_CLOSE_TIME, previousClose.dateTime());
        }
        fields.put(LAST_TRIGGER, requireNonNullElse(alert.lastTrigger, now));
        fields.putAll(alert.fieldsMap());
        var recipientType = switch (alert.clientType) { case DISCORD -> isPrivate(alert.serverId) ? DISCORD_USER : DISCORD_SERVER; };
        var recipientId = DISCORD_USER.equals(recipientType) ? alert.userId : alert.serverId;
        return new MatchingNotification(NEW_NOTIFICATION_ID, now, NEW, matchingStatus, recipientType, String.valueOf(recipientId), locale, fields);
    }

    @Override
    @NotNull
    protected MatchingNotification build(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull NotificationType type, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields) {
        return new MatchingNotification(id, creationDate, status, NotificationType.MATCHED == type ? MATCHED : MARGIN, recipientType, recipientId, locale, fields);
    }

    @Override
    @NotNull
    public CharSequence serializedFields() {
        return serializedFields(Field.values(), true);
    }

    @Override
    @NotNull
    public Message asMessage() {
        Alert alert = Alert.of(fields);
        var matchingType = NotificationType.MATCHED == type ? MATCHED : MARGIN;
        var lastClose = (BigDecimal) fields.get(LAST_CLOSE);
        var lastCloseTime = (ZonedDateTime) fields.get(LAST_CLOSE_TIME);
        var previousClose = null != lastClose && null != lastCloseTime ? new DatedPrice(lastClose, lastCloseTime) : null;
        var embed = alert.asMessage(matchingType, previousClose, (ZonedDateTime) fields.get(LAST_TRIGGER))
                .setTitle(raiseTitle(alert, matchingType))
                .setColor(NotificationType.MATCHED == type ? MATCHED_COLOR : MARGIN_COLOR);
        return Message.of(embed);
    }

    static String raiseTitle(@NotNull Alert alert, @NotNull MatchingStatus matchingStatus) {
        boolean remainderAlert = remainder == alert.type;
        String title = "!!! " + (matchingStatus.isMargin() ? "MARGIN " : "") + alert.type.titleName;
        title += (remainderAlert ?  " !!!" : " ALERT !!!") + " - [" + alert.pair + "]";
        return remainderAlert ? title : title + ' ' + alert.message;
    }
}
