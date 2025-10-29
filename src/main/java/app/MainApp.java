package app;

import app.config.AppConfig;
import app.controller.MainController;
import app.db.Database;
import app.db.EventsDao;
import app.db.NotesDao;
import app.db.TransactionManager;
import app.exception.DatabaseException;
import app.service.EventService;
import app.service.NoteService;
import app.ui.WeekView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    
    private Database database;
    private NotesDao notesDao;
    private EventsDao eventsDao;
    private TransactionManager transactionManager;
    private NoteService noteService;
    private EventService eventService;
    private MainController mainController;
    private AppConfig config;

    @Override
    public void start(Stage stage) {
        try {
            // 先に実行環境を整える（ログ/ディレクトリ等）。
            prepareEnvironment();
            logger.info("Starting Hiyori application");
            initializeApplication();
            createUI(stage);
        } catch (DatabaseException e) {
            logger.error("Application initialization failed", e);
            showErrorAndExit("アプリケーション初期化に失敗しました", e);
            return;
        }
    }

    /**
     * 実行前に必要なフォルダ等を用意する。
     */
    private void prepareEnvironment() {
        try {
            // ログフォルダ
            Path logs = Path.of("logs");
            if (!Files.exists(logs)) {
                Files.createDirectories(logs);
                logger.debug("Created logs directory: {}", logs.toAbsolutePath());
            }
        } catch (IOException e) {
            // ログディレクトリの作成に失敗してもアプリは続行可能
            // （ログはコンソールにも出力される）
            logger.warn("Failed to create logs directory, console logging only", e);
        }
    }

    private void initializeApplication() throws DatabaseException {
        // Initialize configuration
        config = AppConfig.getInstance();
        
        // Initialize database
        initDatabase();
        
        // Initialize transaction manager first
        transactionManager = new TransactionManager(database);
        
        // Initialize DAOs with transaction manager
        notesDao = new NotesDao(database, transactionManager);
        eventsDao = new EventsDao(database, transactionManager);
        
        // Initialize services
        noteService = new NoteService(notesDao, transactionManager);
        eventService = new EventService(eventsDao, transactionManager);
        
        // Initialize controller
        mainController = new MainController(noteService, eventService);
    }

    private void createUI(Stage stage) {
        BorderPane root = new BorderPane();
        var css = getClass().getResource("/application.css");
        if (css != null) {
            root.getStylesheets().add(css.toExternalForm());
        }

        // Top: Global search
        HBox top = new HBox(8);
        top.setPadding(new Insets(10));
        Label searchLabel = new Label("Search");
        HBox.setHgrow(mainController.getSearchField(), Priority.ALWAYS);
        top.getChildren().addAll(searchLabel, mainController.getSearchField());
        root.setTop(top);

        // Left: Notes list
        root.setLeft(mainController.getNotesList());

        // Right: Editor
        VBox editor = new VBox(8);
        editor.setPadding(new Insets(10));
        VBox.setVgrow(mainController.getBodyArea(), Priority.ALWAYS);
        editor.getChildren().addAll(mainController.getTitleField(), mainController.getBodyArea());
        root.setCenter(editor);

        // Bottom: Calendar tabs (Week view focused)
        TabPane tabs = new TabPane();
        Tab weekTab = new Tab("週");
        weekTab.setClosable(false);
        WeekView weekView = new WeekView(() -> eventService);
        weekTab.setContent(weekView.getNode());
        tabs.getTabs().add(weekTab);
        root.setBottom(tabs);

        // Set up week view in controller
        mainController.setWeekView(weekView);

        // Dimming other areas when editing (single-focus)
        mainController.getNotesList().opacityProperty().bind(
            Bindings.when(mainController.getEditorFocusedProperty()).then(0.5).otherwise(1.0));
        tabs.opacityProperty().bind(
            Bindings.when(mainController.getEditorFocusedProperty()).then(0.5).otherwise(1.0));

        // Load initial data
        mainController.loadInitialData();

        // Setup shortcuts
        Scene scene = new Scene(root, config.getWindowWidth(), config.getWindowHeight());
        mainController.setupShortcuts(scene);

        stage.setTitle("Hiyori");
        stage.setScene(scene);
        stage.show();

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
            } catch (NumberFormatException e) {
                logger.warn("Invalid value for app.testExitSeconds: '{}', ignoring auto-exit", exitSec);
            }
        }
    }


    private void initDatabase() throws DatabaseException {
        try {
            // 設定のパスをそのまま使用（相対ならカレント基準）。
            Path dbPath = Path.of(config.getDatabasePath());
            Path parent = dbPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                logger.info("Creating data directory: {}", parent);
                Files.createDirectories(parent);
            }
            logger.info("Initializing database at: {}", dbPath);
            database = new Database(dbPath.toString());
            database.initialize();
            logger.info("Database initialization completed successfully");
        } catch (IOException e) {
            logger.error("Failed to create data directory", e);
            throw new DatabaseException("データディレクトリの作成に失敗しました", e);
        }
    }

    private void showErrorAndExit(String message, DatabaseException e) {
        logger.error("Fatal error occurred", e);
        Alert alert = new Alert(Alert.AlertType.ERROR, message + "\n" + e.getMessage(), ButtonType.CLOSE);
        alert.showAndWait();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        // アプリ終了時にDBをクローズ
        if (database != null) {
            try {
                database.close();
                logger.info("Database connection closed");
            } catch (Exception e) {
                logger.warn("Failed to close database", e);
            }
        }
    }
}
