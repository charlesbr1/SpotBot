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
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.commands.MigrateCommand.MIGRATE_ALL;
import static org.sbot.entities.FieldParser.Type.*;
import static org.sbot.entities.notifications.MigratedNotification.Field.*;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;
import static org.sbot.entities.notifications.Notification.NotificationType.MIGRATED;
import static org.sbot.entities.notifications.RecipientType.DISCORD_USER;

public final class MigratedNotification extends Notification {

    public enum Field implements FieldParser {
        ALERT_ID(LONG),
        TYPE(ALERT_TYPE),
        TICKER_OR_PAIR(STRING),
        REASON(SHORT),
        FROM_SERVER(STRING),
        TO_SERVER(STRING),
        NB_MIGRATED(LONG);

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

    public enum Reason {
        SERVER_LEAVED, LEAVED, BANNED, ADMIN
    }

    private MigratedNotification(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields) {
        super(id, creationDate, status, MIGRATED, recipientType, recipientId, locale, fields);
    }

    public MigratedNotification(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull String fields) {
        this(id, creationDate, status, recipientType, recipientId, locale, fieldsOf(fields, Field.values(), false));
    }

    public static MigratedNotification of(@NotNull ClientType clientType, @NotNull ZonedDateTime now, @NotNull Locale locale, long userId, @Nullable Long alertId, @Nullable Alert.Type type, @NotNull String tickerOrPair, @NotNull String fromServer, @Nullable String toServer, @NotNull Reason reason, long nbMigrated) {
        Map<FieldParser, Object> fields = new HashMap<>();
        if(null != alertId) {
            fields.put(ALERT_ID, alertId);
        }
        if(null != type) {
            fields.put(TYPE, type);
        }
        fields.put(TICKER_OR_PAIR, requireNonNull(tickerOrPair));
        fields.put(REASON, (short) reason.ordinal());
        fields.put(FROM_SERVER, requireNonNull(fromServer));
        if(null != toServer) {
            fields.put(TO_SERVER, toServer);
        }
        if(nbMigrated > 0) {
            fields.put(NB_MIGRATED, nbMigrated);
        }
        var recipientType = switch (clientType) { case DISCORD -> DISCORD_USER; };
        return new MigratedNotification(NEW_NOTIFICATION_ID, now, NEW, recipientType, String.valueOf(userId), locale, fields);
    }

    @Override
    @NotNull
    protected MigratedNotification build(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull NotificationType type, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields) {
        return new MigratedNotification(id, creationDate, status, recipientType, recipientId, locale, fields);
    }

    @Override
    @NotNull
    public CharSequence serializedFields() {
        return serializedFields(Field.values(), false);
    }

    @Override
    @NotNull
    public Message asMessage() {
        String fromServer = (String) fields.get(FROM_SERVER);
        Reason reason = Reason.values()[(short) fields.get(REASON)];
        String header = switch (reason) {
            case SERVER_LEAVED -> "Server " + fromServer + " removed this bot";
            case LEAVED -> "You leaved server " + fromServer;
            case BANNED -> "You were banned from server " + fromServer;
            case ADMIN -> "An admin on server " + fromServer + " made a change";
        };
        Long alertId = (Long) fields.get(ALERT_ID);
        String type = Optional.ofNullable(fields.get(TYPE)).map(Object::toString).map(" "::concat).orElse("");
        String tickerOrPair = (String) fields.get(TICKER_OR_PAIR);
        Long nbMigrated = Optional.ofNullable((Long) fields.get(NB_MIGRATED)).filter(v -> v > 0).orElse(null);
        String description = header;
        String it = "it";
        if(null != alertId) {
            description += ", your" + type + " alert #" + alertId + " on " + tickerOrPair + " was migrated to ";
        } else {
            if(null == nbMigrated || MIGRATE_ALL.equalsIgnoreCase(tickerOrPair)) {
                description += ", all your" + type + " alerts were migrated to ";
            } else {
                description += ", " + (nbMigrated > 1 ? nbMigrated + " of your" + type + " alerts having pair or ticker '" + tickerOrPair + "' were migrated to " :
                        ", your" + type + " alert was migrated to ");
            }
            if(null == nbMigrated || nbMigrated > 1) {
                it = "them";
            }
        }
        String toServer = (String) fields.get(TO_SERVER);
        description += (null != toServer ? "server " + toServer : "your private channel");
        description += "\n\nuse /migrate command to migrate " + it + " back";

        return Message.of(embedBuilder("Notice of " + (null == nbMigrated || nbMigrated > 1 ? "alerts" : "alert") + " migration",
                NOTIFICATION_COLOR, description));
    }
}
