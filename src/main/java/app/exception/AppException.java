package app.exception;

/**
 * アプリケーションの基底例外クラス
 */
public class AppException extends Exception {
    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}