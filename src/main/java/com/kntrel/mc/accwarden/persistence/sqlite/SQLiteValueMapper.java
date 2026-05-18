package com.kntrel.mc.accwarden.persistence.sqlite;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

final class SQLiteValueMapper {

    private SQLiteValueMapper() {
    }

    static Object toSQLite(Object value) {
        return switch (value) {
            case null -> null;
            case UUID uuid -> uuid.toString();
            case Enum<?> enumValue -> enumValue.name();
            case Boolean boolValue -> boolValue ? 1 : 0;
            case LocalDateTime dateTime -> Timestamp.valueOf(dateTime);
            case LocalDate date -> Date.valueOf(date);
            case Instant instant -> Timestamp.from(instant);
            default -> value;
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object fromSQLite(Object value, DTODescriptor.Column column) throws SQLException {
        if (value == null) {
            if (column.primitive()) {
                throw new SQLException("Column '" + column.name() + "' returned NULL for primitive type " + column.type().getName());
            }
            return null;
        }

        Class<?> target = column.boxedType();
        if (target.isInstance(value)) {
            return value;
        }

        if (target == String.class) {
            return String.valueOf(value);
        }
        if (target == UUID.class) {
            return UUID.fromString(String.valueOf(value));
        }
        if (target == boolean.class || target == Boolean.class) {
            return toBoolean(value, column);
        }
        if (target == LocalDateTime.class) {
            return toLocalDateTime(value, column);
        }
        if (target == LocalDate.class) {
            return toLocalDate(value, column);
        }
        if (target == Instant.class) {
            return toInstant(value, column);
        }
        if (target.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) target.asSubclass(Enum.class), String.valueOf(value));
        }
        if (value instanceof Number number && Number.class.isAssignableFrom(target)) {
            return toNumber(number, target, column);
        }

        throw new SQLException(
                "Unable to convert SQLite value for column '" + column.name() + "' from "
                        + value.getClass().getName() + " to " + target.getName()
        );
    }

    private static Boolean toBoolean(Object value, DTODescriptor.Column column) throws SQLException {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof String string) {
            String normalized = string.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "1", "true", "yes", "y" -> true;
                case "0", "false", "no", "n" -> false;
                default -> throw new SQLException("Unable to convert column '" + column.name() + "' value '" + value + "' to boolean.");
            };
        }
        throw new SQLException("Unable to convert column '" + column.name() + "' to boolean from " + value.getClass().getName());
    }

    private static LocalDateTime toLocalDateTime(Object value, DTODescriptor.Column column) throws SQLException {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof String string) {
            return LocalDateTime.parse(string.replace(' ', 'T'));
        }
        throw new SQLException("Unable to convert column '" + column.name() + "' to LocalDateTime from " + value.getClass().getName());
    }

    private static LocalDate toLocalDate(Object value, DTODescriptor.Column column) throws SQLException {
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        if (value instanceof String string) {
            return LocalDate.parse(string);
        }
        throw new SQLException("Unable to convert column '" + column.name() + "' to LocalDate from " + value.getClass().getName());
    }

    private static Instant toInstant(Object value, DTODescriptor.Column column) throws SQLException {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof String string) {
            return Instant.parse(string);
        }
        throw new SQLException("Unable to convert column '" + column.name() + "' to Instant from " + value.getClass().getName());
    }

    private static Object toNumber(Number number, Class<?> target, DTODescriptor.Column column) throws SQLException {
        if (target == Integer.class) {
            return number.intValue();
        }
        if (target == Long.class) {
            return number.longValue();
        }
        if (target == Double.class) {
            return number.doubleValue();
        }
        if (target == Float.class) {
            return number.floatValue();
        }
        if (target == Short.class) {
            return number.shortValue();
        }
        if (target == Byte.class) {
            return number.byteValue();
        }
        throw new SQLException("Unsupported numeric target for column '" + column.name() + "': " + target.getName());
    }
}
