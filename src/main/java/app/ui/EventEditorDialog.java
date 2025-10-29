package app.ui;

import app.config.AppConfig;
import app.model.Event;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.time.*;

public class EventEditorDialog extends Dialog<EventEditorDialog.Result> {
    public enum Action { SAVE, DELETE, CANCEL }
    public record Result(Action action, Event event) {}

    private final AppConfig config = AppConfig.getInstance();
    private final ZoneId zone;
    private final Event original;

    private final TextField titleField = new TextField();
    private final DatePicker startDate = new DatePicker();
    private final Spinner<Integer> startHour = new Spinner<>(0, 23, 9);
    private final Spinner<Integer> startMinute = new Spinner<>(0, 59, 0, 5);
    private final DatePicker endDate = new DatePicker();
    private final Spinner<Integer> endHour = new Spinner<>(0, 23, 10);
    private final Spinner<Integer> endMinute = new Spinner<>(0, 59, 0, 5);

    public EventEditorDialog(Event event, ZoneId zone) {
        this.zone = zone;
        this.original = cloneEvent(event);

        setTitle("イベント編集");
        ButtonType BTN_SAVE = new ButtonType("保存", ButtonBar.ButtonData.OK_DONE);
        ButtonType BTN_DELETE = new ButtonType("削除", ButtonBar.ButtonData.LEFT);
        getDialogPane().getButtonTypes().addAll(BTN_SAVE, BTN_DELETE, ButtonType.CANCEL);

        // Layout
        GridPane gp = new GridPane();
        gp.setHgap(8);
        gp.setVgap(8);
        gp.setPadding(new Insets(12));

        int row = 0;
        gp.add(new Label("タイトル"), 0, row);
        gp.add(titleField, 1, row++, 3, 1);

        gp.add(new Label("開始"), 0, row);
        gp.add(startDate, 1, row);
        gp.add(startHour, 2, row);
        gp.add(startMinute, 3, row++);

        gp.add(new Label("終了"), 0, row);
        gp.add(endDate, 1, row);
        gp.add(endHour, 2, row);
        gp.add(endMinute, 3, row++);

        // Preset block buttons
        HBox presets = new HBox(8);
        Button btnFocus = new Button(config.getEventEditorFocusPresetTitle());
        Button btnBreak = new Button(config.getEventEditorBreakPresetTitle());
        btnFocus.setOnAction(ev -> applyPreset(config.getEventEditorFocusPresetDurationMinutes(), config.getEventEditorFocusPresetTitle()));
        btnBreak.setOnAction(ev -> applyPreset(config.getEventEditorBreakPresetDurationMinutes(), config.getEventEditorBreakPresetTitle()));
        presets.getChildren().addAll(btnFocus, btnBreak);
        gp.add(new Label("定型"), 0, row);
        gp.add(presets, 1, row++, 3, 1);

        getDialogPane().setContent(gp);

        // Initialize values
        titleField.setText(original.getTitle());
        LocalDateTime sdt = LocalDateTime.ofInstant(Instant.ofEpochSecond(original.getStartEpochSec()), zone);
        LocalDateTime edt = LocalDateTime.ofInstant(Instant.ofEpochSecond(original.getEndEpochSec()), zone);
        startDate.setValue(sdt.toLocalDate());
        startHour.getValueFactory().setValue(sdt.getHour());
        startMinute.getValueFactory().setValue(sdt.getMinute());
        endDate.setValue(edt.toLocalDate());
        endHour.getValueFactory().setValue(edt.getHour());
        endMinute.getValueFactory().setValue(edt.getMinute());

        // Editable
        startHour.setEditable(true);
        startMinute.setEditable(true);
        endHour.setEditable(true);
        endMinute.setEditable(true);

        // Result converter
        setResultConverter(new Callback<ButtonType, Result>() {
            @Override
            public Result call(ButtonType button) {
                ButtonBar.ButtonData data = button.getButtonData();
                if (data == ButtonBar.ButtonData.OK_DONE) {
                    Event ev = buildEventFromInputs();
                    if (ev == null) {
                        return new Result(Action.CANCEL, null);
                    }
                    return new Result(Action.SAVE, ev);
                } else if (data == ButtonBar.ButtonData.LEFT) {
                    return new Result(Action.DELETE, original);
                }
                return new Result(Action.CANCEL, null);
            }
        });

        // Basic validation: ensure end > start before enabling Save
        Node okButton = getDialogPane().lookupButton(BTN_SAVE);
        okButton.disableProperty().bind(new javafx.beans.binding.BooleanBinding() {
            { bind(titleField.textProperty(), startDate.valueProperty(), endDate.valueProperty(),
                    startHour.valueProperty(), startMinute.valueProperty(), endHour.valueProperty(), endMinute.valueProperty()); }
            @Override protected boolean computeValue() {
                Event ev = buildEventFromInputs();
                return ev == null || ev.getEndEpochSec() <= ev.getStartEpochSec();
            }
        });

        // Keyboard shortcuts: Enter => Save, Cmd/Ctrl+Delete => Delete
        if (okButton instanceof Button b) {
            b.setDefaultButton(true);
        }
        Node deleteButton = getDialogPane().lookupButton(BTN_DELETE);
        getDialogPane().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (!okButton.isDisabled()) {
                    ((Button) okButton).fire();
                    e.consume();
                }
            } else if (e.getCode() == KeyCode.DELETE && (e.isMetaDown() || e.isControlDown())) {
                if (deleteButton instanceof Button db) {
                    db.fire();
                    e.consume();
                }
            }
        });
    }

    private Event buildEventFromInputs() {
        LocalDate sDate = startDate.getValue();
        LocalDate eDate = endDate.getValue();
        Integer sh = startHour.getValue();
        Integer sm = startMinute.getValue();
        Integer eh = endHour.getValue();
        Integer em = endMinute.getValue();
        if (sDate == null || eDate == null || sh == null || sm == null || eh == null || em == null) return null;

        LocalDateTime sdt = LocalDateTime.of(sDate, LocalTime.of(clamp(sh,0,23), clamp(sm,0,59)));
        LocalDateTime edt = LocalDateTime.of(eDate, LocalTime.of(clamp(eh,0,23), clamp(em,0,59)));
        long s = sdt.atZone(zone).toEpochSecond();
        long e = edt.atZone(zone).toEpochSecond();
        Event ev = cloneEvent(original);
        ev.setTitle(titleField.getText() == null || titleField.getText().isBlank() ? "(無題)" : titleField.getText().trim());
        ev.setStartEpochSec(s);
        ev.setEndEpochSec(e);
        return ev;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private static Event cloneEvent(Event e) {
        Event c = new Event();
        c.setId(e.getId());
        c.setTitle(e.getTitle());
        c.setStartEpochSec(e.getStartEpochSec());
        c.setEndEpochSec(e.getEndEpochSec());
        return c;
    }

    private void applyPreset(int minutes, String title) {
        // set title
        titleField.setText(title);
        // compute end = start + minutes
        LocalDate sDate = startDate.getValue();
        Integer sh = startHour.getValue();
        Integer sm = startMinute.getValue();
        if (sDate == null || sh == null || sm == null) return;
        LocalDateTime sdt = LocalDateTime.of(sDate, LocalTime.of(clamp(sh,0,23), clamp(sm,0,59)));
        LocalDateTime edt = sdt.plusMinutes(minutes);
        endDate.setValue(edt.toLocalDate());
        endHour.getValueFactory().setValue(edt.getHour());
        endMinute.getValueFactory().setValue(edt.getMinute());
    }
}
