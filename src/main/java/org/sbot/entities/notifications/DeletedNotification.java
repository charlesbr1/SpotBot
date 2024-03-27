package org.sbot.entities.notifications;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.FieldParser;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.entities.alerts.ClientType;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.commands.DeleteCommand.DELETE_ALL;
import static org.sbot.entities.FieldParser.Type.*;
import static org.sbot.entities.notifications.DeletedNotification.Field.*;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;
import static org.sbot.entities.notifications.Notification.NotificationType.DELETED;
import static org.sbot.entities.notifications.RecipientType.DISCORD_USER;
import static org.sbot.utils.ArgumentValidator.requireStrictlyPositive;

public final class DeletedNotification extends Notification {

    public enum Field implements FieldParser {
        ALERT_ID(LONG),
        TYPE(ALERT_TYPE),
        TICKER_OR_PAIR(STRING),
        EXPIRED(SHORT),
        SERVER_NAME(STRING),
        NB_DELETED(LONG);

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

    private DeletedNotification(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields) {
        super(id, creationDate, status, DELETED, recipientType, recipientId, locale, fields);
    }

    public DeletedNotification(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull String fields) {
        this(id, creationDate, status, recipientType, recipientId, locale, fieldsOf(fields, Field.values(), false));
    }

    public static DeletedNotification of(@NotNull ClientType clientType, @NotNull ZonedDateTime now, @NotNull Locale locale, long userId, @Nullable Long alertId, @Nullable Alert.Type type, @NotNull String tickerOrPair, @Nullable String serverName, long nbDeleted, boolean expired) {
        Map<FieldParser, Object> fields = new HashMap<>();
        if(null != alertId) {
            fields.put(ALERT_ID, alertId);
        }
        if(null != type) {
            fields.put(TYPE, type);
        }
        fields.put(TICKER_OR_PAIR, requireNonNull(tickerOrPair));
        if(expired) {
            fields.put(EXPIRED, (short) 1);
        }
        if(null != serverName) {
            fields.put(SERVER_NAME, serverName);
        }
        fields.put(NB_DELETED, requireStrictlyPositive(nbDeleted));
        var recipientType = switch (clientType) { case DISCORD -> DISCORD_USER; };
        return new DeletedNotification(NEW_NOTIFICATION_ID, now, NEW, recipientType, String.valueOf(userId), locale, fields);
    }

    @Override
    @NotNull
    protected DeletedNotification build(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull NotificationType type, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields) {
        return new DeletedNotification(id, creationDate, status, recipientType, recipientId, locale, fields);
    }

    @Override
    @NotNull
    public CharSequence serializedFields() {
        return serializedFields(Field.values(), false);
    }

    @Override
    @NotNull
    public Message asMessage() {
        Long alertId = (Long) fields.get(ALERT_ID);
        String type = Optional.ofNullable(fields.get(TYPE)).map(Object::toString).map(" "::concat).orElse("");
        String tickerOrPair = (String) fields.get(TICKER_OR_PAIR);
        String serverName = Optional.ofNullable((String) fields.get(SERVER_NAME)).filter(not(String::isEmpty))
                .map(" on server "::concat).orElse("");
        if(null != alertId) {
            boolean expired = Short.valueOf((short) 1).equals(fields.get(EXPIRED));
            return Message.of(embedBuilder("Notice of alert deletion", NOTIFICATION_COLOR,
                            "Your" + type + " alert #" + alertId + " on " + tickerOrPair + (expired ? " has expired and" : "" ) + " was deleted" + serverName));
        }
        Long nbDeleted = (Long) fields.get(NB_DELETED);
        String description;
        if(DELETE_ALL.equalsIgnoreCase(tickerOrPair)) {
            description = "All your" + type + " alerts were";
        } else if(nbDeleted > 1) {
            description = nbDeleted + " of your" + type + " alerts having pair or ticker '" + tickerOrPair + "' were";
        } else {
            description = "Your" + type + " alert was";
        }
        return Message.of(embedBuilder("Notice of " + (nbDeleted > 1 ? "alerts" : "alert") + " deletion", NOTIFICATION_COLOR,
                description + " deleted" + serverName));
    }
}
