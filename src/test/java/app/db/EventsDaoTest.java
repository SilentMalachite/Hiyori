package app.db;

import app.exception.DataAccessException;
import app.model.Event;
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
 * EventsDaoのテスト
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventsDaoTest {

    private TestDatabase testDb;
    private EventsDao eventsDao;

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
        TransactionManager tm = new TransactionManager(testDb.getDatabase());
        eventsDao = new EventsDao(testDb.getDatabase(), tm);
    }

    @AfterEach
    void tearDown() {
        if (testDb != null) {
            testDb.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("予定の挿入が正常に動作する")
    void testInsertEvent() throws DataAccessException {
        // Given
        Event event = TestDataFactory.createEvent("テスト予定", 9, 11);

        // When
        long id = eventsDao.insert(event.getTitle(), event.getStartEpochSec(), event.getEndEpochSec());

        // Then
        assertThat(id).isGreaterThan(0);
    }

    @Test
    @Order(2)
    @DisplayName("予定の取得が正常に動作する")
    void testGetEventById() throws DataAccessException {
        // Given
        Event originalEvent = TestDataFactory.createEvent("テスト予定", 9, 11);
        long id = eventsDao.insert(originalEvent.getTitle(), originalEvent.getStartEpochSec(), originalEvent.getEndEpochSec());

        // When
        Event retrievedEvent = eventsDao.get(id);

        // Then
        assertThat(retrievedEvent).isNotNull();
        assertThat(retrievedEvent.getId()).isEqualTo(id);
        assertThat(retrievedEvent.getTitle()).isEqualTo("テスト予定");
        assertThat(retrievedEvent.getStartEpochSec()).isEqualTo(originalEvent.getStartEpochSec());
        assertThat(retrievedEvent.getEndEpochSec()).isEqualTo(originalEvent.getEndEpochSec());
    }

    @Test
    @Order(3)
    @DisplayName("存在しない予定の取得はnullを返す")
    void testGetNonExistentEvent() throws DataAccessException {
        // When
        Event event = eventsDao.get(999L);

        // Then
        assertThat(event).isNull();
    }

    @Test
    @Order(4)
    @DisplayName("予定の更新が正常に動作する")
    void testUpdateEvent() throws DataAccessException {
        // Given
        Event event = TestDataFactory.createEvent("元のタイトル", 9, 11);
        long id = eventsDao.insert(event.getTitle(), event.getStartEpochSec(), event.getEndEpochSec());
        
        event.setId(id);
        event.setTitle("更新されたタイトル");
        event.setStartEpochSec(event.getStartEpochSec() + 3600); // 1時間後
        event.setEndEpochSec(event.getEndEpochSec() + 3600);

        // When
        eventsDao.update(event);

        // Then
        Event updatedEvent = eventsDao.get(id);
        assertThat(updatedEvent.getTitle()).isEqualTo("更新されたタイトル");
        assertThat(updatedEvent.getStartEpochSec()).isEqualTo(event.getStartEpochSec());
        assertThat(updatedEvent.getEndEpochSec()).isEqualTo(event.getEndEpochSec());
    }

    @Test
    @Order(5)
    @DisplayName("予定の削除が正常に動作する")
    void testDeleteEvent() throws DataAccessException {
        // Given
        Event event = TestDataFactory.createEvent("削除対象予定", 9, 11);
        long id = eventsDao.insert(event.getTitle(), event.getStartEpochSec(), event.getEndEpochSec());

        // When
        eventsDao.delete(id);

        // Then
        Event deletedEvent = eventsDao.get(id);
        assertThat(deletedEvent).isNull();
    }

    @Test
    @Order(6)
    @DisplayName("期間内の予定一覧が正常に取得できる")
    void testListEventsBetween() throws DataAccessException {
        // Given
        LocalDateTime baseTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long baseEpoch = baseTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        
        // 複数の予定を作成
        eventsDao.insert("予定1", baseEpoch, baseEpoch + 3600);           // 9:00-10:00
        eventsDao.insert("予定2", baseEpoch + 3600, baseEpoch + 7200);    // 10:00-11:00
        eventsDao.insert("予定3", baseEpoch + 7200, baseEpoch + 10800);   // 11:00-12:00
        eventsDao.insert("予定4", baseEpoch + 10800, baseEpoch + 14400);  // 12:00-13:00

        // When - 10:00-12:00の期間で検索
        List<Event> events = eventsDao.listBetween(baseEpoch + 3600, baseEpoch + 10800);

        // Then
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getTitle()).isEqualTo("予定2");
        assertThat(events.get(1).getTitle()).isEqualTo("予定3");
    }

    @Test
    @Order(7)
    @DisplayName("予定のタイトル検索が正常に動作する")
    void testSearchEventsByTitle() throws DataAccessException {
        // Given
        List<Event> searchTestEvents = TestDataFactory.createSearchTestEvents();
        for (Event event : searchTestEvents) {
            eventsDao.insert(event.getTitle(), event.getStartEpochSec(), event.getEndEpochSec());
        }

        // When
        List<Event> javaResults = eventsDao.searchByTitle("Java", 10);
        List<Event> databaseResults = eventsDao.searchByTitle("データベース", 10);
        List<Event> noResults = eventsDao.searchByTitle("存在しないキーワード", 10);

        // Then
        assertThat(javaResults).hasSize(1);
        assertThat(javaResults.get(0).getTitle()).contains("Java");
        
        assertThat(databaseResults).hasSize(1);
        assertThat(databaseResults.get(0).getTitle()).contains("データベース");
        
        assertThat(noResults).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("存在しない予定の更新は例外を投げる")
    void testUpdateNonExistentEvent() {
        // Given
        Event nonExistentEvent = TestDataFactory.createEventWithId(999L, "存在しない", 0, 3600);

        // When & Then
        assertThatThrownBy(() -> eventsDao.update(nonExistentEvent))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("更新対象の予定が見つかりませんでした");
    }

    @Test
    @Order(9)
    @DisplayName("存在しない予定の削除は例外を投げる")
    void testDeleteNonExistentEvent() {
        // When & Then
        assertThatThrownBy(() -> eventsDao.delete(999L))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("削除対象の予定が見つかりませんでした");
    }

    @Test
    @Order(10)
    @DisplayName("長いタイトルの予定が正常に処理される")
    void testLongTitleEvent() throws DataAccessException {
        // Given
        Event longTitleEvent = TestDataFactory.createEventWithLongTitle();

        // When
        long id = eventsDao.insert(longTitleEvent.getTitle(), longTitleEvent.getStartEpochSec(), longTitleEvent.getEndEpochSec());
        Event retrievedEvent = eventsDao.get(id);

        // Then
        assertThat(retrievedEvent).isNotNull();
        assertThat(retrievedEvent.getTitle()).hasSizeGreaterThan(100);
    }

    @Test
    @Order(11)
    @DisplayName("特殊文字を含む予定タイトルが正常に処理される")
    void testSpecialCharactersEvent() throws DataAccessException {
        // Given
        String specialTitle = "特殊文字テスト: !@#$%^&*()_+{}|:<>?[]\\;'\",./";
        Event event = TestDataFactory.createEvent(specialTitle, 9, 10);

        // When
        long id = eventsDao.insert(event.getTitle(), event.getStartEpochSec(), event.getEndEpochSec());
        Event retrievedEvent = eventsDao.get(id);

        // Then
        assertThat(retrievedEvent).isNotNull();
        assertThat(retrievedEvent.getTitle()).isEqualTo(specialTitle);
    }

    @Test
    @Order(12)
    @DisplayName("検索結果の制限数が正常に動作する")
    void testSearchWithLimit() throws DataAccessException {
        // Given
        List<Event> events = TestDataFactory.createEvents(10);
        for (Event event : events) {
            eventsDao.insert(event.getTitle(), event.getStartEpochSec(), event.getEndEpochSec());
        }

        // When
        List<Event> limitedResults = eventsDao.searchByTitle("テスト", 3);

        // Then
        assertThat(limitedResults).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @Order(13)
    @DisplayName("期間検索で重複する予定が正しく取得される")
    void testOverlappingEvents() throws DataAccessException {
        // Given
        LocalDateTime baseTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long baseEpoch = baseTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        
        // 重複する予定を作成
        eventsDao.insert("予定A", baseEpoch, baseEpoch + 7200);           // 9:00-11:00
        eventsDao.insert("予定B", baseEpoch + 3600, baseEpoch + 10800);   // 10:00-12:00
        eventsDao.insert("予定C", baseEpoch + 7200, baseEpoch + 14400);   // 11:00-13:00

        // When - 10:00-12:00の期間で検索（全ての予定が重複）
        List<Event> events = eventsDao.listBetween(baseEpoch + 3600, baseEpoch + 10800);

        // Then
        assertThat(events).hasSize(3);
    }

    @Test
    @Order(14)
    @DisplayName("空の期間検索は空のリストを返す")
    void testEmptyPeriodSearch() throws DataAccessException {
        // Given
        LocalDateTime baseTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long baseEpoch = baseTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        
        eventsDao.insert("予定", baseEpoch, baseEpoch + 3600);

        // When - 予定と重複しない期間で検索
        List<Event> events = eventsDao.listBetween(baseEpoch + 7200, baseEpoch + 10800);

        // Then
        assertThat(events).isEmpty();
    }

    @Test
    @Order(15)
    @DisplayName("予定の開始時刻でソートされる")
    void testEventsSortedByStartTime() throws DataAccessException {
        // Given
        LocalDateTime baseTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long baseEpoch = baseTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        
        // 逆順で予定を作成
        eventsDao.insert("予定3", baseEpoch + 7200, baseEpoch + 10800);   // 11:00-12:00
        eventsDao.insert("予定1", baseEpoch, baseEpoch + 3600);           // 9:00-10:00
        eventsDao.insert("予定2", baseEpoch + 3600, baseEpoch + 7200);    // 10:00-11:00

        // When
        List<Event> events = eventsDao.listBetween(baseEpoch, baseEpoch + 14400);

        // Then
        assertThat(events).hasSize(3);
        assertThat(events.get(0).getTitle()).isEqualTo("予定1");
        assertThat(events.get(1).getTitle()).isEqualTo("予定2");
        assertThat(events.get(2).getTitle()).isEqualTo("予定3");
    }

    @Test
    @Order(16)
    @DisplayName("LIKE検索でワイルドカード文字を含むタイトルを正しく検索できる")
    void testSearchByTitleWithLiteralWildcards() throws DataAccessException {
        long baseEpoch = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0)
                .atZone(ZoneId.systemDefault()).toEpochSecond();

        eventsDao.insert("達成率100%", baseEpoch, baseEpoch + 3600);
        eventsDao.insert("タスク_A_レビュー", baseEpoch + 3600, baseEpoch + 7200);
        eventsDao.insert("通常予定", baseEpoch + 7200, baseEpoch + 10800);

        List<Event> percentResults = eventsDao.searchByTitle("100%", 10);
        List<Event> underscoreResults = eventsDao.searchByTitle("タスク_A_", 10);

        assertThat(percentResults).extracting(Event::getTitle).containsExactly("達成率100%");
        assertThat(underscoreResults).extracting(Event::getTitle).containsExactly("タスク_A_レビュー");
    }
}