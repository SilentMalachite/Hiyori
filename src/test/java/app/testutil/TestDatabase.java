package app.testutil;

import app.db.Database;
import app.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * テスト用のデータベースユーティリティ
 */
public class TestDatabase {
    private static final Logger logger = LoggerFactory.getLogger(TestDatabase.class);
    private final Database database;
    private final Path dbPath;

    public TestDatabase() throws DatabaseException, IOException {
        // テスト用の一時データベースファイルを作成
        Path tempDir = Files.createTempDirectory("hiyori-test-");
        this.dbPath = tempDir.resolve("test.db");
        this.database = new Database(dbPath.toString());
        this.database.initialize();
        logger.debug("Test database created at: {}", dbPath);
    }

    public Database getDatabase() {
        return database;
    }

    public Connection getConnection() throws DatabaseException {
        return database.getConnection();
    }

    /**
     * テストデータをクリアする
     */
    public void clearData() throws SQLException, DatabaseException {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = getConnection();
            stmt = conn.createStatement();
            
            // 外部キー制約を一時的に無効化
            stmt.execute("PRAGMA foreign_keys=OFF");
            
            // テーブルのデータを削除
            stmt.execute("DELETE FROM notes_fts");
            stmt.execute("DELETE FROM notes");
            stmt.execute("DELETE FROM events");
            
            // シーケンスをリセット（sqlite_sequenceが存在する場合のみ）
            try {
                stmt.execute("DELETE FROM sqlite_sequence WHERE name IN ('notes', 'events')");
            } catch (SQLException e) {
                // sqlite_sequenceテーブルが存在しない場合は無視
                logger.debug("sqlite_sequence table not found, skipping reset");
            }
            
            // 外部キー制約を再有効化
            stmt.execute("PRAGMA foreign_keys=ON");
            
            logger.debug("Test data cleared");
        } finally {
            // StatementとConnectionは閉じない（データベースインスタンスで管理）
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    logger.warn("Failed to close statement", e);
                }
            }
        }
    }

    /**
     * テスト用のサンプルデータを挿入する
     */
    public void insertSampleData() throws SQLException, DatabaseException {
        Connection conn = getConnection();
        try (Statement stmt = conn.createStatement()) {
            
            // サンプルメモデータ
            stmt.execute("""
                INSERT INTO notes (id, title, body, created_at, updated_at) VALUES
                (1, 'テストメモ1', 'これはテスト用のメモです。', 1672538400, 1672538400),
                (2, 'テストメモ2', '検索テスト用のメモです。', 1672539000, 1672539000),
                (3, 'サンプルメモ', 'サンプルデータです。', 1672539600, 1672539600)
                """);
            
            // サンプル予定データ
            stmt.execute("""
                INSERT INTO events (id, title, start_epoch_sec, end_epoch_sec) VALUES
                (1, 'テスト会議', 1672538400, 1672542000),
                (2, 'サンプル予定', 1672542000, 1672545600),
                (3, '検索テスト予定', 1672545600, 1672549200)
                """);
            
            logger.debug("Sample data inserted");
        }
    }

    /**
     * データベースを閉じる
     */
    public void close() {
        try {
            database.close();
            // テスト用ファイルを削除
            Files.deleteIfExists(dbPath);
            Files.deleteIfExists(dbPath.getParent());
            logger.debug("Test database closed and cleaned up");
        } catch (Exception e) {
            logger.warn("Failed to clean up test database", e);
        }
    }

    /**
     * テスト用のデータベース統計を取得
     */
    public DatabaseStats getStats() throws SQLException, DatabaseException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            var notesCount = stmt.executeQuery("SELECT COUNT(*) FROM notes");
            notesCount.next();
            int notes = notesCount.getInt(1);
            
            var eventsCount = stmt.executeQuery("SELECT COUNT(*) FROM events");
            eventsCount.next();
            int events = eventsCount.getInt(1);
            
            return new DatabaseStats(notes, events);
        }
    }

    public static class DatabaseStats {
        public final int notesCount;
        public final int eventsCount;

        public DatabaseStats(int notesCount, int eventsCount) {
            this.notesCount = notesCount;
            this.eventsCount = eventsCount;
        }

        @Override
        public String toString() {
            return String.format("DatabaseStats{notes=%d, events=%d}", notesCount, eventsCount);
        }
    }
}
