package com.kntrel.mc.accwarden.persistence.sqlite;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

final class DTODescriptor {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Map<Class<?>, Class<?>> WRAPPERS = Map.ofEntries(
            Map.entry(int.class, Integer.class),
            Map.entry(long.class, Long.class),
            Map.entry(double.class, Double.class),
            Map.entry(float.class, Float.class),
            Map.entry(boolean.class, Boolean.class),
            Map.entry(byte.class, Byte.class),
            Map.entry(char.class, Character.class),
            Map.entry(short.class, Short.class)
    );

    record Column(int index, String name, Class<?> type, boolean primitive, Method accessor, boolean id) {
        Class<?> boxedType() {
            return WRAPPERS.getOrDefault(this.type, this.type);
        }

        String sqlName() {
            return quoteIdentifier(this.name);
        }
    }

    record Projection(DTODescriptor descriptor, List<Column> columns) {
        Projection {
            columns = List.copyOf(columns);
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("Projection must contain at least one column.");
            }
        }
    }

    private final Class<?> dtoClass_;
    private final String tableName_;
    private final Constructor<?> constructor_;
    private final List<Column> columns_;
    private final Map<String, Column> columnMap_;
    private final List<Column> idColumns_;
    private final Projection fullProjection_;
    private final String insertSQL_;
    private final Optional<String> updateSQL_;
    private final Optional<String> deleteSQL_;

    DTODescriptor(Class<?> dtoClass) {
        this.ensureRecord(dtoClass);
        this.dtoClass_ = dtoClass;
        this.tableName_ = findTableName(dtoClass);
        this.constructor_ = findCanonicalConstructor(dtoClass);
        this.columns_ = findColumns(dtoClass, this.constructor_);
        this.columnMap_ = indexColumns(this.columns_);
        this.idColumns_ = this.columns_.stream().filter(Column::id).toList();
        this.fullProjection_ = new Projection(this, this.columns_);
        this.insertSQL_ = buildInsert(this.tableName_, this.columns_);
        this.updateSQL_ = buildUpdate(this.tableName_, this.columns_, this.idColumns_);
        this.deleteSQL_ = buildDelete(this.tableName_, this.idColumns_);
    }

    Class<?> getType() {
        return this.dtoClass_;
    }

    Constructor<?> getConstructor() {
        return this.constructor_;
    }

    List<Column> getColumns() {
        return this.columns_;
    }

    List<Column> getIdColumns() {
        return this.idColumns_;
    }

    Projection fullProjection() {
        return this.fullProjection_;
    }

    Projection project(String... columnNames) {
        return this.project(Arrays.asList(columnNames));
    }

    Projection project(Collection<String> columnNames) {
        List<Column> columns = new ArrayList<>();
        for (String columnName : columnNames) {
            Column column = this.columnMap_.get(columnName);
            if (column == null) {
                throw new IllegalArgumentException("Unknown column '" + columnName + "' for DTO " + this.dtoClass_.getName());
            }
            columns.add(column);
        }
        return new Projection(this, columns);
    }

    String getInsertSQL() {
        return this.insertSQL_;
    }

    String getUpdateSQL() {
        return this.updateSQL_.orElseThrow(() ->
                new IllegalStateException("DTO " + this.dtoClass_.getName() + " has no updatable non-ID columns.")
        );
    }

    String getDeleteSQL() {
        return this.deleteSQL_.orElseThrow(() ->
                new IllegalStateException("DTO " + this.dtoClass_.getName() + " has no ID columns.")
        );
    }

    private void ensureRecord(Class<?> dtoClass) {
        if (!dtoClass.isRecord()) {
            throw new IllegalArgumentException("DTO class must be a record type: " + dtoClass.getName());
        }
        if (dtoClass.getRecordComponents().length == 0) {
            throw new IllegalArgumentException("DTO record must define at least one component: " + dtoClass.getName());
        }
    }

    private static String findTableName(Class<?> dtoClass) {
        DTO.Table table = dtoClass.getAnnotation(DTO.Table.class);
        String name = table == null || table.value().isBlank() ? dtoClass.getSimpleName() : table.value();
        return requireIdentifier(name, "table name for " + dtoClass.getName());
    }

    private static Constructor<?> findCanonicalConstructor(Class<?> dtoClass) {
        RecordComponent[] components = dtoClass.getRecordComponents();
        Class<?>[] parameterTypes = Arrays.stream(components).map(RecordComponent::getType).toArray(Class[]::new);
        try {
            Constructor<?> constructor = dtoClass.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Unable to find canonical constructor for DTO " + dtoClass.getName(), e);
        }
    }

    private static List<Column> findColumns(Class<?> dtoClass, Constructor<?> constructor) {
        RecordComponent[] components = dtoClass.getRecordComponents();
        Parameter[] parameters = constructor.getParameters();
        List<Column> out = new ArrayList<>();

        for (int index = 0; index < components.length; index++) {
            RecordComponent component = components[index];
            Parameter parameter = parameters[index];
            DTO.Column columnAnnotation = Optional
                    .ofNullable(component.getAnnotation(DTO.Column.class))
                    .orElse(parameter.getAnnotation(DTO.Column.class));
            String columnName = columnAnnotation == null || columnAnnotation.value().isBlank()
                    ? component.getName()
                    : columnAnnotation.value();
            Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            out.add(new Column(
                    index,
                    requireIdentifier(columnName, "column name for " + dtoClass.getName() + "." + component.getName()),
                    component.getType(),
                    component.getType().isPrimitive(),
                    accessor,
                    component.isAnnotationPresent(DTO.Id.class) || parameter.isAnnotationPresent(DTO.Id.class)
            ));
        }

        return List.copyOf(out);
    }

    private static Map<String, Column> indexColumns(List<Column> columns) {
        Map<String, Column> out = new HashMap<>();
        for (Column column : columns) {
            Column previous = out.put(column.name(), column);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate DTO column '" + column.name() + "'.");
            }
        }
        return Map.copyOf(out);
    }

    private static Optional<String> buildUpdate(String tableName, List<Column> columns, List<Column> idColumns) {
        if (idColumns.isEmpty()) {
            return Optional.empty();
        }

        List<Column> nonIdColumns = columns.stream().filter(column -> !column.id()).toList();
        if (nonIdColumns.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(
                "UPDATE " + quoteIdentifier(tableName)
                        + " SET " + String.join(", ", nonIdColumns.stream().map(column -> column.sqlName() + " = ?").toList())
                        + " WHERE " + String.join(" AND ", idColumns.stream().map(column -> column.sqlName() + " = ?").toList())
                        + ";"
        );
    }

    private static String buildInsert(String tableName, List<Column> columns) {
        return "INSERT INTO " + quoteIdentifier(tableName)
                + "(" + String.join(", ", columns.stream().map(Column::sqlName).toList()) + ")"
                + " VALUES (" + String.join(", ", Collections.nCopies(columns.size(), "?")) + ");";
    }

    private static Optional<String> buildDelete(String tableName, List<Column> idColumns) {
        if (idColumns.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                "DELETE FROM " + quoteIdentifier(tableName)
                        + " WHERE " + String.join(" AND ", idColumns.stream().map(column -> column.sqlName() + " = ?").toList())
                        + ";"
        );
    }

    private static String requireIdentifier(String identifier, String label) {
        if (!IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Invalid SQLite identifier '" + identifier + "' for " + label + ".");
        }
        return identifier;
    }

    private static String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }
}
