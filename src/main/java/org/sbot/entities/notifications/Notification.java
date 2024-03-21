package org.sbot.entities.notifications;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.FieldParser;
import org.sbot.entities.Message;
import org.sbot.entities.alerts.Alert;
import org.sbot.services.context.Context;

import java.awt.Color;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;

import static java.util.Objects.requireNonNull;
import static org.sbot.entities.FieldParser.format;
import static org.sbot.entities.notifications.Notification.NotificationStatus.BLOCKED;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;

public abstract class Notification {

    static final Logger LOGGER = LogManager.getLogger(Notification.class);

    static final char SOH = 0x1;

    public static final long NEW_NOTIFICATION_ID = 0L;

    public static final Color NOTIFICATION_COLOR = Color.lightGray;

    public enum NotificationStatus {
        NEW, SENDING, BLOCKED
    }

    public enum NotificationType {
        MATCHED,
        MARGIN,
        UPDATED,
        DELETED,
        MIGRATED
    }

    public enum RecipientType {
        DISCORD_USER,
        DISCORD_SERVER
    }

    public final long id;
    public final ZonedDateTime creationDate;
    public final NotificationStatus status; // technical field
    public final NotificationType type;
    public final RecipientType recipientType;
    public final String recipientId;
    public final Locale locale;
    public final Map<FieldParser, Object> fields;


    protected Notification(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull NotificationType type, @NotNull Notification.RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields) {
        this.id = id;
        this.creationDate = requireNonNull(creationDate);
        this.status = requireNonNull(status);
        this.type = requireNonNull(type);
        this.recipientType = requireNonNull(recipientType);
        this.recipientId = requireNonNull(recipientId);
        this.locale = requireNonNull(locale);
        this.fields = requireNonNull(fields);
    }

    @NotNull
    protected abstract Notification build(long id, @NotNull ZonedDateTime creationDate, @NotNull NotificationStatus status, @NotNull NotificationType type, @NotNull Notification.RecipientType recipientType, @NotNull String recipientId, @NotNull Locale locale, @NotNull Map<FieldParser, Object> fields);

    @NotNull
    public abstract CharSequence serializedFields();

    @NotNull
    public abstract Message asMessage(@NotNull Context context);

    public final boolean isNew() {
        return NEW == status;
    }

    public final boolean isBlocked() {
        return BLOCKED == status;
    }

    public final Notification withId(@NotNull LongSupplier idGenerator) {
        if(NEW_NOTIFICATION_ID != this.id) {
            throw new IllegalStateException("Can't update the id of an already stored notification");
        }
        return build(idGenerator.getAsLong(), creationDate, status, type, recipientType, recipientId, locale, fields);
    }

    @NotNull
    public final Notification withStatus(@NotNull NotificationStatus status) {
        return build(id, creationDate, status, type, recipientType, recipientId, locale, fields);
    }

    @NotNull
    public final Notification withStatusRecipient(@NotNull NotificationStatus status, @NotNull String recipientId, @NotNull RecipientType recipientType) {
        return build(id, creationDate, status, type, recipientType, recipientId, locale, fields);
    }

    @NotNull
    protected final CharSequence serializedFields(@NotNull FieldParser[] notificationFields, boolean withAlertFields) {
        // fields must be written in enum declaration order
        var sb = new StringBuilder(withAlertFields ? 256 : 64);
        Arrays.asList(notificationFields).forEach(field -> sb.append(format(fields.get(field))).append(SOH));
        if(withAlertFields) {
            Arrays.asList(Alert.Field.values()).forEach(field -> sb.append(format(fields.get(field))).append(SOH));
        }
        sb.setLength(sb.length() - 1); // remove last SOH
        return sb;
    }

    protected static Map<FieldParser, Object> fieldsOf(@NotNull String serializedFields, @NotNull FieldParser[] notificationFields, boolean withAlertFields) {
        // fields must be read in enum declaration order
        Map<FieldParser, Object> map = new HashMap<>();
        BiConsumer<FieldParser, String> appender = (field, value) -> { if(!value.isEmpty()) map.put(field, field.parse(value)); };
        var fields = serializedFields.split(String.valueOf(SOH));
        int[] index = new int[1];
        try {
            Arrays.asList(notificationFields).forEach(field -> appender.accept(field, fields[index[0]++]));
            if(withAlertFields) {
                Arrays.asList(Alert.Field.values()).forEach(field -> appender.accept(field, fields[index[0]++]));
            }
            return map;
        } catch (IndexOutOfBoundsException e) {
            LOGGER.error("Invalid message format for input {} and parameters : {}, withAlertFields {}", serializedFields, notificationFields, withAlertFields);
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notification notification)) return false;
        return id == notification.id;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public final String toString() {
        return "Notification{" +
                "id=" + id +
                ", status=" + status +
                ", type=" + type +
                ", recipientType=" + recipientType +
                ", recipientId='" + recipientId + '\'' +
                ", fields=" + serializedFields() + '}';
    }
}
