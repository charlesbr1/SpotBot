package org.sbot.services.jdbi;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.result.StreamConsumer;
import org.jdbi.v3.core.statement.Batch;
import org.jdbi.v3.core.statement.PreparedBatch;
import org.jetbrains.annotations.NotNull;
import org.sbot.alerts.Alert;
import org.sbot.services.Alerts;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;

public class AlertsJDBI {

    private static final String SQL_CREATE_TABLE = "CREATE TABLE alerts (" +
            "id INTEGER PRIMARY KEY," +
            "user_id INTEGER NOT NULL," +
            "server_id INTEGER NOT NULL," +
            "exchange TEXT NOT NULL," +
            "ticker1 TEXT NOT NULL," +
            "ticker2 TEXT NOT NULL," +
            "message TEXT NOT NULL," +
            "lastTrigger DATETIME NOT NULL," +
            "margin NUMBER NOT NULL," +
            "repeat INTEGER NOT NULL," +
            "repeatDelay INTEGER NOT NULL)";

    private static final String SQL_CREATE_USER_ID_INDEX = "CREATE INDEX IF NOT EXISTS user_id_index " +
            "ON alerts (user_id)";

    private static final String SQL_CREATE_SERVER_ID_INDEX = "CREATE INDEX IF NOT EXISTS server_id_index " +
            "ON alerts (server_id)";

    private static final String SQL_SELECT_BY_ID = "SELECT * FROM alerts WHERE id=:id";
    private static final String SQL_DELETE_BY_ID = "DELETE FROM alerts WHERE id=:id";
    private static final String SQL_SELECT_ALL = "SELECT * FROM alerts";
    private static final String SQL_INSERT = "INSERT INTO alerts (user_id,server_id,exchange,ticker1,ticker2,message,lastTrigger,margin,repeat,repeatDelay) " +
            "VALUES (:userId,:serverId,:exchange,:ticker1,:ticker2,:message,:lastTrigger,:margin,:repeat,:repeatDelay)";


    private final JDBIRepository repository;

    public AlertsJDBI(@NotNull JDBIRepository repository) {
        this.repository = requireNonNull(repository);
        repository.registerRowMapper(Alerts.class);
        setupTable();
    }

    private void setupTable() {
        repository.transactional(handle -> {
            handle.execute(SQL_CREATE_TABLE);
            handle.execute(SQL_CREATE_USER_ID_INDEX);
            handle.execute(SQL_CREATE_SERVER_ID_INDEX);
        });
    }

    public Optional<Alert> getAlert(@NotNull Handle handle, long alertId) {
        return handle.createQuery(SQL_SELECT_BY_ID)
                .bind("id", alertId)
                .mapTo(Alert.class)
                .findFirst();
    }

    public <X extends Exception> void getAlerts(@NotNull Handle handle, @NotNull StreamConsumer<Alert, X> callBack) throws X {
        handle.createQuery(SQL_SELECT_ALL)
                .mapTo(Alert.class)
                .useStream(callBack);
    }

    public Map<String, Map<String, List<Alert>>> getAlertsByPairsAndExchanges(@NotNull Handle handle) {
        return handle.createQuery(SQL_SELECT_ALL)
                .mapTo(Alert.class)
                .collect(groupingBy(Alert::getExchange, groupingBy(Alert::getSlashPair)));
    }

    public long addAlert(@NotNull Handle handle, @NotNull Alert alert) {
        return handle.createUpdate(SQL_INSERT)
                .bindFields(alert)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one();
    }

    public boolean deleteAlert(@NotNull Handle handle, long alertId) {
        return 0 != handle.createUpdate(SQL_DELETE_BY_ID)
                .bind("id", alertId)
                .execute();
    }

    public void updateAlerts(@NotNull List<Alert> alerts) {
        //TODO
    }

    void batches(Handle handle) {
        Batch batch = handle.createBatch();

        batch.add("INSERT INTO alerts VALUES(0, 'apple')");
        batch.add("INSERT INTO alerts VALUES(1, 'banana')");

        int[] rowsModified = batch.execute();

        PreparedBatch pbatch = handle.prepareBatch("INSERT INTO alerts (id, message) VALUES(:id, :name)");
        for (int i = 100; i < 5000; i++) {
            pbatch.bind("id", i).bind("name", "User:" + i).add();
        }
        int[] counts = pbatch.execute();

    }


}
