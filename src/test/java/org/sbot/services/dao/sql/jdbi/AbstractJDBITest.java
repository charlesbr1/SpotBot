package org.sbot.services.dao.sql.jdbi;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.generic.GenericType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.jdbi.v3.core.statement.Update;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.sbot.utils.DatesTest;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.sbot.services.context.TransactionalContext.DEFAULT_ISOLATION_LEVEL;
import static org.sbot.services.dao.sql.jdbi.JDBIRepositoryTest.SQLITE_MEMORY_VOLATILE;

class AbstractJDBITest {

    static final class TestAbstractJDBI extends AbstractJDBI {
        Handle setupTable;

        TestAbstractJDBI(@NotNull JDBIRepository repository, @NotNull RowMapper<?> rowMapper) {
            super(repository, rowMapper);
        }

        TestAbstractJDBI(@NotNull AbstractJDBI abstractJDBI, @NotNull JDBITransactionHandler transactionHandler) {
            super(abstractJDBI, transactionHandler);
        }

        @Override
        protected void setupTable(@NotNull Handle handle) {
            setupTable = handle;
        }

        @Override
        protected AbstractJDBI withHandler(@NotNull JDBITransactionHandler transactionHandler) {
            return this;
        }
    }

    static final class RowMapperTest implements RowMapper<Long> {
        @Override
        public Long map(ResultSet rs, StatementContext ctx) throws SQLException {
            return rs.getLong(1);
        }
    }

    @Test
    void constructors() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var rowMapper = new RowMapperTest();
        assertDoesNotThrow(() -> new TestAbstractJDBI(new TestAbstractJDBI(repository, rowMapper), mock(JDBITransactionHandler.class)));
        verify(repository).registerRowMapper(eq(rowMapper));
        verify(repository).inTransaction(any(HandleCallback.class));
        assertThrows(NullPointerException.class, () -> new TestAbstractJDBI(mock(AbstractJDBI.class), null));
        assertThrows(NullPointerException.class, () -> new TestAbstractJDBI(null, mock(JDBITransactionHandler.class)));

        assertThrows(NullPointerException.class, () -> new TestAbstractJDBI(repository, null));
        assertThrows(NullPointerException.class, () -> new TestAbstractJDBI(null, rowMapper));
        doAnswer(a -> a.getArgument(0, HandleCallback.class).withHandle(mock(Handle.class)))
                .when(repository).inTransaction(any());
        assertNotNull(new TestAbstractJDBI(repository, rowMapper).setupTable);
        verify(repository, times(2)).registerRowMapper(eq(rowMapper));
        verify(repository, times(2)).inTransaction(any(HandleCallback.class));
    }

    @Test
    void inTransaction() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var rowMapper = new RowMapperTest();
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, rowMapper), mock(JDBITransactionHandler.class));
        assertThrows(IllegalStateException.class, () -> abstractJdbi.inTransaction(v -> 1L));

        assertDoesNotThrow(() -> new TestAbstractJDBI(repository, rowMapper));
        assertThrows(NullPointerException.class, () -> new TestAbstractJDBI(repository, rowMapper).inTransaction(null));
        boolean[] run = new boolean[1];
        new TestAbstractJDBI(repository, rowMapper).inTransaction(v -> run[0] = true);
        assertTrue(run[0]);
    }

    @Test
    void findOneLong() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);
        var handle = transactionHandler.sync(repository.jdbi, h -> h);
        verify(transactionHandler, times(1)).sync(any(), any());

        assertThrows(NullPointerException.class, () -> abstractJdbi.findOneLong(null, "sql", emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.findOneLong(handle, null, emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.findOneLong(handle, "sql", null));

        doReturn(Optional.of(1L)).when(repository).findOneLong(eq(handle), eq("sql"), anyMap());
        assertDoesNotThrow(() -> abstractJdbi.findOneLong(handle, "sql", emptyMap()));
        verify(transactionHandler, times(1)).sync(any(), any());
        verify(repository).findOneLong(eq(handle), eq("sql"), anyMap());
    }

    @Test
    void update() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);

        // update map parameters
        assertThrows(NullPointerException.class, () -> abstractJdbi.update(null, Collections.emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.update("sql", (Map) null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doReturn(1).when(repository).update(any(Handle.class), eq("sql"), anyMap());
        assertDoesNotThrow(() -> abstractJdbi.update("sql", Collections.emptyMap()));
        verify(transactionHandler).sync(eq(repository.jdbi), any());
        verify(repository).update(any(Handle.class), eq("sql"), anyMap());

        // update mapper parameters
        assertThrows(NullPointerException.class, () -> abstractJdbi.update(null, u -> {}));
        assertThrows(NullPointerException.class, () -> abstractJdbi.update("sql", (Consumer<Update>) null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doReturn(1).when(repository).update(any(Handle.class), eq("sql"), any(Consumer.class));
        assertDoesNotThrow(() -> abstractJdbi.update("sql", u -> {}));
        verify(transactionHandler, times(2)).sync(eq(repository.jdbi), any());
        verify(repository).update(any(Handle.class), eq("sql"), any(Consumer.class));
    }

    @Test
    void query() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);

        assertThrows(NullPointerException.class, () -> abstractJdbi.query(null, Long.class, Collections.emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.query("sql", null, Collections.emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.query("sql", Long.class, null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doReturn(emptyList()).when(repository).query(any(Handle.class), eq("sql"), eq(Long.class), anyMap());
        assertDoesNotThrow(() -> abstractJdbi.query("sql", Long.class, Collections.emptyMap()));
        verify(transactionHandler).sync(eq(repository.jdbi), any());
        verify(repository).query(any(Handle.class), eq("sql"), eq(Long.class), anyMap());
    }

    @Test
    void queryMap() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);
        var type = new GenericType<Map<String, String>>() {};

        assertThrows(NullPointerException.class, () -> abstractJdbi.queryMap(null, type, v -> {}, "key", "value"));
        assertThrows(NullPointerException.class, () -> abstractJdbi.queryMap("sql", null, v -> {}, "key", "value"));
        assertThrows(NullPointerException.class, () -> abstractJdbi.queryMap("sql", type, null, "key", "value"));
        assertThrows(NullPointerException.class, () -> abstractJdbi.queryMap("sql", type, v -> {}, null, "value"));
        assertThrows(NullPointerException.class, () -> abstractJdbi.queryMap("sql", type, v -> {}, "key", null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doReturn(emptyMap()).when(repository).queryMap(any(Handle.class), eq("sql"), eq(type), any(Consumer.class), eq("key"), eq("value"));
        assertDoesNotThrow(() -> abstractJdbi.queryMap("sql", type, v -> {}, "key", "value"));
        verify(transactionHandler).sync(eq(repository.jdbi), any());
        verify(repository).queryMap(any(Handle.class), eq("sql"), eq(type), any(Consumer.class), eq("key"), eq("value"));
    }

    @Test
    void queryCollect() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);

        assertThrows(NullPointerException.class, () -> abstractJdbi.queryCollect(null, emptyMap(), groupingBy(
                rowView -> rowView.getColumn("a", String.class),
                mapping(rowView -> rowView.getColumn("b", String.class), toSet()))));
        assertThrows(NullPointerException.class, () -> abstractJdbi.queryCollect("sql", null, groupingBy(
                rowView -> rowView.getColumn("a", String.class),
                mapping(rowView -> rowView.getColumn("b", String.class), toSet()))));
        assertThrows(NullPointerException.class, () -> abstractJdbi.queryCollect(null, emptyMap(), null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doReturn(emptyMap()).when(repository).queryCollect(any(Handle.class), eq("sql"), anyMap(), any(Collector.class));
        assertDoesNotThrow(() -> abstractJdbi.queryCollect("sql", emptyMap(), groupingBy(
                rowView -> rowView.getColumn("a", String.class),
                mapping(rowView -> rowView.getColumn("b", String.class), toSet()))));
        verify(transactionHandler).sync(eq(repository.jdbi), any());
        verify(repository).queryCollect(any(Handle.class), eq("sql"), anyMap(), any(Collector.class));
    }

    @Test
    void queryOne() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);

        assertThrows(NullPointerException.class, () -> abstractJdbi.queryOne(null, Long.class, emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.queryOne("sql", null, emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.queryOne("sql", Long.class, null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doReturn(1L).when(repository).queryOne(any(Handle.class), eq("sql"), eq(Long.class), anyMap());
        assertDoesNotThrow(() -> abstractJdbi.queryOne("sql", Long.class, emptyMap()));
        verify(transactionHandler).sync(eq(repository.jdbi), any());
        verify(repository).queryOne(any(Handle.class), eq("sql"), eq(Long.class), anyMap());
    }

    @Test
    void queryOneLong() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);

        assertThrows(NullPointerException.class, () -> abstractJdbi.queryOneLong(null, emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.queryOneLong("sql", null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doReturn(1L).when(repository).queryOneLong(any(Handle.class), eq("sql"), anyMap());
        assertDoesNotThrow(() -> abstractJdbi.queryOneLong("sql", emptyMap()));
        verify(transactionHandler).sync(eq(repository.jdbi), any());
        verify(repository).queryOneLong(any(Handle.class), eq("sql"), anyMap());
    }

    @Test
    void fetch() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);
        var streamConsumer = mock(Consumer.class);

        assertThrows(NullPointerException.class, () -> abstractJdbi.fetch(null, Long.class, emptyMap(), streamConsumer));
        assertThrows(NullPointerException.class, () -> abstractJdbi.fetch("sql", null, emptyMap(), streamConsumer));
        assertThrows(NullPointerException.class, () -> abstractJdbi.fetch("sql", Long.class, null, streamConsumer));
        assertThrows(NullPointerException.class, () -> abstractJdbi.fetch("sql", Long.class, emptyMap(), null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doReturn(1L).when(repository).fetch(any(Handle.class), eq("sql"), eq(Long.class), anyMap(), eq(streamConsumer));
        assertDoesNotThrow(() -> abstractJdbi.fetch("sql", Long.class, emptyMap(), streamConsumer));
        verify(transactionHandler).sync(eq(repository.jdbi), any());
        verify(repository).fetch(any(Handle.class), eq("sql"), eq(Long.class), anyMap(), eq(streamConsumer));
    }

    @Test
    void findOne() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);

        assertThrows(NullPointerException.class, () -> abstractJdbi.findOne(null, Long.class, emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.findOne("sql", null, emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.findOne("sql", Long.class, null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doReturn(Optional.of(1L)).when(repository).findOne(any(Handle.class), eq("sql"), eq(Long.class), anyMap());
        assertDoesNotThrow(() -> abstractJdbi.findOne("sql", Long.class, emptyMap()));
        verify(transactionHandler).sync(eq(repository.jdbi), any());
        verify(repository).findOne(any(Handle.class), eq("sql"), eq(Long.class), anyMap());
    }

    @Test
    void findOneDateTime() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);

        assertThrows(NullPointerException.class, () -> abstractJdbi.findOneDateTime(null, emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.findOneDateTime("sql", null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doReturn(Optional.of(DatesTest.nowUtc())).when(repository).findOneDateTime(any(Handle.class), eq("sql"), anyMap());
        assertDoesNotThrow(() -> abstractJdbi.findOneDateTime("sql", emptyMap()));
        verify(transactionHandler).sync(eq(repository.jdbi), any());
        verify(repository).findOneDateTime(any(Handle.class), eq("sql"), anyMap());
    }

    @Test
    void batchUpdates() {
        var repository = spy(new JDBIRepository(SQLITE_MEMORY_VOLATILE));
        var transactionHandler = spy(new JDBITransactionHandler(DEFAULT_ISOLATION_LEVEL));
        var abstractJdbi = new TestAbstractJDBI(new TestAbstractJDBI(repository, new RowMapperTest()), transactionHandler);

        assertThrows(NullPointerException.class, () -> abstractJdbi.batchUpdates(null, "sql", emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.batchUpdates(mock(Consumer.class), null, emptyMap()));
        assertThrows(NullPointerException.class, () -> abstractJdbi.batchUpdates(mock(Consumer.class), "sql", null));

        doAnswer(a -> a.getArgument(1, Function.class).apply(mock(Handle.class)))
                .when(transactionHandler).sync(eq(repository.jdbi), any());
        doNothing().when(repository).batchUpdates(any(Handle.class), any(Consumer.class), eq("sql"), anyMap());
        assertDoesNotThrow(() -> abstractJdbi.batchUpdates(mock(Consumer.class), "sql", emptyMap()));
        verify(transactionHandler).sync(eq(repository.jdbi), any());
        verify(repository).batchUpdates(any(Handle.class), any(Consumer.class), eq("sql"), anyMap());
    }
}