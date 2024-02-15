package org.sbot.services.dao.sql.jdbi;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.NoSuchMapperException;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.UnableToCreateStatementException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sbot.services.context.Context;
import org.sbot.services.context.TransactionalContext;
import org.sbot.services.dao.sql.jdbi.AbstractJDBITest.RowMapperTest;
import org.sbot.services.dao.sql.jdbi.AbstractJDBITest.TestAbstractJDBI;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.*;
import static org.jdbi.v3.core.transaction.TransactionIsolationLevel.READ_COMMITTED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class JDBIRepositoryTest {

    public static final String SQLITE_MEMORY_VOLATILE = "jdbc:sqlite::memory:";
    public static final String SQLITE_MEMORY_PERSISTENT = "jdbc:sqlite:file::memory:?cache=shared";

    private static final String CREATE_TEST_TABLE = """
                CREATE TABLE IF NOT EXISTS test (
                id INTEGER PRIMARY KEY,
                text TEXT NOT NULL,
                decimal TEXT NOT NULL,
                locale TEXT NOT NULL,
                date INTEGER NOT NULL) STRICT
                """;

    public static final String DROP_TEST_TABLE = "DROP TABLE test";
    public static final String INSERT_TEST = "INSERT INTO test (id,text,decimal,locale,date) VALUES (:id,:text,:decimal,:locale,:date)";
    public static final String UPDATE_TEXT_BY_ID = "UPDATE test SET text=:text WHERE id=:id";
    public static final String SELECT_ID_TEXT_BY_ID = "SELECT id,text FROM test WHERE id=:id";
    public static final String SELECT_ID_TEXT_BY_TEXT = "SELECT id,text FROM test WHERE text LIKE :text";
    public static final String SELECT_ID_BY_ID = "SELECT id FROM test WHERE id=:id";
    public static final String SELECT_ID_BY_TEXT = "SELECT id FROM test WHERE text LIKE :text";
    public static final String SELECT_TEXT_BY_ID = "SELECT text FROM test WHERE id=:id";
    public static final String SELECT_LOCALE_BY_ID = "SELECT locale FROM test WHERE id=:id";
    public static final String SELECT_TEXT_BY_TEXT = "SELECT text FROM test WHERE text LIKE :text";
    public static final String SELECT_TEXT_BY_DATE = "SELECT text FROM test WHERE date=:date";
    public static final String SELECT_DECIMAL_BY_ID = "SELECT decimal FROM test WHERE id=:id";
    public static final String SELECT_DECIMAL_BY_TEXT = "SELECT decimal FROM test WHERE text LIKE :text";
    public static final String SELECT_LOCALE_BY_TEXT = "SELECT locale FROM test WHERE text LIKE :text";
    public static final String SELECT_DATE_BY_ID = "SELECT date FROM test WHERE id=:id";
    public static final String SELECT_DATE_BY_TEXT = "SELECT date FROM test WHERE text LIKE :text";
    public static final String DELETE_BY_ID = "DELETE FROM test WHERE id=:id";
    public static final String DELETE_BY_ID_AND_TEXT = "DELETE FROM test WHERE id=:id AND text LIKE :text";

    public static final long TEST_ID = 123L;
    public static final long TEST_ID2 = 124L;
    public static final long TEST_ID3 = 125L;
    public static final String TEST_TEXT = "text";
    public static final BigDecimal TEST_DECIMAL1 = new BigDecimal("0.0000000000000001");
    public static final BigDecimal TEST_DECIMAL2 = new BigDecimal("0.0000000000000000002");
    public static final Locale TEST_LOCALE = Locale.CANADA_FRENCH;
    public static final Locale TEST_LOCALE2 = Locale.TAIWAN;
    public static final ZonedDateTime TEST_DATE = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    public static final ZonedDateTime TEST_DATE_UTC = TEST_DATE.withZoneSameInstant(ZoneOffset.UTC);

    private JDBIRepository repository;


    public static AbstractJDBI fakeJdbi(@Nullable JDBIRepository repository) {
        // build a fake abstractJdbi instance that hold repository field
        try {
            var field = AbstractJDBI.class.getDeclaredField("repository");
            field.setAccessible(true);
            var abstractJdbi = mock(AbstractJDBI.class);
            field.set(abstractJdbi, repository);
            return abstractJdbi;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T extends AbstractJDBI> T loadTransactionalDao(BiConsumer<T, Handle> setupTable, Consumer<JDBIRepository> initConstructor, BiFunction<AbstractJDBI, JDBITransactionHandler, T> constructor) {
        JDBIRepository repository = new JDBIRepository(SQLITE_MEMORY_VOLATILE);
        try { // this register the row mapper then fails because each tx is rollback
            initConstructor.accept(repository);
        } catch (RuntimeException e) {
        }
        // build a fake abstractJdbi instance that hold repository field
        var abstractJdbi = fakeJdbi(repository);
        var transactionHandler = new JDBITransactionHandler(READ_COMMITTED);
        var dao = constructor.apply(abstractJdbi, transactionHandler);
        // create the table in current tx
        var handle = transactionHandler.sync(repository.jdbi, h -> h);
        setupTable.accept(dao, handle);
        return dao;
    }

    @BeforeEach
    void loadRepository() {
        repository = new JDBIRepository(SQLITE_MEMORY_VOLATILE);
    }

    private void fillRepository() {
        repository.inTransaction(handle -> {
            handle.execute(CREATE_TEST_TABLE);
            repository.update(handle, INSERT_TEST,
                    Map.of("id", TEST_ID, "text", TEST_TEXT, "decimal", TEST_DECIMAL1, "locale", TEST_LOCALE, "date", TEST_DATE_UTC));
            repository.update(handle, INSERT_TEST,
                    Map.of("id", TEST_ID2, "text", TEST_TEXT, "decimal", TEST_DECIMAL2, "locale", TEST_LOCALE2, "date", TEST_DATE_UTC));
            repository.update(handle, INSERT_TEST,
                    Map.of("id", TEST_ID3, "text", "text3", "decimal", BigDecimal.ZERO, "locale", TEST_LOCALE2, "date", TEST_DATE_UTC));
            return null;
        });
    }

    record TestRecord(long id, String text) {}

    static final class TestRowMapper implements RowMapper<TestRecord> {
        @Override
        public TestRecord map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new TestRecord(rs.getLong("id"), rs.getString("text"));
        }
    }

    @Test
    void registerRowMapper() {
        assertThrows(NullPointerException.class, () -> repository.registerRowMapper(null));

        repository.inTransaction(handle -> {
            fillRepository();
            assertThrows(NoSuchMapperException.class, () -> repository.query(handle, SELECT_ID_TEXT_BY_ID, TestRecord.class, Map.of("id", TEST_ID)));
            return null;
        });
        repository.registerRowMapper(new TestRowMapper());
        repository.inTransaction(handle -> {
            fillRepository();
            assertDoesNotThrow(() -> repository.query(handle, SELECT_ID_TEXT_BY_ID, TestRecord.class, Map.of("id", TEST_ID)));
            TestRecord record = repository.query(handle, SELECT_ID_TEXT_BY_ID, TestRecord.class, Map.of("id", TEST_ID)).get(0);
            assertEquals(TEST_ID, record.id);
            assertEquals(TEST_TEXT, record.text);
            return null;
        });
    }

    @Test
    void inTransaction() {
        assertTrue(repository.<Boolean>inTransaction(handle -> {
            fillRepository();
            assertEquals(TEST_ID, repository.queryOneLong(handle, SELECT_ID_BY_ID, Map.of("id", TEST_ID)));
            return true;
        }));
    }

    @Test
    void update() {
        repository.inTransaction(handle -> {
            fillRepository();

            assertEquals(Optional.of(TEST_ID), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            assertEquals(Optional.of(TEST_TEXT), repository.findOne(handle, SELECT_TEXT_BY_ID, String.class, Map.of("id", TEST_ID)));
            assertEquals(Optional.of(TEST_DECIMAL1), repository.findOne(handle, SELECT_DECIMAL_BY_ID, BigDecimal.class, Map.of("id", TEST_ID)));
            assertEquals(Optional.of(TEST_LOCALE), repository.findOne(handle, SELECT_LOCALE_BY_ID, Locale.class, Map.of("id", TEST_ID)));
            assertEquals(Optional.of(TEST_DATE_UTC), repository.findOneDateTime(handle, SELECT_DATE_BY_ID, Map.of("id", TEST_ID)));

            repository.update(handle, INSERT_TEST, query -> query
                    .bind("id", 321L)
                    .bind("text", "text2")
                    .bind("decimal", TEST_DECIMAL1.multiply(new BigDecimal("0.00000123456")))
                    .bind("locale", Locale.US)
                    .bind("date", TEST_DATE.plusMinutes(33L)));

            assertEquals(Optional.of(321L), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", 321L)));
            assertEquals(Optional.of("text2"), repository.findOne(handle, SELECT_TEXT_BY_ID, String.class, Map.of("id", 321L)));
            assertEquals(Optional.of(TEST_DECIMAL1.multiply(new BigDecimal("0.00000123456"))), repository.findOne(handle, SELECT_DECIMAL_BY_ID, BigDecimal.class, Map.of("id", 321L)));
            assertEquals(Optional.of(Locale.US), repository.findOne(handle, SELECT_LOCALE_BY_ID, Locale.class, Map.of("id", 321L)));
            assertEquals(Optional.of(TEST_DATE_UTC.plusMinutes(33L)), repository.findOneDateTime(handle, SELECT_DATE_BY_ID, Map.of("id", 321L)));
            return null;
        });
    }

    @Test
    void query() {
        repository.inTransaction(handle -> {
            fillRepository();

            assertEquals(List.of(TEST_ID), repository.query(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            assertEquals(List.of(TEST_ID, 124L), repository.query(handle, SELECT_ID_BY_TEXT, Long.class, Map.of("text", TEST_TEXT)));

            assertEquals(List.of(TEST_TEXT), repository.query(handle, SELECT_TEXT_BY_ID, String.class, Map.of("id", TEST_ID)));
            assertEquals(List.of(TEST_TEXT, TEST_TEXT), repository.query(handle, SELECT_TEXT_BY_TEXT, String.class, Map.of("text", TEST_TEXT)));

            assertEquals(List.of(TEST_DECIMAL1), repository.query(handle, SELECT_DECIMAL_BY_ID, BigDecimal.class, Map.of("id", TEST_ID)));
            assertEquals(List.of(TEST_DECIMAL1, TEST_DECIMAL2), repository.query(handle, SELECT_DECIMAL_BY_TEXT, BigDecimal.class, Map.of("text", TEST_TEXT)));

            assertEquals(List.of(TEST_LOCALE), repository.query(handle, SELECT_LOCALE_BY_ID, Locale.class, Map.of("id", TEST_ID)));
            assertEquals(List.of(TEST_LOCALE, TEST_LOCALE2), repository.query(handle, SELECT_LOCALE_BY_TEXT, Locale.class, Map.of("text", TEST_TEXT)));

            assertEquals(List.of(TEST_DATE), repository.query(handle, SELECT_DATE_BY_ID, ZonedDateTime.class, Map.of("id", TEST_ID)));
            assertEquals(List.of(TEST_DATE, TEST_DATE), repository.query(handle, SELECT_DATE_BY_TEXT, ZonedDateTime.class, Map.of("text", TEST_TEXT)));
            return null;
        });
    }

    @Test
    void queryOne() {
        repository.inTransaction(handle -> {
            fillRepository();

            assertEquals(TEST_ID, repository.queryOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            assertThrows(IllegalStateException.class, () -> repository.queryOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", 6543L)));
            assertThrows(IllegalStateException.class, () -> repository.queryOne(handle, SELECT_ID_BY_TEXT, Long.class, Map.of("text", TEST_TEXT)));
            assertThrows(IllegalStateException.class, () -> repository.queryOne(handle, SELECT_LOCALE_BY_TEXT, Locale.class, Map.of("text", TEST_TEXT)));
            return null;
        });
    }

    @Test
    void queryOneLong() {
        repository.inTransaction(handle -> {
            fillRepository();

            assertEquals(TEST_ID, repository.queryOneLong(handle, SELECT_ID_BY_ID, Map.of("id", TEST_ID)));
            assertThrows(IllegalStateException.class, () -> repository.queryOneLong(handle, SELECT_ID_BY_ID, Map.of("id", 6543L)));
            assertThrows(IllegalStateException.class, () -> repository.queryOneLong(handle, SELECT_ID_BY_TEXT, Map.of("text", TEST_TEXT)));
            return null;
        });
    }

    @Test
    void queryMap() {
        repository.inTransaction(handle -> {
            fillRepository();
            var type = new GenericType<Map<Long, String>>() {};
            assertEquals(Map.of(TEST_ID, TEST_TEXT) , repository.queryMap(handle, SELECT_ID_TEXT_BY_ID, type, q -> q.bindMap(Map.of("id", TEST_ID)), "id", "text"));
            assertEquals(emptyMap() , repository.queryMap(handle, SELECT_ID_TEXT_BY_ID, type, q -> q.bindMap(Map.of("id", 6543L)), "id", "text"));
            assertEquals(Map.of(TEST_ID, TEST_TEXT, TEST_ID2, TEST_TEXT) , repository.queryMap(handle, SELECT_ID_TEXT_BY_TEXT, type, q -> q.bindMap(Map.of("text", TEST_TEXT)), "id", "text"));
            assertEquals(Map.of(String.valueOf(TEST_ID), 0L, String.valueOf(TEST_ID2), 0L) , repository.queryMap(handle, SELECT_ID_TEXT_BY_TEXT, new GenericType<Map<String, Long>>() {}, q -> q.bindMap(Map.of("text", TEST_TEXT)), "id", "text"));
            assertThrows(IllegalStateException.class, () -> repository.queryMap(handle, SELECT_ID_TEXT_BY_TEXT, new GenericType<Map<String, Long>>() {}, q -> q.bindMap(Map.of("text", TEST_TEXT)), "text", "id"));
            return null;
        });
    }

    @Test
    void queryCollect() {
        repository.inTransaction(handle -> {
            fillRepository();

            assertEquals(Map.of(TEST_ID, Set.of(TEST_TEXT)) , repository.queryCollect(handle, SELECT_ID_TEXT_BY_ID, Map.of("id", TEST_ID), groupingBy(
                    rowView -> rowView.getColumn("id", Long.class),
                    mapping(rowView -> rowView.getColumn("text", String.class), toSet()))));
            assertEquals(emptyMap() , repository.queryCollect(handle, SELECT_ID_TEXT_BY_ID, Map.of("id", 6543L), groupingBy(
                    rowView -> rowView.getColumn("id", Long.class),
                    mapping(rowView -> rowView.getColumn("text", String.class), toSet()))));
            assertEquals(Map.of(TEST_ID, Set.of(TEST_TEXT), TEST_ID2, Set.of(TEST_TEXT)) , repository.queryCollect(handle, SELECT_ID_TEXT_BY_TEXT, Map.of("text", TEST_TEXT), groupingBy(
                    rowView -> rowView.getColumn("id", Long.class),
                    mapping(rowView -> rowView.getColumn("text", String.class), toSet()))));
            assertEquals(Map.of(String.valueOf(TEST_ID), Set.of(0L), String.valueOf(TEST_ID2), Set.of(0L)) , repository.queryCollect(handle, SELECT_ID_TEXT_BY_TEXT, Map.of("text", TEST_TEXT), groupingBy(
                    rowView -> rowView.getColumn("id", String.class),
                    mapping(rowView -> rowView.getColumn("text", Long.class), toSet()))));
            assertEquals(Map.of(TEST_TEXT, Set.of(TEST_ID, TEST_ID2)) , repository.queryCollect(handle, SELECT_ID_TEXT_BY_TEXT, Map.of("text", TEST_TEXT), groupingBy(
                    rowView -> rowView.getColumn("text", String.class),
                    mapping(rowView -> rowView.getColumn("id", Long.class), toSet()))));
            return null;
        });
    }

    @Test
    void fetch() {
        repository.inTransaction(handle -> {
            fillRepository();

            assertEquals(1, repository.fetch(handle, SELECT_TEXT_BY_ID, String.class, Map.of("id", TEST_ID3),
                    stream -> assertEquals("text3", stream.collect(joining()))));

            assertEquals(2, repository.fetch(handle, SELECT_TEXT_BY_TEXT, String.class, Map.of("text", TEST_TEXT),
                    stream -> assertEquals("text,text", stream.collect(joining(",")))));

            assertEquals(3, repository.fetch(handle, SELECT_TEXT_BY_DATE, String.class, Map.of("date", TEST_DATE_UTC),
                    stream -> assertEquals("text,text,text3", stream.collect(joining(",")))));

            assertEquals(2, repository.fetch(handle, SELECT_LOCALE_BY_TEXT, Locale.class, Map.of("text", TEST_TEXT),
                    stream -> assertEquals("fr-CA,zh-TW", stream.map(Locale::toLanguageTag).collect(joining(",")))));
            return null;
        });
    }

    @Test
    void findOne() {
        repository.inTransaction(handle -> {
            fillRepository();

            assertEquals(Optional.of(TEST_ID), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            assertThrows(IllegalStateException.class, () -> repository.findOne(handle, SELECT_ID_BY_TEXT, Long.class, Map.of("text", TEST_TEXT)));

            assertEquals(Optional.of(TEST_TEXT), repository.findOne(handle, SELECT_TEXT_BY_ID, String.class, Map.of("id", TEST_ID)));
            assertThrows(IllegalStateException.class, () -> repository.findOne(handle, SELECT_TEXT_BY_TEXT, String.class, Map.of("text", TEST_TEXT)));

            assertEquals(Optional.of(TEST_DECIMAL1), repository.findOne(handle, SELECT_DECIMAL_BY_ID, BigDecimal.class, Map.of("id", TEST_ID)));
            assertThrows(IllegalStateException.class, () -> repository.findOne(handle, SELECT_DECIMAL_BY_TEXT, BigDecimal.class, Map.of("text", TEST_TEXT)));

            assertEquals(Optional.of(TEST_LOCALE), repository.findOne(handle, SELECT_LOCALE_BY_ID, Locale.class, Map.of("id", TEST_ID)));
            assertThrows(IllegalStateException.class, () -> repository.findOne(handle, SELECT_LOCALE_BY_TEXT, Locale.class, Map.of("text", TEST_TEXT)));

            assertEquals(Optional.of(TEST_DATE), repository.findOne(handle, SELECT_DATE_BY_ID, ZonedDateTime.class, Map.of("id", TEST_ID)));
            assertThrows(IllegalStateException.class, () -> repository.findOne(handle, SELECT_DATE_BY_TEXT, BigDecimal.class, Map.of("text", TEST_TEXT)));
            return null;
        });
    }

    @Test
    void findOneLong() {
        repository.inTransaction(handle -> {
            fillRepository();

            assertEquals(Optional.of(TEST_ID), repository.findOneLong(handle, SELECT_ID_BY_ID, Map.of("id", TEST_ID)));
            assertEquals(Optional.empty(), repository.findOneLong(handle, SELECT_ID_BY_ID, Map.of("id", 6543L)));
            assertThrows(IllegalStateException.class, () -> repository.findOneLong(handle, SELECT_ID_BY_TEXT, Map.of("text", TEST_TEXT)));
            return null;
        });
    }

    @Test
    void findOneDateTime() {
        repository.inTransaction(handle -> {
            fillRepository();

            assertEquals(Optional.of(TEST_DATE_UTC), repository.findOneDateTime(handle, SELECT_DATE_BY_ID, Map.of("id", TEST_ID)));
            assertEquals(Optional.empty(), repository.findOneLong(handle, SELECT_DATE_BY_ID, Map.of("id", 6543L)));
            assertThrows(IllegalStateException.class, () -> repository.findOneLong(handle, SELECT_DATE_BY_TEXT, Map.of("text", TEST_TEXT)));
            return null;
        });
    }

    @Test
    void batchUpdates() {
        repository.inTransaction(handle -> {
            fillRepository();

            assertEquals(Optional.of(TEST_ID), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            assertEquals(Optional.of(TEST_TEXT), repository.findOne(handle, SELECT_TEXT_BY_ID, String.class, Map.of("id", TEST_ID)));
            repository.batchUpdates(handle, batchEntry -> batchEntry.batchId(TEST_ID), DELETE_BY_ID_AND_TEXT, Map.of("text", "bad text"));
            assertEquals(Optional.of(TEST_ID), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            repository.batchUpdates(handle, batchEntry -> batchEntry.batchId(TEST_ID), DELETE_BY_ID_AND_TEXT, Map.of("text", TEST_TEXT));
            assertEquals(Optional.empty(), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));

            assertEquals(Optional.of(TEST_ID2), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID2)));
            assertEquals(Optional.of(TEST_ID3), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID3)));
            assertEquals(Optional.empty(), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", 987654L)));
            repository.batchUpdates(handle, batchEntry -> {
                batchEntry.batchId(TEST_ID2);
                batchEntry.batchId(TEST_ID3);
                batchEntry.batchId(987654L);
            }, DELETE_BY_ID, emptyMap());

            assertEquals(Optional.empty(), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID2)));
            assertEquals(Optional.empty(), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID3)));
            assertEquals(Optional.empty(), repository.findOne(handle, SELECT_ID_BY_ID, Long.class, Map.of("id", 987654L)));
            return null;
        });
    }

    private static TestAbstractJDBI loadDao(@NotNull TestAbstractJDBI baseDao, @NotNull TransactionalContext transactionalContext) {
        return loadDao(baseDao, transactionalContext, false);
    }

    private static TestAbstractJDBI loadDao(@NotNull TestAbstractJDBI baseDao, @NotNull TransactionalContext transactionalContext, boolean disableLock) {
        var transactionHandler = getTransactionHandler(transactionalContext);
        if(disableLock) {
            disableLock(transactionHandler);
        }
        return new TestAbstractJDBI(baseDao, transactionHandler);
    }

    private static JDBITransactionHandler getTransactionHandler(@NotNull TransactionalContext transactionalContext) {
        try {
            var field = TransactionalContext.class.getDeclaredField("transactionHandler");
            field.setAccessible(true);
            return (JDBITransactionHandler) field.get(transactionalContext);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void disableLock(@NotNull JDBITransactionHandler transactionHandler) {
        try {
            var field = JDBITransactionHandler.class.getDeclaredField("lock");
            field.setAccessible(true);
            field.set(transactionHandler, mock(ReentrantLock.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void transactional() {
        var context = spy(Context.class);

        assertThrows(NullPointerException.class, () -> context.transactional(txCtx -> txCtx, null));
        assertThrows(NullPointerException.class, () -> context.transactional(null, READ_COMMITTED));
        assertThrows(NullPointerException.class, () -> context.transaction(txCtx -> {}, null));
        assertThrows(NullPointerException.class, () -> context.transaction(null, READ_COMMITTED));

        repository = new JDBIRepository(SQLITE_MEMORY_PERSISTENT);

        var baseDao = new TestAbstractJDBI(repository, new RowMapperTest());

        try (var handle = repository.jdbi.open()) { // need to maintain a handle open to keep data in memory between tests
            context.transaction(txCtx -> {
                fillRepository();
                var dao = loadDao(baseDao, txCtx);
                assertEquals(Optional.of(TEST_ID), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            });

            // test delete is commit
            context.transaction(txCtx -> {
                var dao = loadDao(baseDao, txCtx);
                assertEquals(Optional.of(TEST_ID), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            });
            context.transaction(txCtx -> loadDao(baseDao, txCtx).batchUpdates(batchEntry -> batchEntry.batchId(TEST_ID), DELETE_BY_ID, emptyMap()));
            context.transaction(txCtx -> {
                var dao = loadDao(baseDao, txCtx);
                assertEquals(Optional.empty(), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            });

            // test insert is commit
            context.transaction(txCtx -> loadDao(baseDao, txCtx).update(INSERT_TEST,
                    Map.of("id", TEST_ID, "text", TEST_TEXT, "decimal", TEST_DECIMAL1, "locale", TEST_LOCALE, "date", TEST_DATE_UTC)));
            context.transaction(txCtx -> {
                var dao = loadDao(baseDao, txCtx);
                assertEquals(Optional.of(TEST_ID), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            });

            // nested transactions
            context.transaction(txCtx -> txCtx.transaction(sameTxCtx -> {
                assertEquals(txCtx, sameTxCtx);
                var dao = loadDao(baseDao, sameTxCtx);
                dao.batchUpdates(batchEntry -> batchEntry.batchId(TEST_ID), DELETE_BY_ID, emptyMap());
            }));
            context.transaction(txCtx -> txCtx.transaction(sameTxCtx -> {
                assertEquals(txCtx, sameTxCtx);
                var dao = loadDao(baseDao, sameTxCtx);
                assertEquals(Optional.empty(), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            }));
            context.transaction(txCtx -> txCtx.transaction(sameTxCtx -> {
                assertEquals(txCtx, sameTxCtx);
                var dao = loadDao(baseDao, sameTxCtx);
                dao.update(INSERT_TEST,
                        Map.of("id", TEST_ID, "text", TEST_TEXT, "decimal", TEST_DECIMAL1, "locale", TEST_LOCALE2, "date", TEST_DATE_UTC));
            }));
            context.transaction(txCtx -> txCtx.transaction(sameTxCtx -> {
                assertEquals(txCtx, sameTxCtx);
                var dao = loadDao(baseDao, sameTxCtx);
                assertEquals(Optional.of(TEST_ID), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            }));

            // test delete is rollback
            context.transaction(txCtx -> {
                loadDao(baseDao, txCtx).batchUpdates(batchEntry -> batchEntry.batchId(TEST_ID), DELETE_BY_ID, emptyMap());
                txCtx.rollback();
            });
            context.transaction(txCtx -> {
                var dao = loadDao(baseDao, txCtx);
                assertEquals(Optional.of(TEST_ID), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            });

            boolean exceptionThrown = false;
            try {
                context.transaction(txCtx -> {
                    loadDao(baseDao, txCtx).batchUpdates(batchEntry -> batchEntry.batchId(TEST_ID), DELETE_BY_ID, emptyMap());
                    throw new RuntimeException();
                });
            } catch (RuntimeException e) {
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
            context.transaction(txCtx -> {
                var dao = loadDao(baseDao, txCtx);
                assertEquals(Optional.of(TEST_ID), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            });

            context.transaction(txCtx -> loadDao(baseDao, txCtx).batchUpdates(batchEntry -> batchEntry.batchId(TEST_ID), DELETE_BY_ID, emptyMap()));
            context.transaction(txCtx -> {
                var dao = loadDao(baseDao, txCtx);
                assertEquals(Optional.empty(), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            });

            // test insert is rollback
            context.transaction(txCtx -> {
                loadDao(baseDao, txCtx).update(INSERT_TEST,
                        Map.of("id", TEST_ID, "text", TEST_TEXT, "decimal", TEST_DECIMAL1, "locale", TEST_LOCALE2, "date", TEST_DATE_UTC));
                txCtx.rollback();
            });
            context.transaction(txCtx -> {
                var dao = loadDao(baseDao, txCtx);
                assertEquals(Optional.empty(), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            });

            exceptionThrown = false;
            try {
                context.transaction(txCtx -> {
                    loadDao(baseDao, txCtx).update(INSERT_TEST,
                            Map.of("id", TEST_ID, "text", TEST_TEXT, "decimal", TEST_DECIMAL1, "locale", TEST_LOCALE, "date", TEST_DATE_UTC));
                    throw new RuntimeException();
                });
            } catch (RuntimeException e) {
                exceptionThrown = true;
            }
            assertTrue(exceptionThrown);
            context.transaction(txCtx -> {
                var dao = loadDao(baseDao, txCtx);
                assertEquals(Optional.empty(), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            });
            context.transaction(txCtx -> loadDao(baseDao, txCtx).update(INSERT_TEST,
                    Map.of("id", TEST_ID, "text", TEST_TEXT, "decimal", TEST_DECIMAL1, "locale", TEST_LOCALE, "date", TEST_DATE_UTC)));
            context.transaction(txCtx -> {
                var dao = loadDao(baseDao, txCtx);
                assertEquals(Optional.of(TEST_ID), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));
            });

            assertDoesNotThrow(() -> context.transaction(txCtx -> loadDao(baseDao, txCtx).update(DROP_TEST_TABLE, emptyMap())));
            assertThrows(UnableToCreateStatementException.class, () -> context.transaction(txCtx -> loadDao(baseDao, txCtx).update(DROP_TEST_TABLE, emptyMap())));
        }
    }

    @Test
    void transactionalIsThreadSafe() {
        repository = new JDBIRepository(SQLITE_MEMORY_PERSISTENT);
        assertDoesNotThrow(() -> transactionalThreadTest(false));
        assertDoesNotThrow(() -> transactionalThreadTest(false));
        // disabled unmanaged concurrency tests as it breaks SQLITE_MEMORY_PERSISTENT for the other tests
//        assertThrows(UnableToExecuteStatementException.class, () -> transactionalThreadTest(true));
        // once error 'org.sqlite.SQLiteException: [SQLITE_LOCKED_SHAREDCACHE] Contention with a different database connection that shares the cache'
        // is thrown, further attempt to query the in memory database fails
//        assertThrows(UnableToExecuteStatementException.class, () -> transactionalThreadTest(false));
    }

    private void transactionalThreadTest(boolean disableLock) {
        var context = spy(Context.class);
        var baseDao = new TestAbstractJDBI(repository, new RowMapperTest());

        try (var handle = repository.jdbi.open()) { // need to maintain a handle open to keep data in memory between tests
            context.transaction(txCtx -> {
                fillRepository();
                var dao = loadDao(baseDao, txCtx, disableLock);
                assertEquals(Optional.of(TEST_ID), dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", TEST_ID)));

                AtomicLong counter = new AtomicLong(1000000L);
                Callable<Object> insertTask = () -> dao.update(INSERT_TEST,
                        Map.of("id", counter.incrementAndGet(), "text", TEST_TEXT, "decimal", TEST_DECIMAL1, "locale", TEST_LOCALE, "date", TEST_DATE_UTC));
                Callable<Object> findTask = () -> dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", counter.get() - 103));
                Callable<Object> updateTask = () -> dao.update(UPDATE_TEXT_BY_ID,
                        Map.of("id", counter.get() - 100, "text", TEST_TEXT + counter.get()));
                Callable<Object> deleteTask = () -> {
                    dao.batchUpdates(batchEntry -> batchEntry.batchId(counter.get() - 900), DELETE_BY_ID, emptyMap());
                    return null;
                };
                Callable<Object> commitTask = () -> { txCtx.commit(); return null; };
                Callable<Object> rollbackTask = () -> { txCtx.rollback(); return null; };


                var tasks = new ArrayList<>(IntStream.range(0, 1000).mapToObj(v -> insertTask).toList());
                tasks.addAll(IntStream.range(0, 10000).mapToObj(v -> List.of(findTask, insertTask, updateTask, deleteTask, v % 8 == 0 ? commitTask : v % 12 == 0 ? rollbackTask : findTask)).flatMap(List::stream).toList());

                try (var executor = Executors.newWorkStealingPool()) {
                    try {
                        executor.invokeAll(tasks);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                assertEquals(1011000, counter.get());
                assertTrue(LongStream.range(counter.get() - 500, counter.get()).anyMatch(v -> Optional.of(v).equals(dao.findOne(SELECT_ID_BY_ID, Long.class, Map.of("id", v)))));
            });
        }
    }
}