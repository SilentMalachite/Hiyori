package app.db;

import app.exception.DataAccessException;
import app.model.Note;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotesDao {
    private static final Logger logger = LoggerFactory.getLogger(NotesDao.class);
    private final Database db;

    public NotesDao(Database db) { this.db = db; }

    public List<Note> listRecent(int limit) throws DataAccessException {
        logger.debug("Listing recent notes with limit: {}", limit);
        String baseSql = "SELECT id, title, body, created_at, updated_at FROM notes ORDER BY updated_at DESC";
        boolean hasLimit = limit > 0;
        String sql = hasLimit ? baseSql + " LIMIT ?" : baseSql;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            if (hasLimit) {
                ps.setInt(1, limit);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Note> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                logger.debug("Retrieved {} notes", list.size());
                return list;
            }
        } catch (SQLException e) {
            logger.error("Failed to list recent notes", e);
            throw new DataAccessException("メモの一覧取得に失敗しました", e);
        }
    }

    public long insert(Note n) throws DataAccessException {
        logger.debug("Inserting new note: {}", n.getTitle());
        String sql = "INSERT INTO notes(title, body, created_at, updated_at) VALUES (?,?,?,?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, n.getTitle());
            ps.setString(2, n.getBody());
            ps.setLong(3, n.getCreatedAt());
            ps.setLong(4, n.getUpdatedAt());
            ps.executeUpdate();
            
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    n.setId(id);
                    logger.debug("Note inserted with ID: {}", id);
                    // FTSインデックスはトリガーで同期される
                    return id;
                }
                logger.error("Failed to get generated key for inserted note");
                throw new DataAccessException("メモの挿入後にIDの取得に失敗しました");
            }
        } catch (SQLException e) {
            logger.error("Failed to insert note", e);
            throw new DataAccessException("メモの挿入に失敗しました", e);
        }
    }

    public void update(Note n) throws DataAccessException {
        logger.debug("Updating note ID: {}", n.getId());
        
        // notesテーブルを更新
        String sql = "UPDATE notes SET title=?, body=?, updated_at=? WHERE id=?";
        int rowsAffected;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, n.getTitle());
            ps.setString(2, n.getBody());
            ps.setLong(3, n.getUpdatedAt());
            ps.setLong(4, n.getId());
            rowsAffected = ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update note ID: {}", n.getId(), e);
            throw new DataAccessException("メモの更新に失敗しました", e);
        }
        
        if (rowsAffected == 0) {
            logger.warn("No rows affected when updating note ID: {}", n.getId());
            throw new DataAccessException("更新対象のメモが見つかりませんでした (ID: " + n.getId() + ")");
        }
        
        // FTSインデックス更新はデータベーストリガーに任せる
        logger.debug("Note updated successfully");
    }

    public List<Note> searchNotes(String query, int limit) throws DataAccessException {
        if (query == null || query.isBlank()) return listRecent(limit);
        logger.debug("Searching notes with query: '{}', limit: {}", query, limit);
        String sql = "SELECT n.id, n.title, n.body, n.created_at, n.updated_at " +
                "FROM notes_fts JOIN notes n ON n.id = notes_fts.rowid " +
                "WHERE notes_fts MATCH ? ORDER BY n.updated_at DESC LIMIT ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            String escaped = escapeFts(query);
            ps.setString(1, escaped);
            ps.setInt(2, Math.max(limit, 1));
            List<Note> ftsResults = executeAndCollect(ps);
            if (!ftsResults.isEmpty()) {
                return ftsResults;
            }
        } catch (SQLException e) {
            logger.warn("FTS search failed for query '{}', falling back to LIKE", query, e);
        }

        return fallbackSearchWithLike(query, limit);
    }

    private static String escapeFts(String q) {
        // Basic sanitation: wrap as quoted phrase if contains spaces
        String trimmed = q.trim();
        String sanitized = trimmed
                .replace('"', ' ')
                .replace('`', ' ')
                .replace('\'', ' ')
                .replace(':', ' ');
        if (sanitized.isEmpty()) {
            return sanitized;
        }
        if (sanitized.contains(" ")) {
            return '"' + sanitized + "*" + '"';
        }
        return sanitized + '*';
    }

    private List<Note> executeAndCollect(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            List<Note> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            logger.debug("Search returned {} notes", list.size());
            return list;
        }
    }

    private List<Note> fallbackSearchWithLike(String query, int limit) throws DataAccessException {
        String base = "SELECT id, title, body, created_at, updated_at FROM notes " +
                "WHERE title LIKE ? OR body LIKE ? ORDER BY updated_at DESC";
        boolean hasLimit = limit > 0;
        String sql = hasLimit ? base + " LIMIT ?" : base;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            String pattern = '%' + query + '%';
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            if (hasLimit) {
                ps.setInt(3, limit);
            }
            List<Note> list = executeAndCollect(ps);
            logger.debug("Fallback LIKE search returned {} notes", list.size());
            return list;
        } catch (SQLException e) {
            logger.error("Failed LIKE fallback search for query: '{}'", query, e);
            throw new DataAccessException("メモの検索に失敗しました", e);
        }
    }

    public Note getById(long id) throws DataAccessException {
        logger.debug("Getting note by ID: {}", id);
        String sql = "SELECT id, title, body, created_at, updated_at FROM notes WHERE id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Note note = map(rs);
                    logger.debug("Note found: {}", note.getTitle());
                    return note;
                }
                logger.debug("Note not found with ID: {}", id);
                return null;
            }
        } catch (SQLException e) {
            logger.error("Failed to get note by ID: {}", id, e);
            throw new DataAccessException("メモの取得に失敗しました", e);
        }
    }

    public void delete(long id) throws DataAccessException {
        logger.debug("Deleting note ID: {}", id);
        
        // notesテーブルから削除
        String sql = "DELETE FROM notes WHERE id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            int rowsAffected = ps.executeUpdate();
            if (rowsAffected == 0) {
                logger.warn("No rows affected when deleting note ID: {}", id);
                throw new DataAccessException("削除対象のメモが見つかりませんでした (ID: " + id + ")");
            }
            logger.debug("Note deleted successfully");
        } catch (SQLException e) {
            logger.error("Failed to delete note ID: {}", id, e);
            throw new DataAccessException("メモの削除に失敗しました", e);
        }
    }

    private static Note map(ResultSet rs) throws SQLException {
        Note n = new Note();
        n.setId(rs.getLong("id"));
        n.setTitle(rs.getString("title"));
        n.setBody(rs.getString("body"));
        n.setCreatedAt(rs.getLong("created_at"));
        n.setUpdatedAt(rs.getLong("updated_at"));
        return n;
    }
}
