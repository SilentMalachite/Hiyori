package app.service;

import app.config.AppConfig;
import app.db.EventsDao;
import app.db.TransactionManager;
import app.db.ThrowingRunnable;
import app.db.ThrowingSupplier;
import app.exception.DataAccessException;
import app.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * 予定関連のビジネスロジックを担当するサービス
 */
public class EventService {
    private static final Logger logger = LoggerFactory.getLogger(EventService.class);
    private final EventsDao eventsDao;
    private final TransactionManager transactionManager;
    private final AppConfig config;

    public EventService(EventsDao eventsDao, TransactionManager transactionManager) {
        this.eventsDao = eventsDao;
        this.transactionManager = transactionManager;
        this.config = AppConfig.getInstance();
    }

    /**
     * 指定期間の予定一覧を取得する
     * @param startEpochSec 開始時刻（エポック秒）
     * @param endEpochSec 終了時刻（エポック秒）
     * @return 予定のリスト
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public List<Event> getEventsBetween(long startEpochSec, long endEpochSec) throws DataAccessException {
        logger.debug("Getting events between {} and {}", startEpochSec, endEpochSec);
        return transactionManager.executeInReadOnlyTransaction(() -> {
            return eventsDao.listBetween(startEpochSec, endEpochSec);
        });
    }

    /**
     * 予定をタイトルで検索する
     * @param query 検索クエリ
     * @return 検索結果の予定リスト
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public List<Event> searchEventsByTitle(String query) throws DataAccessException {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        
        logger.debug("Searching events by title: '{}'", query);
        return transactionManager.executeInReadOnlyTransaction(() -> {
            return eventsDao.searchByTitle(query.trim(), config.getSearchEventsLimit());
        });
    }

    /**
     * 新しい予定を作成する
     * @param title タイトル
     * @param startEpochSec 開始時刻（エポック秒）
     * @param endEpochSec 終了時刻（エポック秒）
     * @return 作成された予定
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public Event createEvent(String title, long startEpochSec, long endEpochSec) throws DataAccessException {
        validateEventTimes(startEpochSec, endEpochSec);
        
        logger.debug("Creating new event: '{}' from {} to {}", title, startEpochSec, endEpochSec);
        
        return transactionManager.executeInTransaction(() -> {
            long id = eventsDao.insert(title, startEpochSec, endEpochSec);
            
            Event event = new Event();
            event.setId(id);
            event.setTitle(title);
            event.setStartEpochSec(startEpochSec);
            event.setEndEpochSec(endEpochSec);
            
            logger.info("Created new event with ID: {}", id);
            return event;
        });
    }

    /**
     * 現在時刻からデフォルト時間の予定を作成する
     * @param title タイトル
     * @return 作成された予定
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public Event createEventFromNow(String title) throws DataAccessException {
        long now = Instant.now().getEpochSecond();
        long end = now + (config.getEventDefaultDurationMinutes() * 60);
        return createEvent(title, now, end);
    }

    /**
     * 予定を更新する
     * @param event 更新する予定
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public void updateEvent(Event event) throws DataAccessException {
        if (event == null || event.getId() <= 0) {
            throw new IllegalArgumentException("Event and event ID must not be null or invalid");
        }
        
        validateEventTimes(event.getStartEpochSec(), event.getEndEpochSec());
        
        logger.debug("Updating event ID: {}", event.getId());
        
        transactionManager.executeInTransaction(() -> {
            eventsDao.update(event);
            logger.info("Updated event ID: {}", event.getId());
        });
    }

    /**
     * 予定を削除する
     * @param eventId 削除する予定のID
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public void deleteEvent(long eventId) throws DataAccessException {
        logger.debug("Deleting event ID: {}", eventId);
        
        transactionManager.executeInTransaction(() -> {
            eventsDao.delete(eventId);
            logger.info("Deleted event ID: {}", eventId);
        });
    }

    /**
     * 予定を取得する
     * @param eventId 予定のID
     * @return 予定（存在しない場合はnull）
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public Event getEvent(long eventId) throws DataAccessException {
        logger.debug("Getting event ID: {}", eventId);
        
        return transactionManager.executeInReadOnlyTransaction(() -> {
            return eventsDao.get(eventId);
        });
    }

    /**
     * 予定の存在確認
     * @param eventId 確認する予定のID
     * @return 予定が存在する場合true
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public boolean eventExists(long eventId) throws DataAccessException {
        logger.debug("Checking if event exists: ID={}", eventId);
        
        return transactionManager.executeInReadOnlyTransaction(() -> {
            Event event = eventsDao.get(eventId);
            return event != null;
        });
    }

    /**
     * 予定時間の妥当性を検証する
     * @param startEpochSec 開始時刻
     * @param endEpochSec 終了時刻
     * @throws IllegalArgumentException 時間が不正な場合
     */
    private void validateEventTimes(long startEpochSec, long endEpochSec) {
        if (startEpochSec < 0 || endEpochSec < 0) {
            throw new IllegalArgumentException("Event times must be positive");
        }
        
        if (startEpochSec >= endEpochSec) {
            throw new IllegalArgumentException("Start time must be before end time");
        }
        
        long durationMinutes = (endEpochSec - startEpochSec) / 60;
        if (durationMinutes < config.getEventMinDurationMinutes()) {
            throw new IllegalArgumentException(
                String.format("Event duration must be at least %d minutes", 
                             config.getEventMinDurationMinutes()));
        }
    }
}