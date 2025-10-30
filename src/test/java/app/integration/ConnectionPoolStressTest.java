package app.integration;

import app.db.Database;
import app.exception.DatabaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * コネクションプールのストレステスト
 */
class ConnectionPoolStressTest {
    
    private Database database;
    private Path dbPath;
    private Path tempDir;
    private String originalPoolSizeProperty;

    @BeforeEach
    void setUp() throws IOException, DatabaseException {
        // テスト用の一時データベースファイルを作成
        originalPoolSizeProperty = System.getProperty("db.pool.size");
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        System.setProperty("db.pool.size", String.valueOf(poolSize));

        this.tempDir = Files.createTempDirectory("connection-pool-test-");
        this.dbPath = tempDir.resolve("test.db");
        this.database = new Database(dbPath.toString());
        this.database.initialize();
    }

    @AfterEach
    void tearDown() {
        if (database != null) {
            database.close();
        }
        if (originalPoolSizeProperty != null) {
            System.setProperty("db.pool.size", originalPoolSizeProperty);
        } else {
            System.clearProperty("db.pool.size");
        }
        try {
            Files.deleteIfExists(dbPath);
            if (tempDir != null) {
                Files.deleteIfExists(tempDir);
            }
        } catch (IOException e) {
            // クリーンアップ失敗は無視
        }
    }

    @Test
    @DisplayName("並行コネクション取得・解放が正常に動作する")
    void testConcurrentConnectionAcquisitionAndRelease() throws Exception {
        int threadCount = 20;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Exception> exceptions = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 全スレッドが同時に開始
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        Connection conn = null;
                        try {
                            conn = database.getConnection();
                            assertThat(conn).isNotNull();
                            assertThat(conn.isClosed()).isFalse();
                            
                            // 簡単なクエリを実行して接続を検証
                            try (var stmt = conn.createStatement();
                                 var rs = stmt.executeQuery("SELECT 1")) {
                                assertThat(rs.next()).isTrue();
                                assertThat(rs.getInt(1)).isEqualTo(1);
                            }
                            
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            exceptions.add(e);
                        } finally {
                            if (conn != null) {
                                database.releaseConnection(conn);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // 全スレッドを同時に開始
        startLatch.countDown();
        
        // 全スレッドの完了を待機（最大30秒）
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errorCount.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(threadCount * operationsPerThread);
        assertThat(exceptions).isEmpty();
    }

    @Test
    @DisplayName("コネクションプール枯渇時のタイムアウトが正常に動作する")
    void testConnectionPoolExhaustionTimeout() throws Exception {
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        List<Connection> acquiredConnections = new ArrayList<>();

        // プール内の全コネクションを取得
        for (int i = 0; i < poolSize; i++) {
            Connection conn = database.getConnection();
            assertThat(conn).isNotNull();
            acquiredConnections.add(conn);
        }

        // プールが枯渇した状態で追加のコネクションを要求
        long startTime = System.currentTimeMillis();
        assertThatThrownBy(() -> {
            database.getConnection(); // タイムアウトするはず
        }).isInstanceOf(DatabaseException.class)
          .hasMessageContaining("Connection pool exhausted");
        
        long elapsedTime = System.currentTimeMillis() - startTime;
        assertThat(elapsedTime).isBetween(4000L, 6000L); // 5秒前後のタイムアウト

        // クリーンアップ
        for (Connection conn : acquiredConnections) {
            database.releaseConnection(conn);
        }
    }

    @Test
    @DisplayName("無効なコネクションの検証と交換が正常に動作する")
    void testInvalidConnectionDetectionAndReplacement() throws Exception {
        Connection conn = database.getConnection();
        assertThat(conn).isNotNull();
        
        // 意図的にコネクションを無効化
        conn.close();
        
        // 無効化されたコネクションを解放
        database.releaseConnection(conn);
        
        // 新しいコネクションを取得して有効性を確認
        Connection newConn = database.getConnection();
        assertThat(newConn).isNotNull();
        assertThat(newConn.isClosed()).isFalse();
        
        // 新しいコネクションでクエリを実行
        try (var stmt = newConn.createStatement();
             var rs = stmt.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
        
        database.releaseConnection(newConn);
    }

    @Test
    @DisplayName("コネクション状態のリセットが正常に動作する")
    void testConnectionStateReset() throws Exception {
        Connection conn = database.getConnection();
        assertThat(conn).isNotNull();
        
        // トランザクション状態に変更 (use JDBC standard methods)
        conn.setAutoCommit(false);
        try (java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO events(title, start_epoch_sec, end_epoch_sec) VALUES('test', 1, 2)");
        }
        conn.rollback();
        
        // コネクションを解放
        database.releaseConnection(conn);
        
        // 再取得して状態がリセットされていることを確認
        Connection sameConn = database.getConnection();
        assertThat(sameConn.getAutoCommit()).isTrue();
        
        database.releaseConnection(sameConn);
    }

    @Test
    @DisplayName("閉じた接続を解放してもプール容量が維持される")
    void testClosedConnectionsAreReplenished() throws Exception {
        int poolSize = Math.max(2, Runtime.getRuntime().availableProcessors());
        List<Connection> connections = new ArrayList<>();

        for (int i = 0; i < poolSize; i++) {
            Connection conn = database.getConnection();
            assertThat(conn).isNotNull();
            connections.add(conn);
        }

        for (Connection conn : connections) {
            conn.close();
            database.releaseConnection(conn);
        }

        for (int i = 0; i < poolSize; i++) {
            Connection conn = null;
            try {
                conn = database.getConnection();
                assertThat(conn.isClosed()).isFalse();
            } finally {
                if (conn != null) {
                    database.releaseConnection(conn);
                }
            }
        }
    }
}