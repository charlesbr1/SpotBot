package org.sbot.services.dao;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.transaction.TransactionIsolationLevel;
import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.services.dao.jdbi.JDBIRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static org.sbot.services.dao.LastCandlesticksSQLite.CandlestickMapper.bindFields;
import static org.sbot.utils.ArgumentValidator.requirePairFormat;
import static org.sbot.utils.Dates.parseDateTime;

public final class LastCandlesticksSQLite implements LastCandlesticksDao {

    private static final Logger LOGGER = LogManager.getLogger(LastCandlesticksSQLite.class);

    interface SQL {
        String CREATE_TABLE = """
                CREATE TABLE IF NOT EXISTS last_candlesticks (
                pair TEXT PRIMARY KEY,
                open_time INTEGER NOT NULL,
                close_time INTEGER NOT NULL,
                open TEXT NOT NULL,
                close TEXT NOT NULL,
                high TEXT NOT NULL,
                low TEXT NOT NULL) STRICT
                """;

        String CREATE_PAIR_INDEX = "CREATE INDEX IF NOT EXISTS last_candlesticks_pair_index ON last_candlesticks (pair)";

        String SELECT_BY_PAIR = "SELECT open_time,close_time,open,close,high,low FROM last_candlesticks WHERE pair = :pair";
        String INSERT_LAST_CANDLESTICK = "INSERT INTO last_candlesticks (pair,open_time,close_time,open,close,high,low) VALUES (:pair,:open_time,:close_time,:open,:close,:high,:low)";

    }

    static final class CandlestickMapper implements RowMapper<Candlestick> {

        @Override // from SQL to Candlestick
        public Candlestick map(ResultSet rs, StatementContext ctx) throws SQLException {
            ZonedDateTime openTime = parseDateTime(rs.getTimestamp("open_time"));
            ZonedDateTime closeTime = parseDateTime(rs.getTimestamp("close_time"));
            BigDecimal open = rs.getBigDecimal("open");
            BigDecimal close = rs.getBigDecimal("close");
            BigDecimal high = rs.getBigDecimal("high");
            BigDecimal low = rs.getBigDecimal("low");
            return new Candlestick(requireNonNull(openTime), requireNonNull(closeTime), open, close, high, low);
        }

        // from Candlestick to SQL
        static void bindFields(@NotNull Candlestick candlestick, @NotNull SqlStatement<?> query) {
            query.bindFields(candlestick); // this bind common public fields from class Candlestick
        }
    }

    private final JDBIRepository repository;

    public LastCandlesticksSQLite(@NotNull JDBIRepository repository) {
        LOGGER.debug("Loading SQLite storage for last_candlesticks");
        this.repository = requireNonNull(repository);
        repository.registerRowMapper(new CandlestickMapper());
        setupTable();
    }

    private Handle getHandle() {
        return repository.getHandle();
    }

    private void setupTable() {
        transactional(() -> {
            getHandle().execute(SQL.CREATE_TABLE);
            getHandle().execute(SQL.CREATE_PAIR_INDEX);
        });
    }

    @Override
    public <T> T transactional(@NotNull Supplier<T> callback, @NotNull TransactionIsolationLevel isolationLevel) {
        return repository.transactional(callback, isolationLevel);
    }

    @Override
    public Optional<Candlestick> getLastCandlestick(@NotNull String pair) {
        LOGGER.debug("getLastCandlestick {}", pair);
        try (var query = getHandle().createQuery(SQL.SELECT_BY_PAIR)) {
            return query.bind("pair", pair)
                    .mapTo(Candlestick.class)
                    .findOne();
        }
    }

    @Override
    public void setLastCandlestick(@NotNull String pair, @NotNull Candlestick candlestick) {
        LOGGER.debug("setLastCandlestick {} {}", pair, candlestick);
        try (var query = getHandle().createUpdate(SQL.INSERT_LAST_CANDLESTICK)) {
            query.bind("pair", requirePairFormat(pair));
            bindFields(candlestick, query);
            query.execute();
        }
    }
}
