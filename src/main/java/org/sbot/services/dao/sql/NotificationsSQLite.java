package org.sbot.services.dao.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.notifications.*;
import org.sbot.entities.notifications.Notification.NotificationStatus;
import org.sbot.entities.notifications.Notification.NotificationType;
import org.sbot.services.MatchingService.MatchingAlert.MatchingStatus;
import org.sbot.services.dao.BatchEntry;
import org.sbot.services.dao.NotificationsDao;
import org.sbot.services.dao.sql.jdbi.AbstractJDBI;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static java.util.Collections.emptyMap;
import static org.sbot.entities.notifications.Notification.NEW_NOTIFICATION_ID;
import static org.sbot.entities.notifications.Notification.NotificationStatus.BLOCKED;
import static org.sbot.entities.notifications.Notification.NotificationStatus.NEW;
import static org.sbot.services.dao.sql.NotificationsSQLite.SQL.EXPIRATION_DATE_ARGUMENT;
import static org.sbot.services.dao.sql.NotificationsSQLite.SQL.Fields.*;
import static org.sbot.services.dao.sql.NotificationsSQLite.SQL.LIMIT_ARGUMENT;
import static org.sbot.utils.ArgumentValidator.requireStrictlyPositive;
import static org.sbot.utils.Dates.parseUtcDateTime;

public class NotificationsSQLite extends AbstractJDBI implements NotificationsDao {

    private static final Logger LOGGER = LogManager.getLogger(NotificationsSQLite.class);

    interface SQL {

        interface Fields {
            String ID = "id";
            String CREATION_DATE = "creation_date";
            String STATUS = "status";
            String TYPE = "type";
            String RECIPIENT_TYPE = "recipient_type";
            String RECIPIENT_ID = "recipient_id";
            String LOCALE = "locale";
            String FIELDS = "fields";
        }

        String EXPIRATION_DATE_ARGUMENT = "expirationDate";
        String LIMIT_ARGUMENT = "limit";

        String CREATE_TABLE = """
                CREATE TABLE IF NOT EXISTS notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                creation_date INTEGER NOT NULL,
                status TEXT NULL,
                type TEXT NOT NULL,
                recipient_type TEXT NOT NULL,
                recipient_id TEXT NOT NULL,
                locale TEXT NOT NULL,
                fields TEXT NOT NULL) STRICT
                """;

        String CREATE_STATUS_INDEX = "CREATE INDEX IF NOT EXISTS notifications_status_index ON notifications (status)";
        String CREATE_CREATION_DATE_INDEX = "CREATE INDEX IF NOT EXISTS notifications_creation_date_index ON notifications (creation_date)";
        String CREATE_RECIPIENT_INDEX = "CREATE INDEX IF NOT EXISTS notifications_recipient_index ON notifications (recipient_id, recipient_type)";

        String INSERT_NOTIFICATION = "INSERT INTO notifications (creation_date,type,status,recipient_type,recipient_id,locale,fields) VALUES (:creation_date,:type,:status,:recipient_type,:recipient_id,:locale,:fields)";
        String SELECT_HAVING_STATUS_NEW = "SELECT * FROM notifications WHERE status LIKE '" + NEW + "' LIMIT :limit";
        String UPDATE_STATUS_NEW_WHERE_BLOCKED_AND_RECIPIENT = "UPDATE notifications SET status='" + NEW + "' WHERE status='" + BLOCKED + "' AND recipient_id=:recipient_id AND recipient_type=:recipient_type";
        String UPDATE_STATUS_RECIPIENT = "UPDATE notifications SET status=:status,recipient_type=:recipient_type,recipient_id=:recipient_id WHERE id=:id";
        String UPDATE_STATUS_BY_ID = "UPDATE notifications SET status=:status WHERE id=:id";
        String DELETE_BY_ID = "DELETE FROM notifications WHERE id=:id";
        String DELETE_HAVING_CREATION_DATE_BEFORE = "DELETE FROM notifications WHERE creation_date<:expirationDate";
    }

    public static final class NotificationMapper implements RowMapper<Notification> {
        @Override
        public Notification map(ResultSet rs, StatementContext ctx) throws SQLException {
            long notificationId = rs.getLong(ID);
            var creationDate = parseUtcDateTime(rs.getTimestamp(CREATION_DATE))
                    .orElseThrow(() -> new IllegalArgumentException("Missing field notification creation_date"));
            var status = NotificationStatus.valueOf(rs.getString(STATUS));
            var type = NotificationType.valueOf(rs.getString(TYPE));
            var recipientType = RecipientType.SHORTNAMES.get(rs.getString(RECIPIENT_TYPE));
            var recipientId = rs.getString(RECIPIENT_ID);
            var locale = Optional.ofNullable(rs.getString(LOCALE)).map(Locale::forLanguageTag)
                    .orElseThrow(() -> new IllegalArgumentException("Missing field notification locale"));
            var fields = rs.getString(FIELDS);
            return switch (type) {
                case MARGIN -> new MatchingNotification(notificationId, creationDate, status, MatchingStatus.MARGIN, recipientType, recipientId, locale, fields);
                case MATCHED -> new MatchingNotification(notificationId, creationDate, status, MatchingStatus.MATCHED, recipientType, recipientId, locale, fields);
                case UPDATED -> new UpdatedNotification(notificationId, creationDate, status, recipientType, recipientId, locale, fields);
                case DELETED -> new DeletedNotification(notificationId, creationDate, status, recipientType, recipientId, locale, fields);
                case MIGRATED -> new MigratedNotification(notificationId, creationDate, status, recipientType, recipientId, locale, fields);
            };
        }
    }

    private static void bindNotificationFields(@NotNull Notification notification, @NotNull SqlStatement<?> query) {
        query.bind(CREATION_DATE, notification.creationDate);
        query.bind(TYPE, notification.type);
        query.bind(STATUS, notification.status);
        query.bind(RECIPIENT_TYPE, notification.recipientType.shortName);
        query.bind(RECIPIENT_ID, notification.recipientId);
        query.bind(LOCALE, notification.locale);
        query.bind(FIELDS, notification.serializedFields());
    }

    public NotificationsSQLite(@NotNull JDBIRepository repository) {
        super(repository, new NotificationMapper());
        LOGGER.debug("Loading SQLite storage for notifications");
    }

    NotificationsSQLite(@NotNull AbstractJDBI abstractJDBI, @NotNull JDBITransactionHandler transactionHandler) {
        super(abstractJDBI, transactionHandler);
    }

    @Override
    public NotificationsSQLite withHandler(@NotNull JDBITransactionHandler transactionHandler) {
        return new NotificationsSQLite(this, transactionHandler);
    }

    @Override
    protected void setupTable(@NotNull Handle handle) {
        handle.execute(SQL.CREATE_TABLE);
        handle.execute(SQL.CREATE_STATUS_INDEX);
        handle.execute(SQL.CREATE_CREATION_DATE_INDEX);
        handle.execute(SQL.CREATE_RECIPIENT_INDEX);
    }

    @Override
    public void addNotification(@NotNull Notification notification) {
        LOGGER.debug("addNotification {}", notification);
        if(NEW_NOTIFICATION_ID != notification.id) {
            throw new IllegalArgumentException("Notification id is not new : " + notification);
        }
        update(SQL.INSERT_NOTIFICATION, query -> bindNotificationFields(notification, query));
    }

    @Override
    @NotNull
    public List<Notification> getNewNotifications(long limit) {
        LOGGER.debug("getNewNotifications {}", limit);
        return query(SQL.SELECT_HAVING_STATUS_NEW, Notification.class,
                Map.of(LIMIT_ARGUMENT, requireStrictlyPositive(limit)));
    }

    @Override
    public long unblockStatusOfRecipient(@NotNull RecipientType recipientType, @NotNull String recipientId) {
        LOGGER.debug("unblockStatusOfRecipient {} {}", recipientType, recipientId);
        return update(SQL.UPDATE_STATUS_NEW_WHERE_BLOCKED_AND_RECIPIENT,
                Map.of(RECIPIENT_ID, recipientId, RECIPIENT_TYPE, recipientType.shortName));
    }

    @Override
    public void statusRecipientBatchUpdate(@NotNull NotificationStatus status, @NotNull String recipientId, @NotNull RecipientType recipientType, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("statusRecipientBatchUpdate {} {} {}", status, recipientId, recipientType);
        batchUpdates(updater, SQL.UPDATE_STATUS_RECIPIENT,
                Map.of(STATUS, status, RECIPIENT_TYPE, recipientType.shortName, RECIPIENT_ID, recipientId));
    }

    @Override
    public void statusBatchUpdate(@NotNull NotificationStatus status, @NotNull Consumer<BatchEntry> updater) {
        LOGGER.debug("statusBatchUpdate {}", status);
        batchUpdates(updater, SQL.UPDATE_STATUS_BY_ID, Map.of(STATUS, status));
    }

    @Override
    public void delete(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("batchDelete");
        batchUpdates(deleter, SQL.DELETE_BY_ID, emptyMap());
    }

    @Override
    public long deleteHavingCreationDateBefore(@NotNull ZonedDateTime expirationDate) {
        LOGGER.debug("deleteHavingCreationDateBefore {}", expirationDate);
        return update(SQL.DELETE_HAVING_CREATION_DATE_BEFORE,
                Map.of(EXPIRATION_DATE_ARGUMENT, expirationDate.toInstant().toEpochMilli()));
    }
}
