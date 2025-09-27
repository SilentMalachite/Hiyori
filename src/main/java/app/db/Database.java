package app.db;

import app.config.AppConfig;
import app.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);
    private final String url;
    private Connection conn;
    private final int busyTimeoutMs;

    public Database(String path) {
        this.url = "jdbc:sqlite:" + path;
        this.busyTimeoutMs = AppConfig.getInstance().getDatabaseConnectionTimeoutMs();
    }

    public void initialize() throws DatabaseException {
        try {
            logger.info("Initializing database connection to: {}", url);
            conn = DriverManager.getConnection(url);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA busy_timeout=" + busyTimeoutMs);
            }
            createSchema();
            logger.info("Database initialized successfully");
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
            throw new DatabaseException("データベースの初期化に失敗しました", e);
        }
    }

    public Connection getConnection() { return conn; }

    private void createSchema() throws SQLException {
        logger.debug("Creating database schema");
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS notes (" +
                    "id INTEGER PRIMARY KEY, " +
                    "title TEXT NOT NULL, " +
                    "body TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL, " +
                    "updated_at INTEGER NOT NULL)" );

            st.executeUpdate("CREATE VIRTUAL TABLE IF NOT EXISTS notes_fts USING fts5(" +
                    "title, body, content='notes', content_rowid='id')");

            st.executeUpdate("CREATE TRIGGER IF NOT EXISTS notes_ai AFTER INSERT ON notes BEGIN " +
                    "INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body); END;");
            st.executeUpdate("CREATE TRIGGER IF NOT EXISTS notes_au AFTER UPDATE ON notes BEGIN " +
                    "INSERT INTO notes_fts(notes_fts,rowid,title,body) VALUES('delete', old.id, old.title, old.body);" +
                    "INSERT INTO notes_fts(rowid, title, body) VALUES (new.id, new.title, new.body); END;");
            st.executeUpdate("CREATE TRIGGER IF NOT EXISTS notes_ad AFTER DELETE ON notes BEGIN " +
                    "INSERT INTO notes_fts(notes_fts,rowid,title,body) VALUES('delete', old.id, old.title, old.body); END;");

            st.executeUpdate("CREATE TABLE IF NOT EXISTS events (" +
                    "id INTEGER PRIMARY KEY, " +
                    "title TEXT NOT NULL, " +
                    "start_epoch_sec INTEGER NOT NULL, " +
                    "end_epoch_sec INTEGER NOT NULL)" );

            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_events_start ON events(start_epoch_sec)");
        }
    }

    public void close() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }
}
