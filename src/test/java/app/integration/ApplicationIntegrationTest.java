package app.integration;

import app.config.AppConfig;
import app.db.Database;
import app.db.EventsDao;
import app.db.NotesDao;
import app.db.TransactionManager;
import app.exception.DatabaseException;
import app.model.Event;
import app.model.Note;
import app.service.EventService;
import app.service.NoteService;
import app.testutil.TestDataFactory;
import app.testutil.TestDatabase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * アプリケーション統合テスト
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApplicationIntegrationTest {

    private TestDatabase testDb;
    private Database database;
    private NotesDao notesDao;
    private EventsDao eventsDao;
    private TransactionManager transactionManager;
    private NoteService noteService;
    private EventService eventService;
    private AppConfig config;

    @BeforeEach
    void setUp() throws Exception {
        // 各テストごとに新しいデータベースインスタンスを作成
        testDb = new TestDatabase();
        try {
            testDb.clearData();
        } catch (SQLException e) {
            // clearDataが失敗した場合は続行
            System.err.println("Warning: Failed to clear test data: " + e.getMessage());
        }
        database = testDb.getDatabase();
        notesDao = new NotesDao(database);
        eventsDao = new EventsDao(database);
        transactionManager = new TransactionManager(database);
        noteService = new NoteService(notesDao, transactionManager);
        eventService = new EventService(eventsDao, transactionManager);
        config = AppConfig.getInstance();
    }

    @AfterEach
    void tearDown() {
        if (testDb != null) {
            testDb.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("アプリケーションの初期化が正常に動作する")
    void testApplicationInitialization() throws Exception {
        // When & Then
        assertThat(database).isNotNull();
        assertThat(notesDao).isNotNull();
        assertThat(eventsDao).isNotNull();
        assertThat(transactionManager).isNotNull();
        assertThat(noteService).isNotNull();
        assertThat(eventService).isNotNull();
        assertThat(config).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("設定値が正常に読み込まれる")
    void testConfigurationLoading() {
        // When & Then
        assertThat(config.getWindowWidth()).isEqualTo(1200);
        assertThat(config.getWindowHeight()).isEqualTo(800);
        assertThat(config.getNotesListMaxItems()).isEqualTo(500);
        assertThat(config.getAutosaveDebounceMs()).isEqualTo(600.0);
        assertThat(config.getSearchDebounceMs()).isEqualTo(300.0);
        assertThat(config.getEventDefaultDurationMinutes()).isEqualTo(90);
        assertThat(config.getEventMinDurationMinutes()).isEqualTo(5);
    }

    @Test
    @Order(3)
    @DisplayName("メモと予定の連携操作が正常に動作する")
    void testNoteEventIntegration() throws Exception {
        // Given
        LocalDateTime baseTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long baseEpoch = baseTime.atZone(ZoneId.systemDefault()).toEpochSecond();

        // When - 会議の予定とメモを作成
        Event meetingEvent = eventService.createEvent("プロジェクト会議", baseEpoch, baseEpoch + 7200);
        Note meetingNote = noteService.createNote("プロジェクト会議メモ", "会議の内容をまとめました。");

        // Then
        assertThat(meetingEvent).isNotNull();
        assertThat(meetingNote).isNotNull();
        assertThat(meetingEvent.getTitle()).contains("会議");
        assertThat(meetingNote.getTitle()).contains("会議");

        // 検索で両方が見つかることを確認
        List<Note> meetingNotes = noteService.searchNotes("会議");
        List<Event> meetingEvents = eventService.searchEventsByTitle("会議");
        
        assertThat(meetingNotes).hasSize(1);
        assertThat(meetingEvents).hasSize(1);
    }

    @Test
    @Order(4)
    @DisplayName("日付範囲での予定検索が正常に動作する")
    void testDateRangeEventSearch() throws Exception {
        // Given
        LocalDateTime today = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        LocalDateTime tomorrow = today.plusDays(1);
        LocalDateTime nextWeek = today.plusDays(7);
        
        long todayEpoch = today.atZone(ZoneId.systemDefault()).toEpochSecond();
        long tomorrowEpoch = tomorrow.atZone(ZoneId.systemDefault()).toEpochSecond();
        long nextWeekEpoch = nextWeek.atZone(ZoneId.systemDefault()).toEpochSecond();

        // 今日の予定
        Event todayEvent = eventService.createEvent("今日の予定", todayEpoch, todayEpoch + 3600);
        // 明日の予定
        Event tomorrowEvent = eventService.createEvent("明日の予定", tomorrowEpoch, tomorrowEpoch + 3600);
        // 来週の予定
        Event nextWeekEvent = eventService.createEvent("来週の予定", nextWeekEpoch, nextWeekEpoch + 3600);

        // When - 今日から明日までの予定を検索
        List<Event> todayToTomorrowEvents = eventService.getEventsBetween(todayEpoch, tomorrowEpoch + 3600);

        // Then
        assertThat(todayToTomorrowEvents).hasSize(2);
        assertThat(todayToTomorrowEvents.stream().anyMatch(e -> e.getTitle().contains("今日"))).isTrue();
        assertThat(todayToTomorrowEvents.stream().anyMatch(e -> e.getTitle().contains("明日"))).isTrue();
        assertThat(todayToTomorrowEvents.stream().anyMatch(e -> e.getTitle().contains("来週"))).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("複雑な検索シナリオが正常に動作する")
    void testComplexSearchScenario() throws Exception {
        // Given
        // 様々な内容のメモと予定を作成
        noteService.createNote("Java勉強会の準備", "Javaの基礎を復習する必要がある");
        noteService.createNote("データベース設計書", "ER図を作成してテーブル設計を検討");
        noteService.createNote("テスト計画書", "単体テストと統合テストの計画を立てる");
        
        LocalDateTime baseTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long baseEpoch = baseTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        
        eventService.createEvent("Java勉強会", baseEpoch, baseEpoch + 7200);
        eventService.createEvent("データベース設計レビュー", baseEpoch + 7200, baseEpoch + 10800);
        eventService.createEvent("テスト実装", baseEpoch + 10800, baseEpoch + 14400);

        // When - 複数のキーワードで検索
        List<Note> javaNotes = noteService.searchNotes("Java");
        List<Event> javaEvents = eventService.searchEventsByTitle("Java");
        List<Note> dbNotes = noteService.searchNotes("データベース");
        List<Event> dbEvents = eventService.searchEventsByTitle("データベース");
        List<Note> testNotes = noteService.searchNotes("テスト");
        List<Event> testEvents = eventService.searchEventsByTitle("テスト");

        // Then
        assertThat(javaNotes).hasSize(1);
        assertThat(javaEvents).hasSize(1);
        assertThat(dbNotes).hasSize(1);
        assertThat(dbEvents).hasSize(1);
        assertThat(testNotes).hasSize(1);
        assertThat(testEvents).hasSize(1);
    }

    @Test
    @Order(6)
    @DisplayName("データの永続化が正常に動作する")
    void testDataPersistence() throws Exception {
        // Given
        Note note = noteService.createNote("永続化テストメモ", "このメモは永続化されるべきです");
        Event event = eventService.createEventFromNow("永続化テスト予定");

        // When - 新しいサービスインスタンスを作成（データベースは同じ）
        NotesDao newNotesDao = new NotesDao(database);
        EventsDao newEventsDao = new EventsDao(database);
        TransactionManager newTransactionManager = new TransactionManager(database);
        NoteService newNoteService = new NoteService(newNotesDao, newTransactionManager);
        EventService newEventService = new EventService(newEventsDao, newTransactionManager);

        // Then - データが永続化されていることを確認
        Note persistedNote = newNoteService.getRecentNotes().stream()
            .filter(n -> n.getTitle().contains("永続化テスト"))
            .findFirst()
            .orElse(null);
        
        List<Event> persistedEvents = newEventService.getEventsBetween(0, Long.MAX_VALUE);
        Event persistedEvent = persistedEvents.stream()
            .filter(e -> e.getTitle().contains("永続化テスト"))
            .findFirst()
            .orElse(null);

        assertThat(persistedNote).isNotNull();
        assertThat(persistedNote.getTitle()).isEqualTo("永続化テストメモ");
        assertThat(persistedNote.getBody()).isEqualTo("このメモは永続化されるべきです");
        
        assertThat(persistedEvent).isNotNull();
        assertThat(persistedEvent.getTitle()).isEqualTo("永続化テスト予定");
    }

    @Test
    @Order(7)
    @DisplayName("エラーハンドリングが正常に動作する")
    void testErrorHandling() throws Exception {
        // When & Then - 無効なデータでの操作
        assertThatThrownBy(() -> {
            eventService.createEvent("無効な予定", 1000, 500); // 開始時刻 > 終了時刻
        }).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> {
            Note invalidNote = new Note();
            invalidNote.setId(0); // 無効なID
            noteService.updateNote(invalidNote);
        }).isInstanceOf(IllegalArgumentException.class);

        // 正常なデータは影響を受けないことを確認
        List<Note> notes = noteService.getRecentNotes();
        List<Event> events = eventService.getEventsBetween(0, Long.MAX_VALUE);
        
        assertThat(notes).isEmpty();
        assertThat(events).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("パフォーマンステスト")
    void testPerformance() throws Exception {
        // Given
        long startTime = System.currentTimeMillis();

        // When - 大量のデータ操作を実行
        for (int i = 0; i < 100; i++) {
            noteService.createNote("パフォーマンステストメモ" + i, "内容" + i);
            eventService.createEventFromNow("パフォーマンステスト予定" + i);
        }

        // 検索操作
        List<Note> searchResults = noteService.searchNotes("パフォーマンス");
        List<Event> eventSearchResults = eventService.searchEventsByTitle("パフォーマンス");

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then
        assertThat(searchResults).hasSize(100);
        assertThat(eventSearchResults).hasSize(100);
        
        // パフォーマンスの確認（500ms以内に完了することを期待）
        assertThat(duration).isLessThan(500);
    }

    @Test
    @Order(9)
    @DisplayName("メモリリークテスト")
    void testMemoryLeak() throws Exception {
        // Given
        Runtime runtime = Runtime.getRuntime();
        System.gc(); // ガベージコレクションを実行
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // When - 大量のデータを作成・削除を繰り返す
        for (int cycle = 0; cycle < 10; cycle++) {
            // データを作成
            for (int i = 0; i < 50; i++) {
                Note note = noteService.createNote("サイクル" + cycle + "メモ" + i, "内容");
                Event event = eventService.createEventFromNow("サイクル" + cycle + "予定" + i);
            }
            
            // データを削除
            List<Note> notes = noteService.getRecentNotes();
            List<Event> events = eventService.getEventsBetween(0, Long.MAX_VALUE);
            
            for (Note note : notes) {
                noteService.deleteNote(note.getId());
            }
            for (Event event : events) {
                eventService.deleteEvent(event.getId());
            }
            
            // ガベージコレクションを実行
            System.gc();
        }

        // Then
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // メモリ増加量が合理的な範囲内であることを確認（5MB以下）
        assertThat(memoryIncrease).isLessThan(5 * 1024 * 1024);
        
        // データが正しく削除されていることを確認
        List<Note> remainingNotes = noteService.getRecentNotes();
        List<Event> remainingEvents = eventService.getEventsBetween(0, Long.MAX_VALUE);
        
        assertThat(remainingNotes).isEmpty();
        assertThat(remainingEvents).isEmpty();
    }

    @Test
    @Order(10)
    @DisplayName("アプリケーションの完全なワークフローテスト")
    void testCompleteWorkflow() throws Exception {
        // Given - アプリケーションの典型的な使用シナリオ
        
        // 1. 新しいメモを作成
        Note projectNote = noteService.createNote("プロジェクト計画", "新しいプロジェクトの計画を立てる");
        
        // 2. 関連する予定を作成
        LocalDateTime baseTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long baseEpoch = baseTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        Event planningEvent = eventService.createEvent("プロジェクト計画会議", baseEpoch, baseEpoch + 7200);
        
        // 3. メモを更新
        projectNote.setTitle("更新されたプロジェクト計画");
        projectNote.setBody("計画を詳細化しました。要件定義、設計、実装のフェーズに分けます。");
        noteService.updateNote(projectNote);
        
        // 4. 追加の予定を作成
        Event designEvent = eventService.createEvent("設計フェーズ", baseEpoch + 86400, baseEpoch + 86400 + 10800);
        Event implementationEvent = eventService.createEvent("実装フェーズ", baseEpoch + 172800, baseEpoch + 172800 + 14400);
        
        // 5. 検索で関連データを取得
        List<Note> projectNotes = noteService.searchNotes("プロジェクト");
        List<Event> projectEvents = eventService.searchEventsByTitle("プロジェクト");
        List<Event> allEvents = eventService.getEventsBetween(baseEpoch, baseEpoch + 200000);

        // Then - 全ての操作が正常に完了していることを確認
        assertThat(projectNotes).hasSize(1);
        assertThat(projectNotes.get(0).getTitle()).isEqualTo("更新されたプロジェクト計画");
        assertThat(projectNotes.get(0).getBody()).contains("要件定義");
        
        assertThat(projectEvents).hasSize(1);
        assertThat(projectEvents.get(0).getTitle()).isEqualTo("プロジェクト計画会議");
        
        assertThat(allEvents).hasSize(3);
        assertThat(allEvents.stream().anyMatch(e -> e.getTitle().contains("計画"))).isTrue();
        assertThat(allEvents.stream().anyMatch(e -> e.getTitle().contains("設計"))).isTrue();
        assertThat(allEvents.stream().anyMatch(e -> e.getTitle().contains("実装"))).isTrue();
    }
}