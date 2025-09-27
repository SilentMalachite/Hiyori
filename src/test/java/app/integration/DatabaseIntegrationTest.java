package app.integration;

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
 * データベース統合テスト
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseIntegrationTest {

    private TestDatabase testDb;
    private Database database;
    private NotesDao notesDao;
    private EventsDao eventsDao;
    private TransactionManager transactionManager;
    private NoteService noteService;
    private EventService eventService;

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
    }

    @AfterEach
    void tearDown() {
        if (testDb != null) {
            testDb.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("メモと予定の横断検索が正常に動作する")
    void testCrossEntitySearch() throws Exception {
        // Given
        // メモデータを作成
        Note note1 = noteService.createNote("Java勉強会のメモ", "今日のJava勉強会の内容をまとめました。");
        Note note2 = noteService.createNote("データベース設計", "SQLiteの設計について考えました。");
        
        // 予定データを作成
        LocalDateTime baseTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long baseEpoch = baseTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        Event event1 = eventService.createEvent("Java勉強会", baseEpoch, baseEpoch + 7200);
        Event event2 = eventService.createEvent("データベース研修", baseEpoch + 7200, baseEpoch + 14400);

        // When
        List<Note> javaNotes = noteService.searchNotes("Java");
        List<Event> javaEvents = eventService.searchEventsByTitle("Java");
        List<Note> dbNotes = noteService.searchNotes("データベース");
        List<Event> dbEvents = eventService.searchEventsByTitle("データベース");

        // Then
        assertThat(javaNotes).hasSize(1);
        assertThat(javaNotes.get(0).getTitle()).contains("Java");
        assertThat(javaEvents).hasSize(1);
        assertThat(javaEvents.get(0).getTitle()).contains("Java");
        
        assertThat(dbNotes).hasSize(1);
        assertThat(dbNotes.get(0).getTitle()).contains("データベース");
        assertThat(dbEvents).hasSize(1);
        assertThat(dbEvents.get(0).getTitle()).contains("データベース");
    }

    @Test
    @Order(2)
    @DisplayName("複雑なトランザクション処理が正常に動作する")
    void testComplexTransaction() throws Exception {
        // Given
        LocalDateTime baseTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long baseEpoch = baseTime.atZone(ZoneId.systemDefault()).toEpochSecond();

        // When - 複数の操作を一つのトランザクション内で実行
        transactionManager.executeInTransaction(() -> {
            // メモを作成
            Note note = noteService.createNote("会議メモ", "今日の会議の内容");
            
            // 関連する予定を作成
            Event event = eventService.createEvent("会議", baseEpoch, baseEpoch + 3600);
            
            // メモを更新
            note.setTitle("更新された会議メモ");
            noteService.updateNote(note);
            
            // 予定を更新
            event.setTitle("延長された会議");
            event.setEndEpochSec(event.getEndEpochSec() + 1800); // 30分延長
            eventService.updateEvent(event);
            
            return null;
        });

        // Then
        List<Note> notes = noteService.getRecentNotes();
        List<Event> events = eventService.getEventsBetween(baseEpoch, baseEpoch + 7200);
        
        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getTitle()).isEqualTo("更新された会議メモ");
        
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTitle()).isEqualTo("延長された会議");
        assertThat(events.get(0).getEndEpochSec() - events.get(0).getStartEpochSec()).isEqualTo(5400); // 90分
    }

    @Test
    @Order(3)
    @DisplayName("大量データの処理が正常に動作する")
    void testBulkDataProcessing() throws Exception {
        // Given
        List<Note> notes = TestDataFactory.createNotes(100);
        List<Event> events = TestDataFactory.createEvents(50);

        // When
        for (Note note : notes) {
            noteService.createNote(note.getTitle(), note.getBody());
        }
        
        for (Event event : events) {
            eventService.createEvent(event.getTitle(), event.getStartEpochSec(), event.getEndEpochSec());
        }

        // Then
        List<Note> allNotes = noteService.getRecentNotes();
        List<Event> allEvents = eventService.getEventsBetween(0, Long.MAX_VALUE);
        
        assertThat(allNotes).hasSize(100);
        assertThat(allEvents).hasSize(50);
    }

    @Test
    @Order(4)
    @DisplayName("データの整合性が保たれる")
    void testDataConsistency() throws Exception {
        // Given
        Note note = noteService.createNote("整合性テスト", "内容");
        Event event = eventService.createEventFromNow("整合性テスト予定");

        // When - データを更新
        note.setTitle("更新された整合性テスト");
        noteService.updateNote(note);
        
        event.setTitle("更新された整合性テスト予定");
        eventService.updateEvent(event);

        // Then - データが正しく更新されていることを確認
        Note updatedNote = notesDao.getById(note.getId());
        Event updatedEvent = eventsDao.get(event.getId());
        
        assertThat(updatedNote.getTitle()).isEqualTo("更新された整合性テスト");
        assertThat(updatedNote.getUpdatedAt()).isGreaterThan(note.getCreatedAt());
        
        assertThat(updatedEvent.getTitle()).isEqualTo("更新された整合性テスト予定");
    }

    @Test
    @Order(5)
    @DisplayName("並行処理でのデータ整合性が保たれる")
    void testConcurrentDataConsistency() throws Exception {
        // Given
        int threadCount = 5;
        int operationsPerThread = 10;
        
        // When - 複数のスレッドで並行してデータ操作を実行
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // メモを作成
                        Note note = noteService.createNote(
                            "スレッド" + threadId + "メモ" + j, 
                            "スレッド" + threadId + "の内容" + j
                        );
                        
                        // 予定を作成
                        Event event = eventService.createEventFromNow(
                            "スレッド" + threadId + "予定" + j
                        );
                        
                        // 少し待機
                        Thread.sleep(10);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            threads[i].start();
        }
        
        // 全スレッドの完了を待機
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - データが正しく保存されていることを確認
        List<Note> allNotes = noteService.getRecentNotes();
        List<Event> allEvents = eventService.getEventsBetween(0, Long.MAX_VALUE);
        
        assertThat(allNotes).hasSize(threadCount * operationsPerThread);
        assertThat(allEvents).hasSize(threadCount * operationsPerThread);
        
        // 重複がないことを確認
        long uniqueNoteIds = allNotes.stream().mapToLong(Note::getId).distinct().count();
        long uniqueEventIds = allEvents.stream().mapToLong(Event::getId).distinct().count();
        
        assertThat(uniqueNoteIds).isEqualTo(threadCount * operationsPerThread);
        assertThat(uniqueEventIds).isEqualTo(threadCount * operationsPerThread);
    }

    @Test
    @Order(6)
    @DisplayName("エラー時のロールバックが正常に動作する")
    void testRollbackOnError() throws Exception {
        // Given
        Note originalNote = noteService.createNote("元のメモ", "元の内容");
        Event originalEvent = eventService.createEventFromNow("元の予定");

        // When - トランザクション内でエラーが発生する操作を実行
        assertThatThrownBy(() -> {
            transactionManager.executeInTransaction(() -> {
                // 正常な操作
                Note note = noteService.createNote("新しいメモ", "新しい内容");
                
                // エラーを発生させる操作
                Event invalidEvent = new Event();
                invalidEvent.setId(999L); // 存在しないID
                invalidEvent.setTitle("無効な予定");
                invalidEvent.setStartEpochSec(0);
                invalidEvent.setEndEpochSec(3600);
                eventService.updateEvent(invalidEvent); // これでエラーが発生
                
                return null;
            });
        }).isInstanceOf(Exception.class);

        // Then - ロールバックが実行され、元のデータが保持されていることを確認
        Note rollbackNote = notesDao.getById(originalNote.getId());
        Event rollbackEvent = eventsDao.get(originalEvent.getId());
        
        assertThat(rollbackNote).isNotNull();
        assertThat(rollbackNote.getTitle()).isEqualTo("元のメモ");
        
        assertThat(rollbackEvent).isNotNull();
        assertThat(rollbackEvent.getTitle()).isEqualTo("元の予定");
        
        // 新しいメモは作成されていないことを確認
        List<Note> allNotes = noteService.getRecentNotes();
        assertThat(allNotes).hasSize(1);
        assertThat(allNotes.get(0).getId()).isEqualTo(originalNote.getId());
    }

    @Test
    @Order(7)
    @DisplayName("FTS検索の統合テスト")
    void testFTSIntegration() throws Exception {
        // Given
        List<Note> searchTestNotes = TestDataFactory.createSearchTestNotes();
        for (Note note : searchTestNotes) {
            noteService.createNote(note.getTitle(), note.getBody());
        }

        // When
        List<Note> javaResults = noteService.searchNotes("Java");
        List<Note> databaseResults = noteService.searchNotes("データベース");
        List<Note> tddResults = noteService.searchNotes("TDD");
        List<Note> architectureResults = noteService.searchNotes("アーキテクチャ");

        // Then
        assertThat(javaResults).hasSize(1);
        assertThat(javaResults.get(0).getTitle()).contains("Java");
        
        assertThat(databaseResults).hasSize(1);
        assertThat(databaseResults.get(0).getTitle()).contains("データベース");
        
        assertThat(tddResults).hasSize(1);
        assertThat(tddResults.get(0).getTitle()).contains("テスト駆動開発");
        
        assertThat(architectureResults).hasSize(1);
        assertThat(architectureResults.get(0).getTitle()).contains("アーキテクチャ");
    }

    @Test
    @Order(8)
    @DisplayName("データベース統計の確認")
    void testDatabaseStats() throws Exception {
        // Given
        testDb.insertSampleData();

        // When
        var stats = testDb.getStats();

        // Then
        assertThat(stats.notesCount).isEqualTo(3);
        assertThat(stats.eventsCount).isEqualTo(3);
    }

    @Test
    @Order(9)
    @DisplayName("長時間実行テスト")
    void testLongRunningOperations() throws Exception {
        // Given
        long startTime = System.currentTimeMillis();

        // When - 大量のデータ操作を実行
        for (int i = 0; i < 1000; i++) {
            Note note = noteService.createNote("長時間テストメモ" + i, "内容" + i);
            if (i % 100 == 0) {
                note.setTitle("更新されたメモ" + i);
                noteService.updateNote(note);
            }
        }

        // Then
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        List<Note> allNotes = noteService.getRecentNotes();
        assertThat(allNotes).hasSize(1000);
        
        // パフォーマンスの確認（1秒以内に完了することを期待）
        assertThat(duration).isLessThan(1000);
    }

    @Test
    @Order(10)
    @DisplayName("メモリ使用量の確認")
    void testMemoryUsage() throws Exception {
        // Given
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // When - 大量のデータを作成
        for (int i = 0; i < 500; i++) {
            Note note = noteService.createNote("メモリテストメモ" + i, "内容" + i);
            Event event = eventService.createEventFromNow("メモリテスト予定" + i);
        }

        // Then
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // メモリ増加量が合理的な範囲内であることを確認（10MB以下）
        assertThat(memoryIncrease).isLessThan(10 * 1024 * 1024);
        
        // データが正しく保存されていることを確認
        List<Note> allNotes = noteService.getRecentNotes();
        List<Event> allEvents = eventService.getEventsBetween(0, Long.MAX_VALUE);
        
        assertThat(allNotes).hasSize(500);
        assertThat(allEvents).hasSize(500);
    }
}