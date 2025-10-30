package app.service;

import app.config.AppConfig;
import app.db.EventsDao;
import app.db.TransactionManager;
import app.exception.DataAccessException;
import app.model.Event;
import app.testutil.TestDataFactory;
import app.testutil.TestDatabase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EventServiceのテスト
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventServiceTest {

    private TestDatabase testDb;
    private EventsDao eventsDao;
    private TransactionManager transactionManager;
    private EventService eventService;

    @Mock
    private AppConfig mockConfig;

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
        transactionManager = new TransactionManager(testDb.getDatabase());
        eventsDao = new EventsDao(testDb.getDatabase(), transactionManager);
        
        // Mock設定
        when(mockConfig.getSearchEventsLimit()).thenReturn(200);
        when(mockConfig.getEventDefaultDurationMinutes()).thenReturn(90);
        when(mockConfig.getEventMinDurationMinutes()).thenReturn(5);
        
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
    @DisplayName("期間内の予定一覧が正常に取得できる")
    void testGetEventsBetween() throws DataAccessException {
        // Given
        LocalDateTime baseTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long baseEpoch = baseTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        
        eventsDao.insert("予定1", baseEpoch, baseEpoch + 3600);
        eventsDao.insert("予定2", baseEpoch + 3600, baseEpoch + 7200);
        eventsDao.insert("予定3", baseEpoch + 7200, baseEpoch + 10800);

        // When
        List<Event> events = eventService.getEventsBetween(baseEpoch + 1800, baseEpoch + 9000);

        // Then
        assertThat(events).hasSize(3);
    }

    @Test
    @Order(2)
    @DisplayName("予定のタイトル検索が正常に動作する")
    void testSearchEventsByTitle() throws DataAccessException {
        // Given
        List<Event> searchTestEvents = TestDataFactory.createSearchTestEvents();
        for (Event event : searchTestEvents) {
            eventsDao.insert(event.getTitle(), event.getStartEpochSec(), event.getEndEpochSec());
        }

        // When
        List<Event> javaResults = eventService.searchEventsByTitle("Java");
        List<Event> databaseResults = eventService.searchEventsByTitle("データベース");
        List<Event> emptyResults = eventService.searchEventsByTitle("");

        // Then
        assertThat(javaResults).hasSize(1);
        assertThat(javaResults.get(0).getTitle()).contains("Java");
        
        assertThat(databaseResults).hasSize(1);
        assertThat(databaseResults.get(0).getTitle()).contains("データベース");
        
        assertThat(emptyResults).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("新しい予定が正常に作成できる")
    void testCreateEvent() throws DataAccessException {
        // Given
        LocalDateTime startTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long startEpoch = startTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endEpoch = startEpoch + 3600; // 1時間後

        // When
        Event event = eventService.createEvent("新規予定", startEpoch, endEpoch);

        // Then
        assertThat(event).isNotNull();
        assertThat(event.getId()).isGreaterThan(0);
        assertThat(event.getTitle()).isEqualTo("新規予定");
        assertThat(event.getStartEpochSec()).isEqualTo(startEpoch);
        assertThat(event.getEndEpochSec()).isEqualTo(endEpoch);
    }

    @Test
    @Order(4)
    @DisplayName("現在時刻からデフォルト時間の予定が作成できる")
    void testCreateEventFromNow() throws DataAccessException {
        // When
        Event event = eventService.createEventFromNow("現在からの予定");

        // Then
        assertThat(event).isNotNull();
        assertThat(event.getTitle()).isEqualTo("現在からの予定");
        assertThat(event.getEndEpochSec()).isGreaterThan(event.getStartEpochSec());
        
        // デフォルト時間（90分）であることを確認
        long duration = event.getEndEpochSec() - event.getStartEpochSec();
        assertThat(duration).isEqualTo(90 * 60); // 90分
    }

    @Test
    @Order(5)
    @DisplayName("予定の更新が正常に動作する")
    void testUpdateEvent() throws DataAccessException {
        // Given
        Event event = eventService.createEventFromNow("元のタイトル");
        long eventId = event.getId();

        // When
        event.setTitle("更新されたタイトル");
        event.setStartEpochSec(event.getStartEpochSec() + 3600);
        event.setEndEpochSec(event.getEndEpochSec() + 3600);
        eventService.updateEvent(event);

        // Then
        Event updatedEvent = eventsDao.get(eventId);
        assertThat(updatedEvent.getTitle()).isEqualTo("更新されたタイトル");
        assertThat(updatedEvent.getStartEpochSec()).isEqualTo(event.getStartEpochSec());
        assertThat(updatedEvent.getEndEpochSec()).isEqualTo(event.getEndEpochSec());
    }

    @Test
    @Order(6)
    @DisplayName("予定の削除が正常に動作する")
    void testDeleteEvent() throws DataAccessException {
        // Given
        Event event = eventService.createEventFromNow("削除対象予定");
        long eventId = event.getId();

        // When
        eventService.deleteEvent(eventId);

        // Then
        Event deletedEvent = eventsDao.get(eventId);
        assertThat(deletedEvent).isNull();
    }

    @Test
    @Order(7)
    @DisplayName("予定の取得が正常に動作する")
    void testGetEvent() throws DataAccessException {
        // Given
        Event originalEvent = eventService.createEventFromNow("取得テスト予定");

        // When
        Event retrievedEvent = eventService.getEvent(originalEvent.getId());

        // Then
        assertThat(retrievedEvent).isNotNull();
        assertThat(retrievedEvent.getId()).isEqualTo(originalEvent.getId());
        assertThat(retrievedEvent.getTitle()).isEqualTo("取得テスト予定");
    }

    @Test
    @Order(8)
    @DisplayName("存在しない予定の取得はnullを返す")
    void testGetNonExistentEvent() throws DataAccessException {
        // When
        Event event = eventService.getEvent(999L);

        // Then
        assertThat(event).isNull();
    }

    @Test
    @Order(9)
    @DisplayName("予定の存在確認が正常に動作する")
    void testEventExists() throws DataAccessException {
        // Given
        Event event = eventService.createEventFromNow("存在確認テスト");

        // When & Then
        assertThat(eventService.eventExists(event.getId())).isTrue();
        assertThat(eventService.eventExists(999L)).isFalse();
    }

    @Test
    @Order(10)
    @DisplayName("開始時刻が終了時刻より後の場合は例外が投げられる")
    void testCreateEventWithInvalidTimes() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().withHour(10).withMinute(0).withSecond(0);
        long startEpoch = startTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endEpoch = startEpoch - 3600; // 開始時刻より1時間前

        // When & Then
        assertThatThrownBy(() -> eventService.createEvent("無効な予定", startEpoch, endEpoch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Start time must be before end time");
    }

    @Test
    @Order(11)
    @DisplayName("最小時間未満の予定は例外が投げられる")
    void testCreateEventWithTooShortDuration() {
        // Given
        LocalDateTime startTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long startEpoch = startTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endEpoch = startEpoch + 60; // 1分（最小時間5分未満）

        // When & Then
        assertThatThrownBy(() -> eventService.createEvent("短すぎる予定", startEpoch, endEpoch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Event duration must be at least 5 minutes");
    }

    @Test
    @Order(12)
    @DisplayName("負の時刻の予定は例外が投げられる")
    void testCreateEventWithNegativeTimes() {
        // When & Then
        assertThatThrownBy(() -> eventService.createEvent("負の時刻", -1, 3600))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Event times must be positive");
    }

    @Test
    @Order(13)
    @DisplayName("nullの予定で更新しようとすると例外が投げられる")
    void testUpdateNullEvent() {
        // When & Then
        assertThatThrownBy(() -> eventService.updateEvent(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Event and event ID must not be null or invalid");
    }

    @Test
    @Order(14)
    @DisplayName("無効なIDの予定で更新しようとすると例外が投げられる")
    void testUpdateEventWithInvalidId() {
        // Given
        Event event = TestDataFactory.createEvent();
        event.setId(0); // 無効なID

        // When & Then
        assertThatThrownBy(() -> eventService.updateEvent(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Event and event ID must not be null or invalid");
    }

    @Test
    @Order(15)
    @DisplayName("長いタイトルの予定が正常に処理される")
    void testLongTitleEvent() throws DataAccessException {
        // Given
        Event longTitleEvent = TestDataFactory.createEventWithLongTitle();

        // When
        Event createdEvent = eventService.createEvent(
            longTitleEvent.getTitle(), 
            longTitleEvent.getStartEpochSec(), 
            longTitleEvent.getEndEpochSec()
        );

        // Then
        assertThat(createdEvent).isNotNull();
        assertThat(createdEvent.getTitle()).hasSizeGreaterThan(100);
    }

    @Test
    @Order(16)
    @DisplayName("特殊文字を含む予定タイトルが正常に処理される")
    void testSpecialCharactersEvent() throws DataAccessException {
        // Given
        String specialTitle = "特殊文字テスト: !@#$%^&*()_+{}|:<>?[]\\;'\",./";
        LocalDateTime startTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long startEpoch = startTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endEpoch = startEpoch + 3600;

        // When
        Event event = eventService.createEvent(specialTitle, startEpoch, endEpoch);

        // Then
        assertThat(event).isNotNull();
        assertThat(event.getTitle()).isEqualTo(specialTitle);
    }

    @Test
    @Order(17)
    @DisplayName("トランザクション内で予定操作が正常に動作する")
    void testTransactionHandling() throws DataAccessException {
        // When - 複数の操作をトランザクション内で実行
        Event event1 = eventService.createEventFromNow("予定1");
        Event event2 = eventService.createEventFromNow("予定2");
        
        event1.setTitle("更新された予定1");
        eventService.updateEvent(event1);
        
        eventService.deleteEvent(event2.getId());

        // Then
        List<Event> allEvents = eventService.getEventsBetween(0, Long.MAX_VALUE);
        assertThat(allEvents).hasSize(1);
        assertThat(allEvents.get(0).getTitle()).isEqualTo("更新された予定1");
    }

    @Test
    @Order(18)
    @DisplayName("検索でnullクエリは空のリストを返す")
    void testSearchWithNullQuery() throws DataAccessException {
        // Given
        List<Event> events = TestDataFactory.createEvents(3);
        for (Event event : events) {
            eventsDao.insert(event.getTitle(), event.getStartEpochSec(), event.getEndEpochSec());
        }

        // When
        List<Event> results = eventService.searchEventsByTitle(null);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @Order(19)
    @DisplayName("検索で空白のみのクエリは空のリストを返す")
    void testSearchWithBlankQuery() throws DataAccessException {
        // Given
        List<Event> events = TestDataFactory.createEvents(3);
        for (Event event : events) {
            eventsDao.insert(event.getTitle(), event.getStartEpochSec(), event.getEndEpochSec());
        }

        // When
        List<Event> results = eventService.searchEventsByTitle("   ");

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @Order(20)
    @DisplayName("予定の時間検証が正常に動作する")
    void testTimeValidation() throws DataAccessException {
        // Given
        LocalDateTime startTime = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0);
        long startEpoch = startTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endEpoch = startEpoch + 300; // 5分（最小時間）

        // When
        Event event = eventService.createEvent("最小時間予定", startEpoch, endEpoch);

        // Then
        assertThat(event).isNotNull();
        assertThat(event.getEndEpochSec() - event.getStartEpochSec()).isEqualTo(300);
    }

    @Test
    @Order(21)
    @DisplayName("存在しないIDの削除は DataAccessException を投げる")
    void testDeleteNonExistentEventThrows() {
        // Given - データベースに存在しないIDを指定
        long nonExistentId = 999999L;

        // When & Then
        assertThatThrownBy(() -> eventService.deleteEvent(nonExistentId))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("削除対象の予定が見つかりません");
    }

    @Test
    @Order(22)
    @DisplayName("updateEvent: 最小時間未満に変更すると IllegalArgumentException")
    void testUpdateEventTooShortDurationThrows() throws DataAccessException {
        // Given - まず有効な予定を作成
        Event event = eventService.createEventFromNow("短時間更新テスト");
        // 最小時間(既定5分)未満に短縮
        long newStart = event.getStartEpochSec();
        long newEnd = newStart + 60; // 1分
        event.setStartEpochSec(newStart);
        event.setEndEpochSec(newEnd);

        // When & Then
        assertThatThrownBy(() -> eventService.updateEvent(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Event duration must be at least");
    }

    @Test
    @Order(23)
    @DisplayName("searchEventsByTitle: 前後空白はtrimされ、search.events.limitが適用される")
    void testSearchEventsTrimAndLimit() throws DataAccessException {
        // Given - limitを3に設定
        AppConfig.getInstance().setProperty("search.events.limit", "3");

        // 同一キーワードで複数件登録
        long base = LocalDateTime.now().withHour(9).withMinute(0).withSecond(0).withNano(0)
                .atZone(ZoneId.systemDefault()).toEpochSecond();
        for (int i = 0; i < 5; i++) {
            eventsDao.insert("限界テスト" + i, base + i * 3600, base + (i + 1) * 3600);
        }

        // When - 前後に空白付きクエリで検索
        List<Event> results = eventService.searchEventsByTitle("   限界テスト   ");

        // Then - trimによりヒットし、limitにより3件以内
        assertThat(results.size()).isLessThanOrEqualTo(3);
        assertThat(results).allMatch(e -> e.getTitle().startsWith("限界テスト"));
    }

    @Test
    @Order(24)
    @DisplayName("createEventFromNow: event.default.duration.minutes の設定を尊重する")
    void testCreateEventFromNowRespectsConfigDuration() throws DataAccessException {
        // Given - 既定の90分とは異なる値に変更
        AppConfig.getInstance().setProperty("event.default.duration.minutes", "45");

        // When
        Event e = eventService.createEventFromNow("デフォルト時間変更テスト");

        // Then
        long durationSec = e.getEndEpochSec() - e.getStartEpochSec();
        assertThat(durationSec).isEqualTo(45 * 60);

        // 後続テストに影響させないため、元に戻す
        AppConfig.getInstance().setProperty("event.default.duration.minutes", "90");
    }
}
