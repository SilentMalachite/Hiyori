package app.db;

import app.config.AppConfig;
import app.exception.DatabaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);
    private final String url;
    private final int busyTimeoutMs;
    private final int maxPoolSize;
    private final BlockingQueue<Connection> connectionPool;
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final ReentrantReadWriteLock poolLock = new ReentrantReadWriteLock();
    private final AtomicBoolean closing = new AtomicBoolean(false);

    private int resolvePoolSize() {
        final int defaultSize = 4; // conservative default for local/CI
        final int maxCap = 16;     // avoid accidental explosion
        Integer override = null;
        String prop = System.getProperty("db.pool.size");
        if (prop != null && !prop.isBlank()) {
            try {
                override = Integer.parseInt(prop.trim());
            } catch (NumberFormatException e) {
                logger.debug("Invalid system property db.pool.size='{}' — falling back to default", prop);
            }
        }
        if (override == null) {
            String env = System.getenv("DB_POOL_SIZE");
            if (env != null && !env.isBlank()) {
                try {
                    override = Integer.parseInt(env.trim());
                } catch (NumberFormatException e) {
                    logger.debug("Invalid env DB_POOL_SIZE='{}' — falling back to default", env);
                }
            }
        }
        int resolved = override != null ? override : defaultSize;
        if (resolved < 1) resolved = 1;
        if (resolved > maxCap) resolved = maxCap;
        return resolved;
    }

    public Database(String path) {
        this.url = "jdbc:sqlite:" + path;
        this.busyTimeoutMs = AppConfig.getInstance().getDatabaseConnectionTimeoutMs();
        this.maxPoolSize = resolvePoolSize();
        this.connectionPool = new LinkedBlockingQueue<>(maxPoolSize);
    }

    public void initialize() throws DatabaseException {
        if (isInitialized.compareAndSet(false, true)) {
            synchronized (this) {
                // Double-check pattern to prevent race conditions
                if (connectionPool.size() > 0) {
                    logger.info("Database already initialized");
                    return;
                }
                
                try {
                    logger.info("Initializing database connection pool to: {} (size: {})", url, maxPoolSize);
                    
                    // Initialize pool with connections
                    int createdConnections = 0;
                    for (int i = 0; i < maxPoolSize; i++) {
                        try {
                            Connection conn = createConnection();
                            if (connectionPool.offer(conn)) {
                                createdConnections++;
                            } else {
                                conn.close();
                                logger.warn("Connection pool full during initialization, closing excess connection");
                            }
                        } catch (SQLException e) {
                            logger.error("Failed to create connection {} during initialization", i, e);
                            // Continue trying to create other connections
                        }
                    }
                    
                    if (createdConnections == 0) {
                        throw new DatabaseException("Failed to create any database connections during initialization");
                    }
                    
                    // Create schema using one connection from pool
                    Connection schemaConn = getConnection();
                    try {
                        createSchema(schemaConn);
                    } finally {
                        releaseConnection(schemaConn);
                    }
                    
                    logger.info("Database initialized successfully with {} connections", createdConnections);
                } catch (Exception e) {
                    logger.error("Failed to initialize database", e);
                    close();
                    isInitialized.set(false); // Reset for retry
                    throw new DatabaseException("データベースの初期化に失敗しました", e);
                }
            }
        }
    }

    public Connection getConnection() throws DatabaseException {
        if (!isInitialized.get()) {
            throw new DatabaseException("Database not initialized");
        }
        if (closing.get()) {
            throw new DatabaseException("Database is closing");
        }
        poolLock.readLock().lock();
        try {
            if (!isInitialized.get() || closing.get()) {
                throw new DatabaseException("Database not available");
            }
            while (true) {
                Connection conn = connectionPool.poll(5, TimeUnit.SECONDS);
                if (conn == null) {
                    throw new DatabaseException("Connection pool exhausted - timeout waiting for connection");
                }
                
                // Validate connection
                try {
                    if (conn.isClosed()) {
                        logger.warn("Closed connection found in pool, creating new one");
                        conn = createConnection();
                        return conn;
                    }
                    
                    // Additional validation - test connection with simple query
                    if (!isConnectionValid(conn)) {
                        logger.warn("Invalid connection found in pool, creating new one");
                        try {
                            conn.close();
                        } catch (SQLException e) {
                            logger.warn("Failed to close invalid connection", e);
                        }
                        conn = createConnection();
                        return conn;
                    }
                    
                    return conn;
                } catch (SQLException e) {
                    // If validation fails with SQLException, try to get another connection
                    logger.warn("Connection validation failed, trying next connection", e);
                    try {
                        conn.close();
                    } catch (SQLException closeEx) {
                        logger.debug("Failed to close invalid connection", closeEx);
                    }
                    // Continue loop to get next connection
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DatabaseException("Interrupted while waiting for database connection", e);
        } finally {
            poolLock.readLock().unlock();
        }
    }

    public void releaseConnection(Connection conn) {
        if (conn != null) {
            // If database is closing or not initialized, do not return connections to pool
            if (closing.get() || !isInitialized.get()) {
                try {
                    if (!conn.isClosed()) conn.close();
                } catch (SQLException e) {
                    logger.warn("Failed to close connection during shutdown/release", e);
                }
                return;
            }

            boolean needsReplacement = false;
            boolean shouldOffer = false;
            synchronized (conn) {
                try {
                    if (conn.isClosed()) {
                        logger.debug("Attempted to release closed connection");
                        needsReplacement = true;
                    } else {
                        resetConnection(conn);
                        shouldOffer = true;
                    }
                } catch (SQLException e) {
                    logger.warn("Failed to release connection", e);
                    try {
                        conn.close();
                    } catch (SQLException closeEx) {
                        logger.warn("Failed to close connection during release", closeEx);
                    }
                    needsReplacement = true;
                    shouldOffer = false;
                }

                if (shouldOffer) {
                    if (!connectionPool.offer(conn)) {
                        try {
                            conn.close();
                        } catch (SQLException e) {
                            logger.warn("Failed to close connection when pool full", e);
                        }
                        logger.debug("Connection pool full, closed excess connection");
                    }
                }
            }

            if (needsReplacement && isInitialized.get() && !closing.get()) {
                try {
                    Connection replacement = createConnection();
                    if (!connectionPool.offer(replacement)) {
                        replacement.close();
                        logger.debug("Connection pool full while adding replacement, closed connection");
                    }
                } catch (SQLException e) {
                    logger.warn("Failed to create replacement connection", e);
                }
            }
        }
    }

    private Connection createConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=" + busyTimeoutMs);
        }
        return conn;
    }

    private boolean isConnectionValid(Connection conn) {
        try {
            // Simple validation query - SQLite specific
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT 1")) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            logger.debug("Connection validation failed", e);
            return false;
        }
    }

    private void resetConnection(Connection conn) throws SQLException {
        // Reset connection state for reuse
        try {
            if (!conn.getAutoCommit()) {
                // For SQLite, just rollback and set auto-commit
                conn.rollback();
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            // If rollback fails, try to force auto-commit on
            try {
                conn.setAutoCommit(true);
            } catch (SQLException autoCommitEx) {
                // If both fail, close the connection - it's corrupted
                logger.warn("Failed to reset connection state, connection will be discarded", autoCommitEx);
                conn.close();
                throw autoCommitEx;
            }
            throw e;
        }
        
        // Clear any pending warnings
        try {
            conn.clearWarnings();
        } catch (SQLException e) {
            logger.debug("Failed to clear connection warnings", e);
        }
    }

    private void createSchema(Connection conn) throws SQLException {
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

    public void close() {
        poolLock.writeLock().lock();
        try {
            if (!isInitialized.get() && connectionPool.isEmpty()) {
                return;
            }
            closing.set(true);
            // Perform WAL checkpoint before closing to consolidate WAL file
            try {
                Connection conn = connectionPool.peek();
                if (conn != null && !conn.isClosed()) {
                    try (Statement st = conn.createStatement()) {
                        st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                        logger.debug("WAL checkpoint completed");
                    }
                }
            } catch (SQLException e) {
                logger.warn("Failed to perform WAL checkpoint during shutdown", e);
            }
            
            // Close all connections
            Connection conn;
            while ((conn = connectionPool.poll()) != null) {
                try {
                    if (!conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    logger.warn("Failed to close connection during shutdown", e);
                }
            }
            isInitialized.set(false);
            logger.info("Database connection pool closed");
        } finally {
            closing.set(false);
            poolLock.writeLock().unlock();
        }
    }
}
