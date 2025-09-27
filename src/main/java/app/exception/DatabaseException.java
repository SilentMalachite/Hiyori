package app.exception;

/**
 * データベース操作に関する例外
 */
public class DatabaseException extends AppException {
    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}