package org.sbot.services.dao.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.SqlStatement;
import org.jdbi.v3.core.statement.StatementContext;
import org.jetbrains.annotations.NotNull;
import org.sbot.chart.Candlestick;
import org.sbot.services.dao.LastCandlesticksDao;
import org.sbot.services.dao.sql.jdbi.AbstractJDBI;
import org.sbot.services.dao.sql.jdbi.JDBIRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.sbot.utils.ArgumentValidator.requirePairFormat;
import static org.sbot.utils.Dates.parseDateTimeOrNull;

public final class LastCandlesticksSQLite extends AbstractJDBI implements LastCandlesticksDao {

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

        String SELECT_BY_PAIR = "SELECT open_time,close_time,open,close,high,low FROM last_candlesticks WHERE pair=:pair";
        String SELECT_CLOSE_TIME_BY_PAIR = "SELECT close_time FROM last_candlesticks WHERE pair=:pair";
        String INSERT_LAST_CANDLESTICK = "INSERT INTO last_candlesticks (pair,open_time,close_time,open,close,high,low) VALUES (:pair,:openTime,:closeTime,:open,:close,:high,:low)";
        String UPDATE_LAST_CANDLESTICK = "UPDATE last_candlesticks SET open_time=:openTime,close_time=:closeTime,open=:open,close=:close,high=:high,low=:low WHERE pair=:pair";

    }

    private static final class CandlestickMapper implements RowMapper<Candlestick> {
        @Override
        public Candlestick map(ResultSet rs, StatementContext ctx) throws SQLException {
            ZonedDateTime openTime = parseDateTimeOrNull(rs.getTimestamp("open_time"));
            ZonedDateTime closeTime = parseDateTimeOrNull(rs.getTimestamp("close_time"));
            BigDecimal open = rs.getBigDecimal("open");
            BigDecimal close = rs.getBigDecimal("close");
            BigDecimal high = rs.getBigDecimal("high");
            BigDecimal low = rs.getBigDecimal("low");
            return new Candlestick(requireNonNull(openTime), requireNonNull(closeTime), open, close, high, low);
        }
    }

    private static void bindCandlestickFields(@NotNull String pair, @NotNull Candlestick candlestick, @NotNull SqlStatement<?> query) {
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

    @Override
    protected void setupTable() {
        transactional(() -> getHandle().execute(SQL.CREATE_TABLE));
    }

    @Override
    public Optional<ZonedDateTime> getLastCandlestickCloseTime(@NotNull String pair) {
        LOGGER.debug("getLastCandlestickCloseTime {}", pair);
        return findOneDateTime(SQL.SELECT_CLOSE_TIME_BY_PAIR, Map.of("pair", pair));
    }

    @Override
    public Optional<Candlestick> getLastCandlestick(@NotNull String pair) {
        LOGGER.debug("getLastCandlestick {}", pair);
        return findOne(SQL.SELECT_BY_PAIR, Candlestick.class, Map.of("pair", pair));
    }

    @Override
    public void setLastCandlestick(@NotNull String pair, @NotNull Candlestick candlestick) {
        LOGGER.debug("setLastCandlestick {} {}", pair, candlestick);
        update(SQL.INSERT_LAST_CANDLESTICK, query -> bindCandlestickFields(pair, candlestick, query));
    }

    @Override
    public void updateLastCandlestick(@NotNull String pair, @NotNull Candlestick candlestick) {
        LOGGER.debug("updateLastCandlestick {} {}", pair, candlestick);
        update(SQL.UPDATE_LAST_CANDLESTICK, query -> bindCandlestickFields(pair, candlestick, query));
    }
}
