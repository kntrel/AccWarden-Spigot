package com.kntrel.mc.accwarden.io.database;

import org.sqlite.SQLiteDataSource;
import org.sqlite.javax.SQLiteConnectionPoolDataSource;
import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DataBase {
    //ASSETS
    private record EntityEntry <T extends Enitty> (Class<T> type, DataBaseParser<T> dataBaseParser, String tableName) {}

    //FIELDS
    private final Set<EntityEntry<?>> entities_ = new HashSet<>();
    private String filePath_ = "./database.db";
    private final SQLiteDataSource dataSource_ = new SQLiteConnectionPoolDataSource();
    private Logger logger_ = null;

    //CONSTRUCTOR
    public DataBase() {}

    //SETTERS
    public void setFilePath(String path) {
        this.filePath_ = path;
    }
    public void setLogger(Logger logger) {
        this.logger_ = logger;
    }

    //GETTERS
    public String getFilePath() {
        return this.filePath_;
    }

    //METHODS
    public void setUp() {
        File file = new File(this.getFilePath());
        if (!file.exists()) { file.getParentFile().mkdirs(); }
        this.dataSource_.setUrl("jdbc:sqlite:" + this.getFilePath());

        try (Connection conn = this.dataSource_.getConnection()) {
            if (!conn.isValid(1)) {
                throw new SQLException("Could not establish database connection.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public <T extends Enitty> void addEntity(Class<T> type, DataBaseParser<T> dataBaseParser, String tableName) {
        String tableExistsQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name= ?";
        try (Connection conn = this.dataSource_.getConnection(); PreparedStatement stm = conn.prepareStatement(tableExistsQuery)) {
            stm.setString(1,tableName);
            ResultSet resultSet = stm.executeQuery();
            if (!resultSet.next()) {
                throw new NoSuchElementException("No such table found called '" + tableName + "' in the database.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        this.entities_.add(new EntityEntry<>(type, dataBaseParser,tableName));
    }
    public void executeSQL(String sql) throws SQLException {
        this.log_("Running SQL: " + sql);
        try (Connection conn = this.dataSource_.getConnection(); Statement stm = conn.createStatement()) {
            stm.execute(sql);
        }
    }
    public <T extends Enitty> Optional<T> get(Class<T> type, Object id) {
        EntityEntry<T> entityEntry = this.getEntity_(type);

        try (Connection conn = this.dataSource_.getConnection()) {
            String primaryKey = this.getPrimaryKey_(entityEntry, conn);
            return this.query(type,primaryKey,id).stream().findFirst();
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
    public <T extends Enitty> Set<T> query(Class<T> type, String column, Object value) {
        HashSet<T> set = new HashSet<>();
        EntityEntry<T> entityEntry = this.getEntity_(type);

        try (
                Connection conn = this.dataSource_.getConnection();
                PreparedStatement stm = conn.prepareStatement("SELECT * FROM " + entityEntry.tableName() + " WHERE " + column + " = ?");
        ) {
            stm.setObject(1, value);
            this.log_("Executing SQL: " + stm);
            ResultSet resultSet = stm.executeQuery();
            while (resultSet.next()) {
                set.add(entityEntry.dataBaseParser().toEntity(resultSet));
            }
            return set;
        } catch (SQLException ex) {
            ex.printStackTrace();
            return set;
        }
    }
    public <T extends Enitty> Set<T> query(Class<T> type, int limit) {
        HashSet<T> set = new HashSet<>();
        EntityEntry<T> entityEntry = this.getEntity_(type);

        try (
                Connection conn = this.dataSource_.getConnection();
                PreparedStatement stm = conn.prepareStatement("SELECT * FROM " + entityEntry.tableName() + " LIMIT ?");
        ) {
            stm.setInt(1, limit);
            this.log_("Executing SQL: " + stm);
            ResultSet resultSet = stm.executeQuery();
            while (resultSet.next()) {
                set.add(entityEntry.dataBaseParser().toEntity(resultSet));
            }
            return set;
        } catch (SQLException ex) {
            ex.printStackTrace();
            return set;
        }
    }
    public <T extends Enitty> void write(T entity) {
        try (Connection conn = this.dataSource_.getConnection()) {
            EntityEntry<T> entityEntry = (EntityEntry<T>) this.getEntity_(entity.getClass());
            String tableName = entityEntry.tableName();
            String primaryKey = this.getPrimaryKey_(entityEntry, conn);
            Set<Map.Entry<String, Object>> parsingMap = entityEntry.dataBaseParser().toRow(entity).entrySet();

            String insertStatement = "INSERT OR IGNORE INTO " + tableName + " (" + primaryKey + ") VALUES (?)";
            StringBuilder updateStatement = new StringBuilder();
            updateStatement.append("UPDATE ").append(tableName).append(" SET ");
            Iterator<Map.Entry<String,Object>> iterator = parsingMap.iterator();

            while (iterator.hasNext()) {
                Map.Entry<String,Object> entry = iterator.next();
                updateStatement.append(entry.getKey()).append("=").append("?");
                if (iterator.hasNext()) { updateStatement.append(", "); }
            }
            updateStatement.append(" WHERE ").append(primaryKey).append("=?");

            try (
                    PreparedStatement stmInsert = conn.prepareStatement(insertStatement);
                    PreparedStatement stmUpdate = conn.prepareStatement(updateStatement.toString());
            ) {

                stmInsert.setObject(1, entity.getId());
                this.log_("Executing SQL: " + stmInsert);
                stmInsert.executeUpdate();

                iterator = parsingMap.iterator();
                int i = 1;

                while (iterator.hasNext()) {
                    Map.Entry<String, Object> entry = iterator.next();
                    stmUpdate.setObject(i, entry.getValue());
                    i++;
                }

                stmUpdate.setObject(i, entity.getId());
                this.log_("Executing SQL: " + stmUpdate);
                stmUpdate.executeUpdate();
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
    public <T extends Enitty> void delete(T entity) {
        EntityEntry<T> entityEntry = (EntityEntry<T>) this.getEntity_(entity.getClass());
        try (
                Connection conn = this.dataSource_.getConnection()
        ) {
            String primaryKey = this.getPrimaryKey_(entityEntry,conn);
            PreparedStatement stm = conn.prepareStatement("DELETE FROM " + entityEntry.tableName() + " WHERE " + primaryKey + " = ?");
            stm.setObject(1, entity.getId());
            this.log_("Executing SQL: " + stm);
            stm.execute();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    //PRIVATE METHODS
    private <T extends Enitty> EntityEntry<T> getEntity_(Class<T> type) {
        Function<Predicate<Class<?>>,EntityEntry<T>> entitySupplier = (p) -> (EntityEntry<T>) this.entities_.stream()
                .filter(e -> p.test(e.type()))
                .findFirst().orElse(null);

        EntityEntry<T> entity = entitySupplier.apply(type::equals);
        if (entity != null) return entity;
        entity = entitySupplier.apply(t -> t.isAssignableFrom(type));
        if (entity != null) return entity;

        throw new NoSuchElementException("There's no Entity defined for '" + type.getName() + "'. Unable to query database");
    }
    private void log_(String log) {
        if (this.logger_ == null) { return; }
        this.logger_.log(Level.INFO,"[Database] " + log);
    }
    private String getPrimaryKey_(EntityEntry<?> entry, Connection connection) throws SQLException {
        try (Statement stm = connection.createStatement();) {
            ResultSet rs = stm.executeQuery("SELECT name FROM pragma_table_info ('"+ entry.tableName() +"') WHERE pk = 1");
            rs.next();
            return rs.getString("name");
        }
    }
}
