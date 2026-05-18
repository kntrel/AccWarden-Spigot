package com.kntrel.mc.accwarden.persistence.sqlite;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SQLiteDatabase implements AutoCloseable {

    private final Connection connection_;
    private final Map<Class<?>, DTODescriptor> descriptors_ = new HashMap<>();
    private final Object lock_ = new Object();

    public SQLiteDatabase(Connection connection) {
        this.connection_ = connection;
    }

    public Connection getConnection() {
        return this.connection_;
    }

    public <T> List<T> query(String sql, Class<T> dtoClass, Object... parameters) throws SQLException {
        return this.queryLimited(sql, dtoClass, -1, parameters);
    }

    public <T> List<T> queryLimited(String sql, Class<T> dtoClass, int limit, Object... parameters) throws SQLException {
        DTODescriptor descriptor = this.getDescriptor(dtoClass);
        List<Object[]> rows = this.queryRowsLimited(sql, dtoClass, limit, parameters);
        return this.buildDTOs(rows, dtoClass, descriptor);
    }

    public <T> Optional<T> queryOne(String sql, Class<T> dtoClass, Object... parameters) throws SQLException {
        List<T> rows = this.queryLimited(sql, dtoClass, 1, parameters);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    public List<Object[]> queryRows(String sql, Class<?> dtoClass, Object... parameters) throws SQLException {
        return this.queryRowsLimited(sql, dtoClass, -1, parameters);
    }

    public List<Object[]> queryRowsLimited(String sql, Class<?> dtoClass, int limit, Object... parameters) throws SQLException {
        DTODescriptor descriptor = this.getDescriptor(dtoClass);
        return this.queryRowsLimited(sql, descriptor.fullProjection(), limit, parameters);
    }

    public List<Object[]> queryColumns(String sql, Class<?> dtoClass, Collection<String> columns, Object... parameters) throws SQLException {
        return this.queryColumnsLimited(sql, dtoClass, columns, -1, parameters);
    }

    public List<Object[]> queryColumnsLimited(
            String sql,
            Class<?> dtoClass,
            Collection<String> columns,
            int limit,
            Object... parameters
    ) throws SQLException {
        DTODescriptor descriptor = this.getDescriptor(dtoClass);
        return this.queryRowsLimited(sql, descriptor.project(columns), limit, parameters);
    }

    public int execute(String sql, Object... parameters) throws SQLException {
        synchronized (this.lock_) {
            try (PreparedStatement statement = this.connection_.prepareStatement(sql)) {
                bind(statement, parameters);
                return statement.executeUpdate();
            }
        }
    }

    public void write(Collection<?> inserts, Collection<?> updates, Collection<?> deletes) throws SQLException {
        if (isEmpty(inserts) && isEmpty(updates) && isEmpty(deletes)) {
            return;
        }

        synchronized (this.lock_) {
            this.inTransaction(() -> {
                this.writeGrouped(inserts, this::insertToTable);
                this.writeGrouped(updates, this::updateTable);
                this.writeGrouped(deletes, this::deleteFromTable);
            });
        }
    }

    public void insert(Collection<?> dtos) throws SQLException {
        this.write(dtos, null, null);
    }

    public void update(Collection<?> dtos) throws SQLException {
        this.write(null, dtos, null);
    }

    public void delete(Collection<?> dtos) throws SQLException {
        this.write(null, null, dtos);
    }

    @Override
    public void close() throws SQLException {
        synchronized (this.lock_) {
            this.connection_.close();
        }
    }

    private DTODescriptor getDescriptor(Class<?> dtoClass) {
        synchronized (this.lock_) {
            return this.descriptors_.computeIfAbsent(dtoClass, DTODescriptor::new);
        }
    }

    private List<Object[]> queryRowsLimited(
            String sql,
            DTODescriptor.Projection projection,
            int limit,
            Object... parameters
    ) throws SQLException {
        synchronized (this.lock_) {
            try (PreparedStatement statement = this.connection_.prepareStatement(sql)) {
                bind(statement, parameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    return this.buildRows(resultSet, projection, limit);
                }
            }
        }
    }

    private List<Object[]> buildRows(ResultSet resultSet, DTODescriptor.Projection projection, int limit) throws SQLException {
        List<DTODescriptor.Column> columns = projection.columns();
        List<Object[]> rows = new ArrayList<>();
        while (resultSet.next()) {
            if (limit >= 0 && rows.size() >= limit) {
                break;
            }

            Object[] values = new Object[columns.size()];
            int index = 0;
            for (DTODescriptor.Column column : columns) {
                values[index++] = SQLiteValueMapper.fromSQLite(resultSet.getObject(column.name()), column);
            }
            rows.add(values);
        }

        return rows;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> buildDTOs(List<Object[]> rows, Class<T> dtoClass, DTODescriptor descriptor) {
        List<T> dtos = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            try {
                dtos.add((T) descriptor.getConstructor().newInstance(row));
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new SQLitePersistenceException("Failed to instantiate DTO of type " + dtoClass.getName(), e);
            }
        }
        return dtos;
    }

    private void writeGrouped(Collection<?> instances, BatchWriter writer) throws SQLException {
        if (isEmpty(instances)) {
            return;
        }

        Map<Class<?>, List<Object>> groups = instances.stream().collect(Collectors.groupingBy(Object::getClass));
        for (Map.Entry<Class<?>, List<Object>> entry : groups.entrySet()) {
            writer.write(this.getDescriptor(entry.getKey()), entry.getValue());
        }
    }

    private void insertToTable(DTODescriptor descriptor, Iterable<?> instances) throws SQLException {
        List<DTODescriptor.Column> columns = descriptor.getColumns();
        try (PreparedStatement statement = this.connection_.prepareStatement(descriptor.getInsertSQL())) {
            for (Object instance : instances) {
                int index = 1;
                for (DTODescriptor.Column column : columns) {
                    statement.setObject(index++, SQLiteValueMapper.toSQLite(readColumn(instance, column)));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void updateTable(DTODescriptor descriptor, Iterable<?> instances) throws SQLException {
        List<DTODescriptor.Column> columns = descriptor.getColumns();
        List<DTODescriptor.Column> idColumns = descriptor.getIdColumns();
        try (PreparedStatement statement = this.connection_.prepareStatement(descriptor.getUpdateSQL())) {
            for (Object instance : instances) {
                int index = 1;
                for (DTODescriptor.Column column : columns) {
                    if (column.id()) {
                        continue;
                    }
                    statement.setObject(index++, SQLiteValueMapper.toSQLite(readColumn(instance, column)));
                }
                for (DTODescriptor.Column idColumn : idColumns) {
                    statement.setObject(index++, SQLiteValueMapper.toSQLite(readColumn(instance, idColumn)));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void deleteFromTable(DTODescriptor descriptor, Iterable<?> instances) throws SQLException {
        List<DTODescriptor.Column> idColumns = descriptor.getIdColumns();
        try (PreparedStatement statement = this.connection_.prepareStatement(descriptor.getDeleteSQL())) {
            for (Object instance : instances) {
                int index = 1;
                for (DTODescriptor.Column idColumn : idColumns) {
                    statement.setObject(index++, SQLiteValueMapper.toSQLite(readColumn(instance, idColumn)));
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void inTransaction(SQLRunnable runnable) throws SQLException {
        boolean originalAutoCommit = this.connection_.getAutoCommit();
        try {
            if (!originalAutoCommit) {
                runnable.run();
                return;
            }
            this.connection_.setAutoCommit(false);
            runnable.run();
            this.connection_.commit();
        } catch (SQLException | RuntimeException e) {
            rollback(e);
            throw e;
        } finally {
            this.connection_.setAutoCommit(originalAutoCommit);
        }
    }

    private void rollback(Exception cause) {
        try {
            this.connection_.rollback();
        } catch (SQLException rollbackException) {
            cause.addSuppressed(rollbackException);
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        if (parameters == null) {
            return;
        }
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, SQLiteValueMapper.toSQLite(parameters[index]));
        }
    }

    private static Object readColumn(Object instance, DTODescriptor.Column column) {
        try {
            return column.accessor().invoke(instance);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new SQLitePersistenceException(
                    "Failed to read DTO column '" + column.name() + "' from " + instance.getClass().getName(),
                    e
            );
        }
    }

    private static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    @FunctionalInterface
    private interface BatchWriter {
        void write(DTODescriptor descriptor, Iterable<?> instances) throws SQLException;
    }

    @FunctionalInterface
    private interface SQLRunnable {
        void run() throws SQLException;
    }
}
