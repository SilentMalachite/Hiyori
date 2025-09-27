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
    private final ThreadLocal<TxContext> contextHolder = ThreadLocal.withInitial(TxContext::new);

    public TransactionManager(Database database) {
        this.database = database;
    }

    /**
     * トランザクション内で処理を実行する
     * @param operation 実行する処理
     * @param <T> 戻り値の型
     * @return 処理の結果
     * @throws DataAccessException 処理中にエラーが発生した場合
     */
    public <T> T executeInTransaction(ThrowingSupplier<T> operation) throws DataAccessException {
        Connection conn = database.getConnection();
        TxContext ctx = contextHolder.get();
        boolean outermost = beginTransaction(conn, ctx, false);

        T result = null;
        DataAccessException failure = null;
        try {
            result = operation.get();
        } catch (DataAccessException e) {
            ctx.markRollbackOnly();
            failure = e;
        } catch (Exception e) {
            ctx.markRollbackOnly();
            failure = new DataAccessException("トランザクションの実行に失敗しました", e);
        } finally {
            endTransaction(conn, ctx, outermost, failure);
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
        Connection conn = database.getConnection();
        TxContext ctx = contextHolder.get();
        boolean outermost = beginTransaction(conn, ctx, true);

        T result = null;
        DataAccessException failure = null;
        try {
            result = operation.get();
        } catch (DataAccessException e) {
            ctx.markRollbackOnly();
            failure = e;
        } catch (Exception e) {
            ctx.markRollbackOnly();
            failure = new DataAccessException("読み取り専用トランザクションの実行に失敗しました", e);
        } finally {
            endTransaction(conn, ctx, outermost, failure);
        }

        if (failure != null) throw failure;
        return result;
    }

    private boolean beginTransaction(Connection conn, TxContext ctx, boolean readOnly) throws DataAccessException {
        boolean outermost = ctx.depth == 0;
        if (outermost) {
            logger.debug("Starting {}transaction", readOnly ? "read-only " : "");
            if (!writeLock.isHeldByCurrentThread()) {
                writeLock.lock();
            }
            try {
                ctx.originalAutoCommit = conn.getAutoCommit();
                conn.setAutoCommit(false);
            } catch (SQLException e) {
                writeLock.unlock();
                throw new DataAccessException("トランザクションの開始に失敗しました", e);
            }
            ctx.rollbackOnly = false;
        }
        ctx.depth++;
        return outermost;
    }

    private void endTransaction(Connection conn, TxContext ctx, boolean outermost, DataAccessException failure) throws DataAccessException {
        ctx.depth = Math.max(0, ctx.depth - 1);
        if (!outermost) {
            return;
        }

        DataAccessException result = failure;
        try {
            if (ctx.rollbackOnly || failure != null) {
                conn.rollback();
                logger.debug("Transaction rolled back");
            } else {
                conn.commit();
                logger.debug("Transaction committed successfully");
            }
        } catch (SQLException e) {
            DataAccessException ex = new DataAccessException(ctx.rollbackOnly ? "トランザクションのロールバックに失敗しました" : "トランザクションのコミットに失敗しました", e);
            if (result != null) {
                result.addSuppressed(ex);
            } else {
                result = ex;
            }
        } finally {
            try {
                conn.setAutoCommit(ctx.originalAutoCommit);
            } catch (SQLException e) {
                DataAccessException ex = new DataAccessException("自動コミット設定の復元に失敗しました", e);
                if (result != null) {
                    result.addSuppressed(ex);
                } else {
                    result = ex;
                }
            } finally {
                ctx.reset();
                writeLock.unlock();
            }
        }

        if (result != null) {
            throw result;
        }
    }

    private static final class TxContext {
        int depth = 0;
        boolean rollbackOnly = false;
        boolean originalAutoCommit = true;

        void markRollbackOnly() {
            rollbackOnly = true;
        }

        void reset() {
            depth = 0;
            rollbackOnly = false;
            originalAutoCommit = true;
        }
    }
}
