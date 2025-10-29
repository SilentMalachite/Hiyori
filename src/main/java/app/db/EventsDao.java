package app.db;

import app.exception.DataAccessException;
import app.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EventsDao {
    private static final Logger logger = LoggerFactory.getLogger(EventsDao.class);
    private final Database db;
    private final TransactionManager transactionManager;

    public EventsDao(Database db, TransactionManager transactionManager) { 
        this.db = db;
        this.transactionManager = transactionManager;
    }

    private Connection getConnection() throws DataAccessException {
        // Check if we're in a transaction and have a connection available
        if (transactionManager != null && transactionManager.isInTransaction()) {
            Connection txConn = transactionManager.getCurrentConnection();
            logger.debug("In transaction, using existing connection");
            return txConn;
        }
        logger.debug("Not in transaction, getting new connection");
        // Otherwise get a new connection
        try {
            return db.getConnection();
        } catch (app.exception.DatabaseException e) {
            throw new DataAccessException("Failed to get database connection", e);
        }
    }

    private void closeResources(ResultSet rs, PreparedStatement ps, Connection conn) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                logger.warn("Failed to close ResultSet", e);
            }
        }
        if (ps != null) {
            try {
                ps.close();
            } catch (SQLException e) {
                logger.warn("Failed to close PreparedStatement", e);
            }
        }
        // Only release connection if not in transaction
        if (conn != null && (transactionManager == null || !transactionManager.isInTransaction())) {
            db.releaseConnection(conn);
        }
    }

    public List<Event> listBetween(long startEpochSec, long endEpochSec) throws DataAccessException {
        logger.debug("Listing events between {} and {}", startEpochSec, endEpochSec);
        String sql = "SELECT id, title, start_epoch_sec, end_epoch_sec FROM events " +
                "WHERE end_epoch_sec > ? AND start_epoch_sec < ? ORDER BY start_epoch_sec";
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, startEpochSec);
            ps.setLong(2, endEpochSec);
            rs = ps.executeQuery();
            List<Event> list = new ArrayList<>();
            while (rs.next()) list.add(map(rs));
            logger.debug("Retrieved {} events", list.size());
            return list;
        } catch (SQLException e) {
            logger.error("Failed to list events between {} and {}", startEpochSec, endEpochSec, e);
            throw new DataAccessException("予定の一覧取得に失敗しました", e);
        } finally {
            closeResources(rs, ps, conn);
        }
    }

    public long insert(String title, long start, long end) throws DataAccessException {
        logger.debug("Inserting new event: {}", title);
        String sql = "INSERT INTO events(title, start_epoch_sec, end_epoch_sec) VALUES(?,?,?)";
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, title);
            ps.setLong(2, start);
            ps.setLong(3, end);
            ps.executeUpdate();
            rs = ps.getGeneratedKeys();
            if (rs.next()) {
                long id = rs.getLong(1);
                logger.debug("Event inserted with ID: {}", id);
                return id;
            }
            logger.error("Failed to get generated key for inserted event");
            throw new DataAccessException("予定の挿入後にIDの取得に失敗しました");
        } catch (SQLException e) {
            logger.error("Failed to insert event: {}", title, e);
            throw new DataAccessException("予定の挿入に失敗しました", e);
        } finally {
            closeResources(rs, ps, conn);
        }
    }

    public void update(Event ev) throws DataAccessException {
        logger.debug("Updating event ID: {}", ev.getId());
        String sql = "UPDATE events SET title=?, start_epoch_sec=?, end_epoch_sec=? WHERE id=?";
        
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, ev.getTitle());
            ps.setLong(2, ev.getStartEpochSec());
            ps.setLong(3, ev.getEndEpochSec());
            ps.setLong(4, ev.getId());
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                logger.warn("No rows affected when updating event ID: {}", ev.getId());
                throw new DataAccessException("更新対象の予定が見つかりませんでした (ID: " + ev.getId() + ")");
            }
            logger.debug("Event updated successfully");
        } catch (SQLException e) {
            logger.error("Failed to update event ID: {}", ev.getId(), e);
            throw new DataAccessException("予定の更新に失敗しました", e);
        } finally {
            closeResources(null, ps, conn);
        }
    }

    public void delete(long id) throws DataAccessException {
        logger.debug("Deleting event ID: {}", id);
        String sql = "DELETE FROM events WHERE id=?";
        
        Connection conn = null;
        PreparedStatement ps = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                logger.warn("No rows affected when deleting event ID: {}", id);
                throw new DataAccessException("削除対象の予定が見つかりませんでした (ID: " + id + ")");
            }
            logger.debug("Event deleted successfully");
        } catch (SQLException e) {
            logger.error("Failed to delete event ID: {}", id, e);
            throw new DataAccessException("予定の削除に失敗しました", e);
        } finally {
            closeResources(null, ps, conn);
        }
    }

    public List<Event> searchByTitle(String query, int limit) throws DataAccessException {
        logger.debug("Searching events by title: '{}', limit: {}", query, limit);
        String sql = "SELECT id, title, start_epoch_sec, end_epoch_sec FROM events " +
                "WHERE title LIKE ? ESCAPE '\\' ORDER BY start_epoch_sec LIMIT ?";
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, "%" + escapeLikeQuery(query) + "%");
            ps.setInt(2, limit);
            rs = ps.executeQuery();
            List<Event> list = new ArrayList<>();
            while (rs.next()) list.add(map(rs));
            logger.debug("Search returned {} events", list.size());
            return list;
        } catch (SQLException e) {
            logger.error("Failed to search events by title: '{}'", query, e);
            throw new DataAccessException("予定の検索に失敗しました", e);
        } finally {
            closeResources(rs, ps, conn);
        }
    }

    public Event get(long id) throws DataAccessException {
        logger.debug("Getting event ID: {}", id);
        String sql = "SELECT id, title, start_epoch_sec, end_epoch_sec FROM events WHERE id=?";
        
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            conn = getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, id);
            rs = ps.executeQuery();
            if (rs.next()) {
                Event event = map(rs);
                logger.debug("Event found: {}", event.getTitle());
                return event;
            }
            logger.debug("Event not found with ID: {}", id);
            return null;
        } catch (SQLException e) {
            logger.error("Failed to get event ID: {}", id, e);
            throw new DataAccessException("予定の取得に失敗しました", e);
        } finally {
            closeResources(rs, ps, conn);
        }
    }

    private static String escapeLikeQuery(String query) {
        if (query == null) return "";
        // Escape LIKE wildcards in SQLite
        return query.replace("\\", "\\\\")
                   .replace("%", "\\%")
                   .replace("_", "\\_");
    }

    private static Event map(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setId(rs.getLong("id"));
        e.setTitle(rs.getString("title"));
        e.setStartEpochSec(rs.getLong("start_epoch_sec"));
        e.setEndEpochSec(rs.getLong("end_epoch_sec"));
        return e;
    }
}