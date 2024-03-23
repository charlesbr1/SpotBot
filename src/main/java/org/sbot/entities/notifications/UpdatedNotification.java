package org.sbot.entities.notifications;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.sbot.entities.FieldParser;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.ClientType;
import org.sbot.services.context.Context;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.sbot.commands.CommandAdapter.embedBuilder;
import static org.sbot.entities.FieldParser.Type.LONG;
import static org.sbot.entities.FieldParser.Type.STRING;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;
import static org.sbot.entities.notifications.Notification.NotificationType.UPDATED;
import static org.sbot.entities.notifications.RecipientType.DISCORD_USER;
import static org.sbot.entities.notifications.UpdatedNotification.Field.*;

public final class UpdatedNotification extends Notification {

    public enum Field implements FieldParser {
        ALERT_ID(LONG),
        FIELD(STRING),
        NEW_VALUE(STRING),
        SERVER_NAME(STRING);

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

    private UpdatedNotification(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields) {
        super(id, creationDate, status, UPDATED, recipientType, recipientId, locale, fields);
    }

    public UpdatedNotification(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull String fields) {
        this(id, creationDate, status, recipientType, recipientId, locale, fieldsOf(fields, Field.values(), false));
    }

    public static UpdatedNotification of(@NotNull ClientType clientType, @NotNull ZonedDateTime now, @NotNull Locale locale, long userId, @Nullable Long alertId, @NotNull String field, @NotNull String newValue, @NotNull String serverName) {
        Map<FieldParser, Object> fields = new HashMap<>();
        fields.put(ALERT_ID, requireNonNull(alertId));
        fields.put(FIELD, requireNonNull(field));
        fields.put(NEW_VALUE, requireNonNull(newValue));
        fields.put(SERVER_NAME, requireNonNull(serverName));
        var recipientType = switch (clientType) { case DISCORD -> DISCORD_USER; };
        return new UpdatedNotification(NEW_NOTIFICATION_ID, now, NEW, recipientType, String.valueOf(userId), locale, fields);
    }

    @Override
    @NotNull
    protected UpdatedNotification build(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull NotificationType type, @NotNull RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields) {
        return new UpdatedNotification(id, creationDate, status, recipientType, recipientId, locale, fields);
    }

    @Override
    @NotNull
    public CharSequence serializedFields() {
        return serializedFields(Field.values(), false);
    }

    @Override
    @NotNull
    public Message asMessage(@NotNull Context unused) {
        Long alertId = (Long) fields.get(ALERT_ID);
        return Message.of(embedBuilder("Notice of alert update", NOTIFICATION_COLOR,
                "Your alert #" + alertId + " was updated on server " + fields.get(SERVER_NAME) +
                        ", " + fields.get(FIELD) + " = " + fields.get(NEW_VALUE)));
    }
}
