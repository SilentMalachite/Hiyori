package app.db;

import app.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * データベーストランザクション管理クラス
 */
public class TransactionManager {
    private static final Logger logger = LoggerFactory.getLogger(TransactionManager.class);
    private final Database database;
    private final ReentrantLock writeLock = new ReentrantLock(true);
    private final ThreadLocal<TxContext> contextHolder = new ThreadLocal<>();

    public TransactionManager(Database database) {
        this.database = database;
    }

    /**
     * Check if currently in a transaction
     * @return true if in a transaction, false otherwise
     */
    public boolean isInTransaction() {
        TxContext ctx = contextHolder.get();
        return ctx != null && ctx.depth > 0;
    }

    /**
     * Get current transaction connection
     * @return current connection if in transaction
     */
    public Connection getCurrentConnection() throws DataAccessException {
        TxContext ctx = contextHolder.get();
        if (ctx == null || ctx.connection == null) {
            throw new DataAccessException("No transaction connection available");
        }
        return ctx.connection;
    }

    /**
     * トランザクション内で処理を実行する
     * @param operation 実行する処理
     * @param <T> 戻り値の型
     * @return 処理の結果
     * @throws DataAccessException 処理中にエラーが発生した場合
     */
    public <T> T executeInTransaction(ThrowingSupplier<T> operation) throws DataAccessException {
        TxContext ctx = contextHolder.get();
        boolean newContext = false;
        
        if (ctx == null) {
            ctx = new TxContext();
            contextHolder.set(ctx);
            newContext = true;
        }
        
        boolean outermost = ctx.depth == 0;
        if (outermost) {
            try {
                writeLock.lock();
                ctx.connection = database.getConnection();
                ctx.originalAutoCommit = ctx.connection.getAutoCommit();
                ctx.connection.setAutoCommit(false);
            } catch (SQLException e) {
                writeLock.unlock();
                throw new DataAccessException("トランザクションの開始に失敗しました", e);
            } catch (app.exception.DatabaseException e) {
                writeLock.unlock();
                throw new DataAccessException("データベース接続の取得に失敗しました", e);
            }
        }
        
        ctx.depth++;
        
        T result = null;
        DataAccessException failure = null;
        try {
            result = operation.get();
        } catch (DataAccessException e) {
            ctx.rollbackOnly = true;
            failure = e;
        } catch (Exception e) {
            ctx.rollbackOnly = true;
            failure = new DataAccessException("トランザクションの実行に失敗しました", e);
        } finally {
            ctx.depth--;
            if (outermost) {
                try {
                    if (ctx.rollbackOnly || failure != null) {
                        ctx.connection.rollback();
                        logger.debug("Transaction rolled back");
                    } else {
                        ctx.connection.commit();
                        logger.debug("Transaction committed successfully");
                    }
                } catch (SQLException e) {
                    DataAccessException ex = new DataAccessException(
                        ctx.rollbackOnly ? "トランザクションのロールバックに失敗しました" : "トランザクションのコミットに失敗しました", e);
                    if (failure != null) {
                        failure.addSuppressed(ex);
                    } else {
                        failure = ex;
                    }
                } finally {
                    try {
                        if (ctx.connection != null) {
                            ctx.connection.setAutoCommit(ctx.originalAutoCommit);
                        }
                    } catch (SQLException e) {
                        DataAccessException ex = new DataAccessException("自動コミット設定の復元に失敗しました", e);
                        if (failure != null) {
                            failure.addSuppressed(ex);
                        } else {
                            failure = ex;
                        }
                    } finally {
                        if (ctx.connection != null) {
                            database.releaseConnection(ctx.connection);
                        }
                        ctx.reset();
                        writeLock.unlock();
                        if (newContext) {
                            contextHolder.remove();
                        }
                    }
                }
            }
        }

        if (failure != null) throw failure;
        return result;
    }

    /**
     * トランザクション内で処理を実行する（戻り値なし）
     * @param operation 実行する処理
     * @throws DataAccessException 処理中にエラーが発生した場合
     */
    public void executeInTransaction(ThrowingRunnable operation) throws DataAccessException {
        executeInTransaction(() -> {
            operation.run();
            return null;
        });
    }

    /**
     * 読み取り専用トランザクション内で処理を実行する
     * @param operation 実行する処理
     * @param <T> 戻り値の型
     * @return 処理の結果
     * @throws DataAccessException 処理中にエラーが発生した場合
     */
    public <T> T executeInReadOnlyTransaction(ThrowingSupplier<T> operation) throws DataAccessException {
        TxContext current = contextHolder.get();
        if (current != null && current.depth > 0) {
            return executeInTransaction(operation);
        }

        TxContext readContext = new TxContext();
        Connection conn = null;
        try {
            conn = database.getConnection();
            readContext.connection = conn;
            readContext.originalAutoCommit = conn.getAutoCommit();
            readContext.depth = 1;
            contextHolder.set(readContext);

            // SQLite JDBC does not support toggling read-only after connection creation.
            // For compatibility across drivers, we skip setReadOnly here.
            return operation.get();
        } catch (app.exception.DatabaseException e) {
            throw new DataAccessException("データベース接続の取得に失敗しました", e);
        } catch (DataAccessException e) {
            throw e;
        } catch (Exception e) {
            throw new DataAccessException("読み取り専用トランザクションの実行に失敗しました", e);
        } finally {
            contextHolder.remove();
            if (conn != null) {
                try {
                    conn.setAutoCommit(readContext.originalAutoCommit);
                } catch (SQLException e) {
                    logger.debug("Failed to restore auto-commit state", e);
                }
                database.releaseConnection(conn);
            }
        }
    }

    private static final class TxContext {
        int depth = 0;
        boolean rollbackOnly = false;
        boolean originalAutoCommit = true;
        Connection connection = null;

        void reset() {
            depth = 0;
            rollbackOnly = false;
            originalAutoCommit = true;
            connection = null;
        }
    }
}