package org.sbot.services.dao.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.NotNull;
import org.sbot.entities.chart.Candlestick;
import org.sbot.services.dao.LastCandlesticksDao;
import org.sbot.services.dao.sql.jdbi.AbstractJDBI;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;
import org.sbot.services.dao.sql.jdbi.JDBIRepository.BatchEntry;
import org.sbot.services.dao.sql.jdbi.JDBITransactionHandler;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.*;
import static org.sbot.utils.ArgumentValidator.*;
import static org.sbot.utils.Dates.parseUtcDateTime;

public final class LastCandlesticksSQLite extends AbstractJDBI implements LastCandlesticksDao {

    private static final Logger LOGGER = LogManager.getLogger(LastCandlesticksSQLite.class);

    interface SQL {
        String CREATE_TABLE = """
                CREATE TABLE IF NOT EXISTS last_candlesticks (
                exchange TEXT NOT NULL,
                pair TEXT NOT NULL,
                open_time INTEGER NOT NULL,
                close_time INTEGER NOT NULL,
                open TEXT NOT NULL,
                close TEXT NOT NULL,
                high TEXT NOT NULL,
                low TEXT NOT NULL,
                PRIMARY KEY (exchange, pair)) STRICT, WITHOUT ROWID
                """;

        String SELECT_PAIRS_EXCHANGES = "SELECT DISTINCT exchange,pair FROM last_candlesticks";
        String SELECT_BY_PAIR = "SELECT open_time,close_time,open,close,high,low FROM last_candlesticks WHERE exchange=:exchange AND pair=:pair";
        String SELECT_CLOSE_TIME_BY_PAIR = "SELECT close_time FROM last_candlesticks WHERE exchange=:exchange AND pair=:pair";
        String INSERT_LAST_CANDLESTICK = "INSERT INTO last_candlesticks (exchange,pair,open_time,close_time,open,close,high,low) VALUES (:exchange,:pair,:openTime,:closeTime,:open,:close,:high,:low)";
        String UPDATE_LAST_CANDLESTICK = "UPDATE last_candlesticks SET open_time=:openTime,close_time=:closeTime,open=:open,close=:close,high=:high,low=:low WHERE exchange=:exchange AND pair=:pair";
        String DELETE_BY_EXCHANGE_PAIR = "DELETE FROM last_candlesticks WHERE exchange=:exchange AND pair=:pair";
    }

    public static final class CandlestickMapper implements RowMapper<Candlestick> {
        @Override
        public Candlestick map(ResultSet rs, StatementContext ctx) throws SQLException {
            ZonedDateTime openTime = parseUtcDateTime(rs.getTimestamp("open_time"))
                    .orElseThrow(() -> new IllegalArgumentException("Missing field last_candlesticks open_time"));
            ZonedDateTime closeTime = parseUtcDateTime(rs.getTimestamp("close_time"))
                    .orElseThrow(() -> new IllegalArgumentException("Missing field last_candlesticks close_time"));
            BigDecimal open = rs.getBigDecimal("open");
            BigDecimal close = rs.getBigDecimal("close");
            BigDecimal high = rs.getBigDecimal("high");
            BigDecimal low = rs.getBigDecimal("low");
            return new Candlestick(openTime, closeTime, open, close, high, low);
        }
    }

    private static void bindCandlestickFields(@NotNull String exchange, @NotNull String pair, @NotNull Candlestick candlestick, @NotNull SqlStatement<?> query) {
        query.bind("exchange", requireSupportedExchange(exchange));
        query.bind("pair", requirePairFormat(pair));
        query.bind("openTime", candlestick.openTime());
        query.bind("closeTime", candlestick.closeTime());
        query.bind("open", candlestick.open());
        query.bind("close", candlestick.close());
        query.bind("high", candlestick.high());
        query.bind("low", candlestick.low());
    }

    public LastCandlesticksSQLite(@NotNull JDBIRepository repository) {
        super(repository, new CandlestickMapper());
        LOGGER.debug("Loading SQLite storage for last_candlesticks");
    }

    LastCandlesticksSQLite(@NotNull AbstractJDBI abstractJDBI, @NotNull JDBITransactionHandler transactionHandler) {
        super(abstractJDBI, transactionHandler);
    }

    @Override
    public LastCandlesticksSQLite withHandler(@NotNull JDBITransactionHandler transactionHandler) {
        return new LastCandlesticksSQLite(this, transactionHandler);
    }

    @Override
    protected void setupTable(@NotNull Handle handle) {
        handle.execute(SQL.CREATE_TABLE);
    }


    @Override
    @NotNull
    public Map<String, Set<String>> getPairsByExchanges() {
        LOGGER.debug("getPairsByExchanges");
        return queryCollect(SQL.SELECT_PAIRS_EXCHANGES, emptyMap(),
                groupingBy(
                        rowView -> rowView.getColumn("exchange", String.class),
                        mapping(rowView -> rowView.getColumn("pair", String.class), toSet())));
    }

    @Override
    public Optional<ZonedDateTime> getLastCandlestickCloseTime(@NotNull String exchange, @NotNull String pair) {
        LOGGER.debug("getLastCandlestickCloseTime {} {}", exchange, pair);
        return findOneDateTime(SQL.SELECT_CLOSE_TIME_BY_PAIR, Map.of("exchange", requireSupportedExchange(exchange), "pair", requirePairFormat(pair)));
    }

    @Override
    public Optional<Candlestick> getLastCandlestick(@NotNull String exchange, @NotNull String pair) {
        LOGGER.debug("getLastCandlestick {} {}", exchange, pair);
        return findOne(SQL.SELECT_BY_PAIR, Candlestick.class, Map.of("exchange", requireSupportedExchange(exchange), "pair", requirePairFormat(pair)));
    }

    @Override
    public void setLastCandlestick(@NotNull String exchange, @NotNull String pair, @NotNull Candlestick candlestick) {
        LOGGER.debug("setLastCandlestick {} {} {}", exchange, pair, candlestick);
        requireNonNull(exchange); requireNonNull(pair); requireNonNull(candlestick);
        update(SQL.INSERT_LAST_CANDLESTICK, query -> bindCandlestickFields(exchange, pair, candlestick, query));
    }

    @Override
    public void updateLastCandlestick(@NotNull String exchange, @NotNull String pair, @NotNull Candlestick candlestick) {
        LOGGER.debug("updateLastCandlestick {} {} {}", exchange, pair, candlestick);
        requireNonNull(exchange); requireNonNull(pair); requireNonNull(candlestick);
        update(SQL.UPDATE_LAST_CANDLESTICK, query -> bindCandlestickFields(exchange, pair, candlestick, query));
    }

    @Override
    public void lastCandlestickBatchDeletes(@NotNull Consumer<BatchEntry> deleter) {
        LOGGER.debug("lastCandlestickBatchDeletes");
        batchUpdates(deleter, SQL.DELETE_BY_EXCHANGE_PAIR, emptyMap());
    }
}
