package com.kntrel.mc.accwarden.persistence.sqlite;

public class SQLitePersistenceException extends RuntimeException {

    public SQLitePersistenceException(String message) {
        super(message);
    }

    public SQLitePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
