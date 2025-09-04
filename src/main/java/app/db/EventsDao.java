package app.db;

import app.model.Event;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class EventsDao {
    private final Database db;

    public EventsDao(Database db) { this.db = db; }

    public List<Event> listBetween(long startEpochSec, long endEpochSec) {
        String sql = "SELECT id, title, start_epoch_sec, end_epoch_sec FROM events " +
                "WHERE end_epoch_sec > ? AND start_epoch_sec < ? ORDER BY start_epoch_sec";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, startEpochSec);
            ps.setLong(2, endEpochSec);
            try (ResultSet rs = ps.executeQuery()) {
                List<Event> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public long insert(String title, long start, long end) {
        String sql = "INSERT INTO events(title, start_epoch_sec, end_epoch_sec) VALUES(?,?,?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setLong(2, start);
            ps.setLong(3, end);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
                return -1;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void update(Event ev) {
        String sql = "UPDATE events SET title=?, start_epoch_sec=?, end_epoch_sec=? WHERE id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, ev.getTitle());
            ps.setLong(2, ev.getStartEpochSec());
            ps.setLong(3, ev.getEndEpochSec());
            ps.setLong(4, ev.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(long id) {
        String sql = "DELETE FROM events WHERE id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Event> searchByTitle(String query, int limit) {
        String sql = "SELECT id, title, start_epoch_sec, end_epoch_sec FROM events WHERE title LIKE ? ORDER BY start_epoch_sec LIMIT ?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, "%" + query + "%");
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Event> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Event get(long id) {
        String sql = "SELECT id, title, start_epoch_sec, end_epoch_sec FROM events WHERE id=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
                return null;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
