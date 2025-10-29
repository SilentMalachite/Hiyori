package app.ui;

import app.config.AppConfig;
import app.service.EventService;
import app.exception.DataAccessException;
import app.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Lightweight week view using Canvas as per spec.
 */
public class WeekView {
    private static final Logger logger = LoggerFactory.getLogger(WeekView.class);
    private final Supplier<EventService> eventServiceSupplier;
    private final Pane root = new Pane();
    private final Canvas canvas = new Canvas();
    private final AppConfig config = AppConfig.getInstance();
    private final Insets padding; // left space for time labels
    private final double hourHeight; // px per hour
    private final double dayHeaderHeight;

    private LocalDate weekStart; // Monday
    private final ZoneId zone = ZoneId.systemDefault();
    private final List<Event> events = new ArrayList<>();

    private boolean draggingNew = false;
    private long dragStartEpoch = 0L;
    private long dragEndEpoch = 0L;

    private enum DragMode { NONE, MOVE, RESIZE_START, RESIZE_END, NEW }
    private DragMode dragMode = DragMode.NONE;
    private Event dragTarget = null;
    private long dragPointerOffsetSec = 0L; // for move: pointer - event.start

    private static final double HANDLE_SIZE = 6; // px

    private Long highlightEventId = null;
    private final int snapStepMinutes;

    public WeekView(Supplier<EventService> eventServiceSupplier) {
        this.eventServiceSupplier = eventServiceSupplier;
        this.weekStart = LocalDate.now().with(DayOfWeek.MONDAY);
        this.snapStepMinutes = AppConfig.getInstance().getEventSnapMinutes();

        // Initialize UI constants from config
        this.padding = new Insets(
            config.getWeekViewPaddingTop(),
            config.getWeekViewPaddingRight(),
            config.getWeekViewPaddingBottom(),
            config.getWeekViewPaddingLeft()
        );
        this.hourHeight = config.getWeekViewHourHeight();
        this.dayHeaderHeight = config.getWeekViewDayHeaderHeight();
        root.getChildren().add(canvas);
        root.setMinHeight(24 * hourHeight + dayHeaderHeight + padding.getTop() + padding.getBottom());
        root.heightProperty().addListener((obs, a, b) -> layoutCanvas());
        root.widthProperty().addListener((obs, a, b) -> layoutCanvas());

        canvas.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.getY() < padding.getTop() + dayHeaderHeight) return; // ignore header area

            Event target = findEventAt(e.getX(), e.getY());
            if (target != null) {
                // Determine if on handles or body
                double[] bounds = eventBoundsPx(target);
                double ex = bounds[0], ey = bounds[1], ew = bounds[2], eh = bounds[3];
                if (e.getY() <= ey + HANDLE_SIZE) {
                    dragMode = DragMode.RESIZE_START;
                } else if (e.getY() >= ey + eh - HANDLE_SIZE) {
                    dragMode = DragMode.RESIZE_END;
                } else {
                    dragMode = DragMode.MOVE;
                }
                dragTarget = cloneEvent(target);
                long pointerTime = pointToEpoch(e.getX(), e.getY(), !e.isAltDown());
                dragPointerOffsetSec = pointerTime - dragTarget.getStartEpochSec();
            } else {
                // Create new
                dragMode = DragMode.NEW;
                draggingNew = true;
                dragStartEpoch = pointToEpoch(e.getX(), e.getY(), !e.isAltDown());
                dragEndEpoch = dragStartEpoch + 60 * 60; // default 60 minutes
            }
            draw();
        });
        canvas.setOnMouseDragged(e -> {
            if (dragMode == DragMode.NEW) {
                if (!draggingNew) return;
                dragEndEpoch = pointToEpoch(e.getX(), e.getY(), !e.isAltDown());
                draw();
            } else if (dragTarget != null) {
                long pointer = pointToEpoch(e.getX(), e.getY(), !e.isAltDown());
                if (dragMode == DragMode.MOVE) {
                    long newStart = pointer - dragPointerOffsetSec;
                    long dur = dragTarget.getEndEpochSec() - dragTarget.getStartEpochSec();
                    dragTarget.setStartEpochSec(newStart);
                    dragTarget.setEndEpochSec(newStart + dur);
                } else if (dragMode == DragMode.RESIZE_START) {
                    long minDur = 5 * 60;
                    long newStart = Math.min(pointer, dragTarget.getEndEpochSec() - minDur);
                    dragTarget.setStartEpochSec(newStart);
                } else if (dragMode == DragMode.RESIZE_END) {
                    long minDur = 5 * 60;
                    long newEnd = Math.max(pointer, dragTarget.getStartEpochSec() + minDur);
                    dragTarget.setEndEpochSec(newEnd);
                }
                draw();
            }
        });
        canvas.setOnMouseReleased(e -> {
            if (dragMode == DragMode.NEW) {
                if (!draggingNew) return;
                draggingNew = false;
                long start = Math.min(dragStartEpoch, dragEndEpoch);
                long end = Math.max(dragStartEpoch, dragEndEpoch);
                if (end - start < 5 * 60) end = start + 5 * 60; // minimum 5 minutes
                try {
                    eventServiceSupplier.get().createEvent("新規予定", start, end);
                    dragMode = DragMode.NONE;
                    reload();
                } catch (DataAccessException ex) {
                    logger.error("Failed to insert new event", ex);
                    dragMode = DragMode.NONE;
                    draw();
                }
            } else if (dragTarget != null) {
                try {
                    eventServiceSupplier.get().updateEvent(dragTarget);
                    long id = dragTarget.getId();
                    dragTarget = null;
                    dragMode = DragMode.NONE;
                    reload();
                    // keep highlight on moved/edited event
                    highlightEvent(id);
                } catch (DataAccessException ex) {
                    logger.error("Failed to update event", ex);
                    dragTarget = null;
                    dragMode = DragMode.NONE;
                    draw();
                }
            }
        });

        canvas.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                Event target = findEventAt(e.getX(), e.getY());
                if (target != null) {
                    openEventEditor(target);
                }
            }
        });
    }

    public Node getNode() { return root; }

    public void reload() {
        try {
            long start = weekStart.atStartOfDay(zone).toEpochSecond();
            long end = weekStart.plusDays(7).atStartOfDay(zone).toEpochSecond();
            events.clear();
            events.addAll(eventServiceSupplier.get().getEventsBetween(start, end));
            logger.debug("Reloaded {} events for week starting {}", events.size(), weekStart);
            draw();
        } catch (DataAccessException ex) {
            logger.error("Failed to reload events", ex);
            // Keep existing events and redraw
            draw();
        }
    }

    public void quickAddEventAtNow() {
        try {
            eventServiceSupplier.get().createEventFromNow("集中 (90分)");
            logger.debug("Quick added 90-minute focus block");
            reload();
        } catch (DataAccessException ex) {
            logger.error("Failed to quick add event", ex);
        }
    }

    private void layoutCanvas() {
        canvas.setWidth(root.getWidth());
        canvas.setHeight(Math.max(root.getHeight(), 24 * hourHeight + dayHeaderHeight + padding.getTop() + padding.getBottom()));
        draw();
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.setFill(Color.web(config.getWeekViewBackgroundColor()));
        g.fillRect(0, 0, w, h);

        // Colors: keep palette small
        Color gridColor = Color.web(config.getWeekViewGridColor());
        Color hourBold = Color.web(config.getWeekViewHourBoldColor());
        Color eventColor = Color.web(config.getWeekViewEventColor()); // blue

        double contentX = padding.getLeft();
        double contentY = padding.getTop() + dayHeaderHeight;
        double contentW = w - padding.getLeft() - padding.getRight();
        double contentH = 24 * hourHeight;

        // Day headers
        g.setFill(Color.web(config.getWeekViewTextColor()));
        for (int d = 0; d < 7; d++) {
            LocalDate date = weekStart.plusDays(d);
            String label = date.getMonthValue() + "/" + date.getDayOfMonth() + " (" + dayLabel(d) + ")";
            double colX = contentX + d * (contentW / 7.0);
            g.fillText(label, colX + 6, padding.getTop() + 18);
        }

        // Time labels and horizontal grid lines (15-min increments)
        g.setStroke(gridColor);
        g.setLineWidth(1.0);
        for (int hIdx = 0; hIdx <= 24; hIdx++) {
            double y = contentY + hIdx * hourHeight;
            // 時刻線は常に強調色
            g.setStroke(hourBold);
            g.strokeLine(contentX, y, contentX + contentW, y);
            if (hIdx < 24) {
                g.setFill(Color.web("#666666"));
                String hh = String.format("%02d:00", hIdx);
                g.fillText(hh, 4, y + 4);
            }
            // 15-min minor lines
            if (hIdx < 24) {
                double y15 = y + hourHeight * 0.25;
                double y30 = y + hourHeight * 0.5;
                double y45 = y + hourHeight * 0.75;
                g.setStroke(gridColor);
                g.strokeLine(contentX, y15, contentX + contentW, y15);
                g.strokeLine(contentX, y30, contentX + contentW, y30);
                g.strokeLine(contentX, y45, contentX + contentW, y45);
            }
        }

        // Vertical day separators
        g.setStroke(hourBold);
        for (int d = 0; d <= 7; d++) {
            double x = contentX + d * (contentW / 7.0);
            g.strokeLine(x, contentY, x, contentY + contentH);
        }

        // Draw events with column layout per overlapping group
        List<LayoutBox> layout = layoutEvents(contentX, contentY, contentW);
        for (LayoutBox lb : layout) {
            drawEventBox(g, lb, eventColor);
        }

        // Draw dragging new event overlay
        if (draggingNew) {
            Event temp = new Event();
            long s = Math.min(dragStartEpoch, dragEndEpoch);
            long e = Math.max(dragStartEpoch, dragEndEpoch);
            temp.setTitle("新規予定");
            temp.setStartEpochSec(s);
            temp.setEndEpochSec(e);
            LayoutBox lb = computeBoxForEvent(temp, contentX, contentY, contentW, 0, 1);
            drawEventBox(g, lb, Color.web("#7bb8ff"));
        }
        // Dragging existing target overlay
        if (dragTarget != null) {
            LayoutBox lb = computeBoxForEvent(dragTarget, contentX, contentY, contentW, 0, 1);
            drawEventBox(g, lb, Color.web("#7bb8ff"));
        }
    }

    private static class LayoutBox {
        Event ev;
        double x, y, w, h;
        int dayIndex;
    }

    private List<LayoutBox> layoutEvents(double contentX, double contentY, double contentW) {
        List<LayoutBox> out = new ArrayList<>();
        // group by day
        for (int d = 0; d < 7; d++) {
            List<Event> day = new ArrayList<>();
            LocalDate dayDate = weekStart.plusDays(d);
            for (Event ev : events) {
                LocalDate sday = LocalDateTime.ofInstant(Instant.ofEpochSecond(ev.getStartEpochSec()), zone).toLocalDate();
                if (sday.equals(dayDate)) day.add(ev);
            }
            day.sort((a,b) -> Long.compare(a.getStartEpochSec(), b.getStartEpochSec()));
            // sweep to assign columns
            List<Long> colEnd = new ArrayList<>();
            List<List<Event>> colEvents = new ArrayList<>();
            // For group width calculation, track active set
            List<Event> active = new ArrayList<>();
            for (Event ev : day) {
                // cleanup columns where end <= start
                for (int i = 0; i < colEnd.size(); i++) {
                    if (colEnd.get(i) <= ev.getStartEpochSec()) {
                        colEnd.set(i, 0L);
                    }
                }
                int col = -1;
                for (int i = 0; i < colEnd.size(); i++) {
                    if (colEnd.get(i) == 0L) { col = i; break; }
                }
                if (col == -1) {
                    col = colEnd.size();
                    colEnd.add(0L);
                    colEvents.add(new ArrayList<>());
                }
                colEnd.set(col, ev.getEndEpochSec());
                colEvents.get(col).add(ev);
            }
            int totalCols = Math.max(1, colEnd.size());
            for (int c = 0; c < colEvents.size(); c++) {
                for (Event ev : colEvents.get(c)) {
                    LayoutBox lb = computeBoxForEvent(ev, contentX, contentY, contentW, c, totalCols);
                    lb.dayIndex = d;
                    out.add(lb);
                }
            }
        }
        return out;
    }

    private LayoutBox computeBoxForEvent(Event ev, double contentX, double contentY, double contentW, int colIndex, int totalCols) {
        LayoutBox lb = new LayoutBox();
        lb.ev = ev;
        LocalDateTime sdt = LocalDateTime.ofInstant(Instant.ofEpochSecond(ev.getStartEpochSec()), zone);
        LocalDateTime edt = LocalDateTime.ofInstant(Instant.ofEpochSecond(ev.getEndEpochSec()), zone);
        LocalDate d = sdt.toLocalDate();
        int dayIndex = (int) Duration.between(weekStart.atStartOfDay(), d.atStartOfDay()).toDays();
        double colWidthFull = contentW / 7.0;
        double colWidth = Math.max(10, (colWidthFull - 4) / Math.max(1, totalCols));
        double x = contentX + dayIndex * colWidthFull + 2 + colIndex * colWidth;
        double yStart = contentY + (sdt.getHour() + sdt.getMinute() / 60.0) * hourHeight + 2;
        double yEnd = contentY + (edt.getHour() + edt.getMinute() / 60.0) * hourHeight - 2;
        if (yEnd < yStart + 6) yEnd = yStart + 6;
        lb.x = x; lb.y = yStart; lb.w = colWidth - 2; lb.h = yEnd - yStart; lb.dayIndex = dayIndex;
        return lb;
    }

    private void drawEventBox(GraphicsContext g, LayoutBox lb, Color base) {
        boolean hl = (highlightEventId != null && lb.ev.getId() == highlightEventId);
        Color fill = hl ? Color.web(config.getWeekViewHighlightColor()) : base;
        g.setFill(fill);
        g.fillRoundRect(lb.x, lb.y, lb.w, lb.h, 6, 6);
        g.setFill(Color.WHITE);
        g.fillText(lb.ev.getTitle(), lb.x + 6, lb.y + 14);
        // handles
        g.setFill(Color.color(1,1,1,0.6));
        g.fillRect(lb.x + 2, lb.y, lb.w - 4, HANDLE_SIZE);
        g.fillRect(lb.x + 2, lb.y + lb.h - HANDLE_SIZE, lb.w - 4, HANDLE_SIZE);
    }

    private Event findEventAt(double x, double y) {
        double[] dims = contentDims();
        double contentX = dims[0], contentY = dims[1], contentW = dims[2];
        List<LayoutBox> layout = layoutEvents(contentX, contentY, contentW);
        for (LayoutBox lb : layout) {
            if (x >= lb.x && x <= lb.x + lb.w && y >= lb.y && y <= lb.y + lb.h) return lb.ev;
        }
        return null;
    }

    private double[] contentDims() {
        double w = canvas.getWidth();
        double contentX = padding.getLeft();
        double contentY = padding.getTop() + dayHeaderHeight;
        double contentW = w - padding.getLeft() - padding.getRight();
        return new double[]{contentX, contentY, contentW};
    }

    private double[] eventBoundsPx(Event ev) {
        double[] dims = contentDims();
        double contentX = dims[0], contentY = dims[1], contentW = dims[2];
        LayoutBox lb = computeBoxForEvent(ev, contentX, contentY, contentW, 0, 1);
        return new double[]{lb.x, lb.y, lb.w, lb.h};
    }

    private long pointToEpoch(double x, double y, boolean snap) {
        double[] dims = contentDims();
        double contentX = dims[0];
        double contentY = dims[1];
        double contentW = dims[2];
        double colWidth = contentW / 7.0;
        int dayIndex = (int) Math.floor((x - contentX) / colWidth);
        if (dayIndex < 0) dayIndex = 0; if (dayIndex > 6) dayIndex = 6;
        double hourFloat = (y - contentY) / hourHeight;
        if (hourFloat < 0) hourFloat = 0; if (hourFloat > 24) hourFloat = 24;
        int hour = (int) Math.floor(hourFloat);
        int minutes = (int) Math.round((hourFloat - hour) * 60);
        if (snap) minutes = snapMinutes(minutes);
        LocalDate date = weekStart.plusDays(dayIndex);
        LocalDateTime dt = LocalDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, Math.min(minutes, 59));
        return dt.atZone(zone).toEpochSecond();
    }

    private int snapMinutes(int minutes) {
        int step = Math.max(1, Math.min(30, snapStepMinutes));
        int snapped = (int) Math.round(minutes / (double) step) * step;
        if (snapped >= 60) snapped = 59; // 60分は分としては扱わない
        if (snapped < 0) snapped = 0;
        return snapped;
    }

    private String dayLabel(int idx) {
        return switch (idx) {
            case 0 -> "月"; case 1 -> "火"; case 2 -> "水"; case 3 -> "木"; case 4 -> "金"; case 5 -> "土"; default -> "日";
        };
    }

    private static Event cloneEvent(Event e) {
        Event c = new Event();
        c.setId(e.getId());
        c.setTitle(e.getTitle());
        c.setStartEpochSec(e.getStartEpochSec());
        c.setEndEpochSec(e.getEndEpochSec());
        return c;
    }

    public void showEvent(long eventId) {
        try {
            // center view to week of event and highlight
            Event ev = eventServiceSupplier.get().getEvent(eventId);
            if (ev == null) {
                logger.warn("Event not found with ID: {}", eventId);
                return;
            }
            LocalDateTime sdt = LocalDateTime.ofInstant(Instant.ofEpochSecond(ev.getStartEpochSec()), zone);
            LocalDate d = sdt.toLocalDate();
            this.weekStart = d.with(DayOfWeek.MONDAY);
            logger.debug("Showing event ID: {} on week starting {}", eventId, weekStart);
            reload();
            highlightEvent(eventId);
        } catch (DataAccessException ex) {
            logger.error("Failed to show event ID: {}", eventId, ex);
        }
    }

    private void highlightEvent(long eventId) {
        this.highlightEventId = eventId;
        // clear after short duration
        javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        pt.setOnFinished(_e -> { this.highlightEventId = null; draw(); });
        pt.play();
    }

    private void openEventEditor(Event target) {
        EventEditorDialog dlg = new EventEditorDialog(target, zone);
        dlg.initOwner(root.getScene() != null ? root.getScene().getWindow() : null);
        var resultOpt = dlg.showAndWait();
        if (resultOpt.isPresent()) {
            var res = resultOpt.get();
            try {
                if (res.action() == EventEditorDialog.Action.SAVE && res.event() != null) {
                    eventServiceSupplier.get().updateEvent(res.event());
                    reload();
                    highlightEvent(res.event().getId());
                } else if (res.action() == EventEditorDialog.Action.DELETE) {
                    eventServiceSupplier.get().deleteEvent(target.getId());
                    reload();
                }
            } catch (DataAccessException ex) {
                logger.error("Failed to handle event editor action: {}", res.action(), ex);
            }
        }
    }
}
