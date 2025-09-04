package app;

import app.db.Database;
import app.db.EventsDao;
import app.db.NotesDao;
import app.model.Event;
import app.model.Note;
import app.model.SearchItem;
import app.model.NoteItem;
import app.model.EventItem;
import app.ui.WeekView;
import app.util.Debouncer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

public class MainApp extends Application {
    private Database database;
    private NotesDao notesDao;
    private EventsDao eventsDao;

    private final ObservableList<SearchItem> items = FXCollections.observableArrayList();
    private final ListView<SearchItem> notesList = new ListView<>();
    private final TextField titleField = new TextField();
    private final TextArea bodyArea = new TextArea();
    private final TextField searchField = new TextField();
    private final Debouncer autosaveDebounce = new Debouncer(0.6); // 600ms
    private final BooleanProperty editorFocused = new SimpleBooleanProperty(false);

    private Note currentNote;
    private WeekView weekView;

    @Override
    public void start(Stage stage) {
        try {
            initDatabase();
        } catch (Exception e) {
            showErrorAndExit("データベース初期化に失敗しました", e);
            return;
        }

        BorderPane root = new BorderPane();
        root.getStylesheets().add(getClass().getResource("/application.css").toExternalForm());

        // Top: Global search
        HBox top = new HBox(8);
        top.setPadding(new Insets(10));
        Label searchLabel = new Label("Search");
        searchField.setPromptText("メモ・予定を検索 (Ctrl/Cmd+K)");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        top.getChildren().addAll(searchLabel, searchField);
        root.setTop(top);

        // Left: Notes list
        notesList.setItems(items);
        notesList.setPrefWidth(280);
        notesList.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(SearchItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.display());
                }
                setFont(Font.font(14));
                setMinHeight(44); // ASD/ADHD: larger target size
            }
        });
        root.setLeft(notesList);

        // Right: Editor
        VBox editor = new VBox(8);
        editor.setPadding(new Insets(10));
        titleField.setPromptText("タイトル");
        bodyArea.setPromptText("本文");
        bodyArea.setWrapText(true);
        VBox.setVgrow(bodyArea, Priority.ALWAYS);
        editor.getChildren().addAll(titleField, bodyArea);
        root.setCenter(editor);

        // Bottom: Calendar tabs (Week view focused)
        TabPane tabs = new TabPane();
        Tab weekTab = new Tab("週");
        weekTab.setClosable(false);
        weekView = new WeekView(() -> eventsDao);
        weekTab.setContent(weekView.getNode());
        tabs.getTabs().add(weekTab);
        root.setBottom(tabs);

        // Dimming other areas when editing (single-focus)
        BooleanProperty anyEditorFocused = new SimpleBooleanProperty();
        anyEditorFocused.bind(titleField.focusedProperty().or(bodyArea.focusedProperty()));
        notesList.opacityProperty().bind(Bindings.when(anyEditorFocused).then(0.5).otherwise(1.0));
        tabs.opacityProperty().bind(Bindings.when(anyEditorFocused).then(0.5).otherwise(1.0));

        // Load initial notes
        reloadNotes();

        // List selection -> load into editor
        notesList.getSelectionModel().selectedItemProperty().addListener((obs, a, b) -> {
            if (b instanceof NoteItem ni) {
                loadNote(ni.note());
            } else if (b instanceof EventItem ei) {
                weekView.showEvent(ei.event().getId());
            } else if (b == null) {
                clearEditor();
            }
        });

        // Autosave on edit with debounce
        Runnable autosave = () -> Platform.runLater(this::saveCurrentNoteIfChanged);
        titleField.textProperty().addListener((obs, a, b) -> autosaveDebounce.call(autosave));
        bodyArea.textProperty().addListener((obs, a, b) -> autosaveDebounce.call(autosave));

        // Search
        Debouncer searchDebounce = new Debouncer(0.3);
        searchField.setOnKeyTyped(e -> searchDebounce.call(() -> Platform.runLater(this::performGlobalSearch)));
        searchField.setOnAction(e -> performGlobalSearch());

        // Shortcuts
        Scene scene = new Scene(root, 1200, 800);
        addShortcuts(scene);

        stage.setTitle("Hiyori");
        stage.setScene(scene);
        stage.show();

        // Select the first item if exists
        if (!items.isEmpty()) notesList.getSelectionModel().selectFirst();

        // Load events in week view
        weekView.reload();

        // Optional: auto-exit after seconds for headless CI or quick smoke test
        String exitSec = System.getProperty("app.testExitSeconds");
        if (exitSec != null && !exitSec.isBlank()) {
            try {
                double sec = Double.parseDouble(exitSec);
                PauseTransition pt = new PauseTransition(Duration.seconds(sec));
                pt.setOnFinished(ev -> Platform.exit());
                pt.play();
            } catch (NumberFormatException ignored) { }
        }
    }

    private void addShortcuts(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), this::createNewNote);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN), () -> {
            searchField.requestFocus();
            searchField.selectAll();
        });
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), this::saveCurrentNoteIfChanged);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> {
            weekView.quickAddEventAtNow();
        });
    }

    private void performGlobalSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) {
            reloadNotes();
            return;
        }
        // Filter notes using FTS and show results in the list; events are not shown here but week view can be navigated.
        List<Note> foundNotes = notesDao.searchNotes(q, 300);
        List<Event> foundEvents = eventsDao.searchByTitle(q, 200);
        items.clear();
        for (Note n : foundNotes) items.add(new NoteItem(n));
        for (Event e : foundEvents) items.add(new EventItem(e));
        if (!items.isEmpty()) notesList.getSelectionModel().selectFirst();
    }

    private void initDatabase() throws Exception {
        Path dataDir = Path.of("data");
        if (!Files.exists(dataDir)) Files.createDirectories(dataDir);
        Path dbPath = dataDir.resolve("app.db");
        database = new Database(dbPath.toString());
        database.initialize();
        notesDao = new NotesDao(database);
        eventsDao = new EventsDao(database);
    }

    private void reloadNotes() {
        List<Note> list = notesDao.listRecent(500);
        items.setAll(list.stream().map(NoteItem::new).toList());
    }

    private void loadNote(Note n) {
        currentNote = n;
        titleField.setText(n.getTitle());
        bodyArea.setText(n.getBody());
    }

    private void clearEditor() {
        currentNote = null;
        titleField.clear();
        bodyArea.clear();
    }

    private void createNewNote() {
        Note n = new Note();
        n.setTitle("無題のメモ");
        n.setBody("");
        long now = Instant.now().getEpochSecond();
        n.setCreatedAt(now);
        n.setUpdatedAt(now);
        long id = notesDao.insert(n);
        n.setId(id);
        reloadNotes();
        if (!items.isEmpty()) notesList.getSelectionModel().selectFirst();
    }

    private void saveCurrentNoteIfChanged() {
        if (currentNote == null) return;
        String title = Optional.ofNullable(titleField.getText()).orElse("").trim();
        String body = Optional.ofNullable(bodyArea.getText()).orElse("");
        if (!title.equals(currentNote.getTitle()) || !body.equals(currentNote.getBody())) {
            currentNote.setTitle(title.isEmpty() ? "無題のメモ" : title);
            currentNote.setBody(body);
            currentNote.setUpdatedAt(Instant.now().getEpochSecond());
            notesDao.update(currentNote);
            // refresh title in list cell
            notesList.refresh();
        }
    }

    private void showErrorAndExit(String message, Exception e) {
        e.printStackTrace();
        Alert alert = new Alert(Alert.AlertType.ERROR, message + "\n" + e.getMessage(), ButtonType.CLOSE);
        alert.showAndWait();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
