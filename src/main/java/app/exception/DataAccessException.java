package app.exception;

/**
 * データアクセス層の例外
 */
public class DataAccessException extends AppException {
    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}