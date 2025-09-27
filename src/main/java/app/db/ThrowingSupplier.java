package app.db;

import app.exception.DataAccessException;

@FunctionalInterface
public interface ThrowingSupplier<T> {
    T get() throws DataAccessException;
}