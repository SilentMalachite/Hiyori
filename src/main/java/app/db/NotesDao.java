package app.db;

import app.model.Note;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class NotesDao {
    private final Database db;

    public NotesDao(Database db) { this.db = db; }

    public List<Note> listRecent(int limit) {
        String sql = "SELECT id, title, body, created_at, updated_at FROM notes ORDER BY updated_at DESC LIMIT ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Note> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public long insert(Note n) {
        String sql = "INSERT INTO notes(title, body, created_at, updated_at) VALUES (?,?,?,?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, n.getTitle());
            ps.setString(2, n.getBody());
            ps.setLong(3, n.getCreatedAt());
            ps.setLong(4, n.getUpdatedAt());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
                return -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(Note n) {
        String sql = "UPDATE notes SET title=?, body=?, updated_at=? WHERE id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, n.getTitle());
            ps.setString(2, n.getBody());
            ps.setLong(3, n.getUpdatedAt());
            ps.setLong(4, n.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Note> searchNotes(String query, int limit) {
        if (query == null || query.isBlank()) return listRecent(limit);
        String sql = "SELECT n.id, n.title, n.body, n.created_at, n.updated_at " +
                "FROM notes_fts f JOIN notes n ON n.id = f.rowid " +
                "WHERE notes_fts MATCH ? " +
                "ORDER BY n.updated_at DESC LIMIT ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, escapeFts(query));
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Note> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String escapeFts(String q) {
        // Basic sanitation: wrap as quoted phrase if contains spaces
        String trimmed = q.trim();
        if (trimmed.contains(" ")) return '"' + trimmed.replace('"', ' ') + '"';
        return trimmed.replace('"', ' ');
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

