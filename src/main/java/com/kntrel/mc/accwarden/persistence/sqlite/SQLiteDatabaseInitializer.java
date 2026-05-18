package com.kntrel.mc.accwarden.persistence.sqlite;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SQLiteDatabaseInitializer {

    public static final String DEFAULT_MIGRATIONS_PATH = "schema";
    private static final Pattern MIGRATION_FILE = Pattern.compile("v(\\d+)(?:[._-].*)?\\.sql", Pattern.CASE_INSENSITIVE);

    private SQLiteDatabaseInitializer() {}

    public static SQLiteDatabase openDatabase(Plugin plugin, Path databasePath) {
        return openDatabase(plugin, databasePath, DEFAULT_MIGRATIONS_PATH);
    }

    public static SQLiteDatabase openDatabase(Plugin plugin, Path databasePath, String migrationsPath) {
        return new SQLiteDatabase(openConnection(plugin, databasePath, migrationsPath));
    }

    public static Connection openConnection(Plugin plugin, Path databasePath) {
        return openConnection(plugin, databasePath, DEFAULT_MIGRATIONS_PATH);
    }

    public static Connection openConnection(Plugin plugin, Path databasePath, String migrationsPath) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLitePersistenceException("SQLite JDBC driver not found.", e);
        }

        Path normalizedPath = databasePath.toAbsolutePath().normalize();
        try {
            Path parent = normalizedPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + normalizedPath);
            try {
                configure(connection);
                migrate(plugin, connection, migrationsPath);
                return connection;
            } catch (RuntimeException | SQLException e) {
                try {
                    connection.close();
                } catch (SQLException closeException) {
                    e.addSuppressed(closeException);
                }
                throw e;
            }
        } catch (SQLException | IOException e) {
            throw new SQLitePersistenceException("Failed to initialize SQLite database at " + normalizedPath, e);
        }
    }

    public static void migrate(Plugin plugin, Connection connection, String migrationsPath) {
        String resourcePrefix = normalizeResourcePrefix(migrationsPath);
        int currentVersion = readUserVersion(connection);
        SortedMap<Integer, String> pendingMigrations = loadMigrations(plugin, resourcePrefix).tailMap(currentVersion + 1);
        if (pendingMigrations.isEmpty()) {
            return;
        }

        plugin.getLogger().log(Level.INFO, "Database schema is behind. Migrating from v" + currentVersion + ".");
        boolean originalAutoCommit = true;
        try {
            originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            int latestVersion = currentVersion;
            for (Map.Entry<Integer, String> entry : pendingMigrations.entrySet()) {
                latestVersion = entry.getKey();
                plugin.getLogger().log(Level.INFO, "Applying database migration v" + latestVersion + ".");
                runScript(connection, requireResource(plugin, entry.getValue()));
            }
            setUserVersion(connection, latestVersion);
            connection.commit();
        } catch (SQLException | IOException e) {
            rollback(connection, e);
            throw new SQLitePersistenceException("Failed to migrate SQLite database.", e);
        } finally {
            try {
                connection.setAutoCommit(originalAutoCommit);
            } catch (SQLException ignored) {
            }
        }
    }

    static void runScript(Connection connection, URL script) throws IOException, SQLException {
        String sql = new String(script.openStream().readAllBytes(), StandardCharsets.UTF_8);
        for (String statement : splitStatements(sql)) {
            try (Statement sqlStatement = connection.createStatement()) {
                sqlStatement.execute(statement);
            }
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private static SortedMap<Integer, String> loadMigrations(Plugin plugin, String resourcePrefix) {
        try {
            Path codeSource = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
            if (Files.isDirectory(codeSource)) {
                return loadMigrationsFromDirectory(codeSource, resourcePrefix);
            }
            if (Files.isRegularFile(codeSource)) {
                return loadMigrationsFromJar(codeSource, resourcePrefix);
            }
            return new TreeMap<>();
        } catch (IOException | URISyntaxException e) {
            throw new SQLitePersistenceException("Failed to scan SQLite migrations under " + resourcePrefix + ".", e);
        }
    }

    private static SortedMap<Integer, String> loadMigrationsFromDirectory(Path codeSource, String resourcePrefix) throws IOException {
        SortedMap<Integer, String> out = new TreeMap<>();
        Path migrationDirectory = codeSource.resolve(resourcePrefix).normalize();
        if (!Files.isDirectory(migrationDirectory)) {
            return out;
        }

        try (Stream<Path> stream = Files.list(migrationDirectory)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .toList();
            for (Path file : files) {
                registerMigration(out, resourcePrefix + "/" + file.getFileName(), file.getFileName().toString());
            }
        }
        return out;
    }

    private static SortedMap<Integer, String> loadMigrationsFromJar(Path jarPath, String resourcePrefix) throws IOException {
        SortedMap<Integer, String> out = new TreeMap<>();
        String prefix = resourcePrefix + "/";

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName();
                if (!name.startsWith(prefix)) {
                    continue;
                }

                String simpleName = name.substring(prefix.length());
                if (simpleName.contains("/")) {
                    continue;
                }
                registerMigration(out, name, simpleName);
            }
        }

        return out;
    }

    private static void registerMigration(SortedMap<Integer, String> migrations, String resourcePath, String simpleName) {
        Matcher matcher = MIGRATION_FILE.matcher(simpleName);
        if (!matcher.matches()) {
            return;
        }

        int version = Integer.parseInt(matcher.group(1));
        String previous = migrations.put(version, resourcePath);
        if (previous != null) {
            throw new SQLitePersistenceException(
                    "Duplicate SQLite migration version v" + version + ": " + previous + " and " + resourcePath
            );
        }
    }

    private static URL requireResource(Plugin plugin, String resourcePath) throws IOException {
        URL resource = plugin.getClass().getClassLoader().getResource(resourcePath);
        if (resource == null) {
            throw new IOException("Missing SQLite migration resource " + resourcePath);
        }
        return resource;
    }

    private static int readUserVersion(Connection connection) {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("PRAGMA user_version")) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            throw new SQLitePersistenceException("Failed to read SQLite user_version.", e);
        }
    }

    private static void setUserVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = " + version);
        }
    }

    private static void rollback(Connection connection, Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            cause.addSuppressed(rollbackException);
        }
    }

    private static String normalizeResourcePrefix(String migrationsPath) {
        String normalized = migrationsPath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("SQLite migrations path must not be blank.");
        }
        return normalized;
    }

    private static List<String> splitStatements(String script) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean lineComment = false;
        boolean blockComment = false;

        for (int index = 0; index < script.length(); index++) {
            char currentChar = script.charAt(index);
            char nextChar = index + 1 < script.length() ? script.charAt(index + 1) : '\0';

            if (lineComment) {
                if (currentChar == '\n') {
                    lineComment = false;
                    current.append('\n');
                }
                continue;
            }

            if (blockComment) {
                if (currentChar == '*' && nextChar == '/') {
                    blockComment = false;
                    index++;
                }
                continue;
            }

            if (!singleQuoted && !doubleQuoted && currentChar == '-' && nextChar == '-') {
                lineComment = true;
                index++;
                continue;
            }
            if (!singleQuoted && !doubleQuoted && currentChar == '/' && nextChar == '*') {
                blockComment = true;
                index++;
                continue;
            }

            if (currentChar == '\'' && !doubleQuoted) {
                current.append(currentChar);
                if (singleQuoted && nextChar == '\'') {
                    current.append(nextChar);
                    index++;
                    continue;
                }
                singleQuoted = !singleQuoted;
                continue;
            }
            if (currentChar == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                current.append(currentChar);
                continue;
            }

            if (currentChar == ';' && !singleQuoted && !doubleQuoted) {
                addStatement(out, current);
                current.setLength(0);
                continue;
            }

            current.append(currentChar);
        }

        addStatement(out, current);
        return out;
    }

    private static void addStatement(List<String> out, StringBuilder current) {
        String statement = current.toString().trim();
        if (!statement.isEmpty()) {
            out.add(statement);
        }
    }
}
