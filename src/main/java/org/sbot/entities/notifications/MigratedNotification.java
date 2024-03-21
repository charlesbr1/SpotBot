package org.sbot.entities.notifications;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.FieldParser;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.context.Context;

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

public final class MigratedNotification extends Notification {

    public enum Field implements FieldParser {
        ALERT_ID(LONG),
        TYPE(ALERT_TYPE),
        TICKER_OR_PAIR(STRING),
        REASON(SHORT),
        GUILD_NAME(STRING),
        TO_GUILD(STRING),
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

    public static MigratedNotification of(@NotNull ZonedDateTime now, @NotNull Locale locale, long userId, @Nullable Long alertId, @Nullable Alert.Type type, @NotNull String tickerOrPair, @NotNull String fromGuild, @Nullable String toGuild, @NotNull Reason reason, long nbMigrated) {
        Map<FieldParser, Object> fields = new HashMap<>();
        if(null != alertId) {
            fields.put(ALERT_ID, alertId);
        }
        if(null != type) {
            fields.put(TYPE, type);
        }
        fields.put(TICKER_OR_PAIR, requireNonNull(tickerOrPair));
        fields.put(REASON, reason.ordinal());
        fields.put(GUILD_NAME, requireNonNull(fromGuild));
        if(null != toGuild) {
            fields.put(TO_GUILD, toGuild);
        }
        if(nbMigrated > 0) {
            fields.put(NB_MIGRATED, nbMigrated);
        }
        return new MigratedNotification(NEW_NOTIFICATION_ID, now, NEW, RecipientType.DISCORD_USER, String.valueOf(userId), locale, fields);
    }

    @Override
    @NotNull
    protected MigratedNotification build(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull NotificationType type, @NotNull Notification.RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields) {
        return new MigratedNotification(id, creationDate, status, recipientType, recipientId, locale, fields);
    }

    @Override
    @NotNull
    public CharSequence serializedFields() {
        return serializedFields(Field.values(), false);
    }

    @Override
    @NotNull
    public Message asMessage(@NotNull Context unused) {
        String fromGuild = (String) fields.get(GUILD_NAME);
        Reason reason = Reason.values()[(Integer) fields.get(REASON)];
        String header = switch (reason) {
            case SERVER_LEAVED -> "Guild " + fromGuild + " removed this bot";
            case LEAVED -> "You leaved guild " + fromGuild;
            case BANNED -> "You were banned from guild " + fromGuild;
            case ADMIN -> "An admin on guild " + fromGuild + " made a change";
        };
        Long alertId = (Long) fields.get(ALERT_ID);
        String type = Optional.ofNullable(fields.get(TYPE)).map(Object::toString).map(" "::concat).orElse("");
        String tickerOrPair = (String) fields.get(TICKER_OR_PAIR);
        Long nbMigrated = Optional.ofNullable((Long) fields.get(NB_MIGRATED)).filter(v -> v > 0).orElse(null);
        String description = header;
        if(null != alertId) {
            description += ", your" + type + " alert #" + alertId + " on " + tickerOrPair + " was migrated to ";
        } else {
            if(null == nbMigrated || MIGRATE_ALL.equalsIgnoreCase(tickerOrPair)) {
                description += ", all your" + type + " alerts were migrated to ";
            } else {
                description += ", " + (nbMigrated > 1 ? nbMigrated + " of your" + type + " alerts having pair or ticker '" + tickerOrPair + "' were migrated to " :
                        ", your" + type + " alert was migrated to ");
            }
        }
        String toGuild = (String) fields.get(TO_GUILD);
        description += (null != toGuild ? "guild " + toGuild : "your private channel");

        return Message.of(embedBuilder("Notice of " + (null == nbMigrated || nbMigrated > 1 ? "alerts" : "alert") + " migration",
                NOTIFICATION_COLOR, description));
    }
}
