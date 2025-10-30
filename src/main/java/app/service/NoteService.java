package app.service;

import app.config.AppConfig;
import app.db.NotesDao;
import app.db.TransactionManager;
import app.db.ThrowingRunnable;
import app.db.ThrowingSupplier;
import app.exception.DataAccessException;
import app.model.Note;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * メモ関連のビジネスロジックを担当するサービス
 */
public class NoteService {
    private static final Logger logger = LoggerFactory.getLogger(NoteService.class);
    private final NotesDao notesDao;
    private final TransactionManager transactionManager;
    private final AppConfig config;

    public NoteService(NotesDao notesDao, TransactionManager transactionManager) {
        this.notesDao = notesDao;
        this.transactionManager = transactionManager;
        this.config = AppConfig.getInstance();
    }

    /**
     * 最近のメモ一覧を取得する
     * @return メモのリスト
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public List<Note> getRecentNotes() throws DataAccessException {
        logger.debug("Getting recent notes");
        return transactionManager.executeInReadOnlyTransaction(() -> notesDao.listRecent(0));
    }

    /**
     * 最近のメモ一覧を取得する（上限指定）
     * @param limit 取得上限（0以下で無制限）
     */
    public List<Note> getRecentNotesWithLimit(int limit) throws DataAccessException {
        int effectiveLimit = limit > 0 ? limit : 0;
        logger.debug("Getting recent notes with limit: {}", effectiveLimit);
        return transactionManager.executeInReadOnlyTransaction(() -> notesDao.listRecent(effectiveLimit));
    }

    /**
     * メモを検索する
     * @param query 検索クエリ
     * @return 検索結果のメモリスト
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public List<Note> searchNotes(String query) throws DataAccessException {
        if (query == null || query.trim().isEmpty()) {
            return getRecentNotes();
        }
        
        logger.debug("Searching notes with query: '{}'", query);
        return transactionManager.executeInReadOnlyTransaction(() -> {
            return notesDao.searchNotes(query.trim(), config.getSearchNotesLimit());
        });
    }

    /**
     * 新しいメモを作成する
     * @param title タイトル
     * @param body 本文
     * @return 作成されたメモ
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public Note createNote(String title, String body) throws DataAccessException {
        logger.debug("Creating new note with title: '{}'", title);
        
        return transactionManager.executeInTransaction(() -> {
            Note note = new Note();
            note.setTitle(title != null && !title.trim().isEmpty() ? title.trim() : "無題のメモ");
            note.setBody(body != null ? body : "");
            
            long now = Instant.now().getEpochSecond();
            note.setCreatedAt(now);
            note.setUpdatedAt(now);
            
            long id = notesDao.insert(note);
            note.setId(id);
            
            logger.info("Created new note with ID: {}", id);
            return note;
        });
    }

    /**
     * メモを更新する
     * @param note 更新するメモ
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public void updateNote(Note note) throws DataAccessException {
        if (note == null || note.getId() <= 0) {
            throw new IllegalArgumentException("Note and note ID must not be null or invalid");
        }
        
        logger.debug("Updating note ID: {}", note.getId());
        
        transactionManager.executeInTransaction(() -> {
            // タイトルが空の場合はデフォルトタイトルを設定
            if (note.getTitle() == null || note.getTitle().trim().isEmpty()) {
                note.setTitle("無題のメモ");
            }
            
            long previous = note.getUpdatedAt();
            long now = Instant.now().getEpochSecond();
            if (previous >= now) {
                now = previous + 1;
            }
            note.setUpdatedAt(now);
            
            notesDao.update(note);
            logger.info("Updated note ID: {}", note.getId());
        });
    }

    /**
     * メモを削除する
     * @param noteId 削除するメモのID
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public void deleteNote(long noteId) throws DataAccessException {
        logger.debug("Deleting note ID: {}", noteId);
        
        transactionManager.executeInTransaction(() -> {
            notesDao.delete(noteId);
            logger.info("Deleted note ID: {}", noteId);
        });
    }

    /**
     * メモの存在確認
     * @param noteId 確認するメモのID
     * @return メモが存在する場合true
     * @throws DataAccessException データアクセスエラーが発生した場合
     */
    public boolean noteExists(long noteId) throws DataAccessException {
        logger.debug("Checking if note exists: ID={}", noteId);
        
        return transactionManager.executeInReadOnlyTransaction(() -> {
            try {
                Note note = notesDao.getById(noteId);
                return note != null;
            } catch (DataAccessException e) {
                logger.debug("Note not found: ID={}", noteId);
                return false;
            }
        });
    }
}
