package app.controller;

import app.config.AppConfig;
import app.exception.DataAccessException;
import app.model.Event;
import app.model.Note;
import app.model.SearchItem;
import app.model.NoteItem;
import app.model.EventItem;
import app.service.EventService;
import app.service.NoteService;
import app.ui.WeekView;
import app.util.Debouncer;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * メイン画面のUI制御を担当するコントローラー
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    
    private final NoteService noteService;
    private final EventService eventService;
    private final AppConfig config;
    
    // UI Components
    private final ObservableList<SearchItem> items = FXCollections.observableArrayList();
    private final ListView<SearchItem> notesList = new ListView<>();
    private final TextField titleField = new TextField();
    private final TextArea bodyArea = new TextArea();
    private final TextField searchField = new TextField();
    private final Debouncer autosaveDebounce;
    private final Debouncer searchDebounce;
    private final BooleanProperty editorFocused = new SimpleBooleanProperty(false);
    
    // State
    private Note currentNote;
    private WeekView weekView;

    public MainController(NoteService noteService, EventService eventService) {
        this.noteService = noteService;
        this.eventService = eventService;
        this.config = AppConfig.getInstance();
        
        // Initialize debouncers with config values
        this.autosaveDebounce = new Debouncer(config.getAutosaveDebounceMs() / 1000.0);
        this.searchDebounce = new Debouncer(config.getSearchDebounceMs() / 1000.0);
        
        initializeUI();
        setupEventHandlers();
    }

    private void initializeUI() {
        // Configure notes list
        notesList.setItems(items);
        notesList.setPrefWidth(config.getNotesListWidth());
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
                setMinHeight(config.getNotesListMinHeight());
            }
        });

        // Configure editor fields
        titleField.setPromptText("タイトル");
        bodyArea.setPromptText("本文");
        bodyArea.setWrapText(true);

        // Configure search field
        searchField.setPromptText("メモ・予定を検索 (Ctrl/Cmd+K)");
    }

    private void setupEventHandlers() {
        // List selection handler
        notesList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue instanceof NoteItem noteItem) {
                loadNote(noteItem.note());
            } else if (newValue instanceof EventItem eventItem) {
                if (weekView != null) {
                    weekView.showEvent(eventItem.event().getId());
                }
            } else if (newValue == null) {
                clearEditor();
            }
        });

        // Auto-save handlers
        Runnable autosave = () -> Platform.runLater(this::saveCurrentNoteIfChanged);
        titleField.textProperty().addListener((obs, oldValue, newValue) -> 
            autosaveDebounce.call(autosave));
        bodyArea.textProperty().addListener((obs, oldValue, newValue) -> 
            autosaveDebounce.call(autosave));

        // Search handler
        searchField.setOnKeyTyped(e -> 
            searchDebounce.call(() -> Platform.runLater(this::performGlobalSearch)));
        searchField.setOnAction(e -> performGlobalSearch());

        // Focus handling for single-focus mode（アプリ全体で利用できるよう公開プロパティにバインド）
        editorFocused.bind(titleField.focusedProperty().or(bodyArea.focusedProperty()));
        notesList.opacityProperty().bind(Bindings.when(editorFocused).then(0.5).otherwise(1.0));
    }

    public void setWeekView(WeekView weekView) {
        this.weekView = weekView;
    }

    public void loadInitialData() {
        reloadNotes();
    }

    public void setupShortcuts(Scene scene) {
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN), 
            this::createNewNote);
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN), 
            () -> {
                searchField.requestFocus();
                searchField.selectAll();
            });
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), 
            this::saveCurrentNoteIfChanged);
        scene.getAccelerators().put(
            new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), 
            () -> {
                if (weekView != null) {
                    weekView.quickAddEventAtNow();
                }
            });
    }

    private void performGlobalSearch() {
        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            reloadNotes();
            return;
        }

        try {
            logger.debug("Performing global search for: '{}'", query);
            List<Note> foundNotes = noteService.searchNotes(query);
            List<Event> foundEvents = eventService.searchEventsByTitle(query);

            foundNotes = limitList(foundNotes, config.getSearchNotesLimit());
            foundEvents = limitList(foundEvents, config.getSearchEventsLimit());
            
            items.clear();
            for (Note note : foundNotes) {
                items.add(new NoteItem(note));
            }
            for (Event event : foundEvents) {
                items.add(new EventItem(event));
            }
            
            if (!items.isEmpty()) {
                notesList.getSelectionModel().selectFirst();
            }
            
            logger.debug("Search completed: {} notes, {} events found", foundNotes.size(), foundEvents.size());
        } catch (DataAccessException e) {
            logger.error("Search failed for query: '{}'", query, e);
            showError("検索に失敗しました", e.getMessage());
        }
    }

    private static <T> List<T> limitList(List<T> source, int limit) {
        if (limit <= 0 || source.size() <= limit) {
            return source;
        }
        return source.subList(0, limit);
    }

    private void reloadNotes() {
        try {
            List<Note> notes = noteService.getRecentNotes();
            int maxItems = config.getNotesListMaxItems();
            if (maxItems > 0 && notes.size() > maxItems) {
                notes = notes.subList(0, maxItems);
            }
            items.setAll(notes.stream().map(NoteItem::new).toList());
            logger.debug("Reloaded {} notes", notes.size());
        } catch (DataAccessException e) {
            logger.error("Failed to reload notes", e);
            showError("メモの読み込みに失敗しました", e.getMessage());
        }
    }

    private void loadNote(Note note) {
        currentNote = note;
        titleField.setText(note.getTitle());
        bodyArea.setText(note.getBody());
    }

    private void clearEditor() {
        currentNote = null;
        titleField.clear();
        bodyArea.clear();
    }

    private void createNewNote() {
        try {
            Note note = noteService.createNote("無題のメモ", "");
            logger.debug("Created new note with ID: {}", note.getId());
            reloadNotes();
            if (!items.isEmpty()) {
                notesList.getSelectionModel().selectFirst();
            }
        } catch (DataAccessException e) {
            logger.error("Failed to create new note", e);
            showError("新規メモの作成に失敗しました", e.getMessage());
        }
    }

    private void saveCurrentNoteIfChanged() {
        if (currentNote == null) return;
        
        String title = Optional.ofNullable(titleField.getText()).orElse("").trim();
        String body = Optional.ofNullable(bodyArea.getText()).orElse("");
        
        if (!title.equals(currentNote.getTitle()) || !body.equals(currentNote.getBody())) {
            try {
                currentNote.setTitle(title.isEmpty() ? "無題のメモ" : title);
                currentNote.setBody(body);
                noteService.updateNote(currentNote);
                logger.debug("Note saved: ID={}, title='{}'", currentNote.getId(), currentNote.getTitle());
                notesList.refresh();
            } catch (DataAccessException e) {
                logger.error("Failed to save note ID: {}", currentNote.getId(), e);
                showError("メモの保存に失敗しました", e.getMessage());
            }
        }
    }

    private void showError(String title, String message) {
        logger.warn("Error: {} - {}", title, message);
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.showAndWait();
    }

    // Getters for UI components
    public ListView<SearchItem> getNotesList() { return notesList; }
    public TextField getTitleField() { return titleField; }
    public TextArea getBodyArea() { return bodyArea; }
    public TextField getSearchField() { return searchField; }
    public BooleanProperty getEditorFocusedProperty() { return editorFocused; }
}
