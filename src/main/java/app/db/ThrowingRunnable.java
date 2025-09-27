package app.db;

import app.exception.DataAccessException;

@FunctionalInterface
public interface ThrowingRunnable {
    void run() throws DataAccessException;
}