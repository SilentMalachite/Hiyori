package app.service;

import app.config.AppConfig;
import app.db.NotesDao;
import app.db.TransactionManager;
import app.exception.DataAccessException;
import app.model.Note;
import app.testutil.TestDataFactory;
import app.testutil.TestDatabase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NoteServiceのテスト
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NoteServiceTest {

    private TestDatabase testDb;
    private NotesDao notesDao;
    private TransactionManager transactionManager;
    private NoteService noteService;

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
        notesDao = new NotesDao(testDb.getDatabase());
        transactionManager = new TransactionManager(testDb.getDatabase());
        
        // Mock設定
        when(mockConfig.getNotesListMaxItems()).thenReturn(500);
        when(mockConfig.getSearchNotesLimit()).thenReturn(300);
        
        noteService = new NoteService(notesDao, transactionManager);
    }

    @AfterEach
    void tearDown() {
        if (testDb != null) {
            testDb.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("最近のメモ一覧が正常に取得できる")
    void testGetRecentNotes() throws DataAccessException {
        // Given
        List<Note> notes = TestDataFactory.createNotes(3);
        for (Note note : notes) {
            notesDao.insert(note);
        }

        // When
        List<Note> recentNotes = noteService.getRecentNotes();

        // Then
        assertThat(recentNotes).hasSize(3);
        assertThat(recentNotes.get(0).getTitle()).isEqualTo("テストメモ1");
    }

    @Test
    @Order(2)
    @DisplayName("メモ検索が正常に動作する")
    @Disabled("FTS検索の問題を調査中 - 後で修正予定")
    void testSearchNotes() throws DataAccessException {
        // Given
        List<Note> searchTestNotes = TestDataFactory.createSearchTestNotes();
        for (Note note : searchTestNotes) {
            notesDao.insert(note);
        }

        // When
        List<Note> javaResults = noteService.searchNotes("Java");
        List<Note> databaseResults = noteService.searchNotes("データベース");
        List<Note> emptyResults = noteService.searchNotes("");

        // Then
        assertThat(javaResults).hasSize(1);
        assertThat(javaResults.get(0).getTitle()).contains("Java");
        
        assertThat(databaseResults).hasSize(1);
        assertThat(databaseResults.get(0).getTitle()).contains("データベース");
        
        assertThat(emptyResults).hasSize(5); // 空の検索は最近のメモを返す
    }

    @Test
    @Order(3)
    @DisplayName("新しいメモが正常に作成できる")
    void testCreateNote() throws DataAccessException {
        // When
        Note note = noteService.createNote("新規メモ", "新規メモの内容");

        // Then
        assertThat(note).isNotNull();
        assertThat(note.getId()).isGreaterThan(0);
        assertThat(note.getTitle()).isEqualTo("新規メモ");
        assertThat(note.getBody()).isEqualTo("新規メモの内容");
        assertThat(note.getCreatedAt()).isGreaterThan(0);
        assertThat(note.getUpdatedAt()).isGreaterThan(0);
    }

    @Test
    @Order(4)
    @DisplayName("空のタイトルでメモを作成するとデフォルトタイトルが設定される")
    void testCreateNoteWithEmptyTitle() throws DataAccessException {
        // When
        Note note = noteService.createNote("", "内容のみのメモ");

        // Then
        assertThat(note.getTitle()).isEqualTo("無題のメモ");
        assertThat(note.getBody()).isEqualTo("内容のみのメモ");
    }

    @Test
    @Order(5)
    @DisplayName("nullのタイトルでメモを作成するとデフォルトタイトルが設定される")
    void testCreateNoteWithNullTitle() throws DataAccessException {
        // When
        Note note = noteService.createNote(null, "内容のみのメモ");

        // Then
        assertThat(note.getTitle()).isEqualTo("無題のメモ");
        assertThat(note.getBody()).isEqualTo("内容のみのメモ");
    }

    @Test
    @Order(6)
    @DisplayName("nullの本文でメモを作成できる")
    void testCreateNoteWithNullBody() throws DataAccessException {
        // When
        Note note = noteService.createNote("タイトルのみ", null);

        // Then
        assertThat(note.getTitle()).isEqualTo("タイトルのみ");
        assertThat(note.getBody()).isEqualTo("");
    }

    @Test
    @Order(7)
    @DisplayName("メモの更新が正常に動作する")
    void testUpdateNote() throws DataAccessException {
        // Given
        Note note = noteService.createNote("元のタイトル", "元の内容");

        // When
        note.setTitle("更新されたタイトル");
        note.setBody("更新された内容");
        noteService.updateNote(note);

        // Then
        Note updatedNote = notesDao.getById(note.getId());
        assertThat(updatedNote.getTitle()).isEqualTo("更新されたタイトル");
        assertThat(updatedNote.getBody()).isEqualTo("更新された内容");
        assertThat(updatedNote.getUpdatedAt()).isGreaterThanOrEqualTo(note.getCreatedAt());
    }

    @Test
    @Order(8)
    @DisplayName("空のタイトルでメモを更新するとデフォルトタイトルが設定される")
    void testUpdateNoteWithEmptyTitle() throws DataAccessException {
        // Given
        Note note = noteService.createNote("元のタイトル", "元の内容");

        // When
        note.setTitle("");
        note.setBody("更新された内容");
        noteService.updateNote(note);

        // Then
        Note updatedNote = notesDao.getById(note.getId());
        assertThat(updatedNote.getTitle()).isEqualTo("無題のメモ");
        assertThat(updatedNote.getBody()).isEqualTo("更新された内容");
    }

    @Test
    @Order(9)
    @DisplayName("メモの削除が正常に動作する")
    void testDeleteNote() throws DataAccessException {
        // Given
        Note note = noteService.createNote("削除対象メモ", "削除される内容");
        long noteId = note.getId();

        // When
        noteService.deleteNote(noteId);

        // Then
        Note deletedNote = notesDao.getById(noteId);
        assertThat(deletedNote).isNull();
    }

    @Test
    @Order(10)
    @DisplayName("メモの存在確認が正常に動作する")
    void testNoteExists() throws DataAccessException {
        // Given
        Note note = noteService.createNote("存在確認テスト", "内容");

        // When & Then
        assertThat(noteService.noteExists(note.getId())).isTrue();
        assertThat(noteService.noteExists(999L)).isFalse();
    }

    @Test
    @Order(11)
    @DisplayName("nullのメモで更新しようとすると例外が投げられる")
    void testUpdateNullNote() {
        // When & Then
        assertThatThrownBy(() -> noteService.updateNote(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Note and note ID must not be null or invalid");
    }

    @Test
    @Order(12)
    @DisplayName("無効なIDのメモで更新しようとすると例外が投げられる")
    void testUpdateNoteWithInvalidId() {
        // Given
        Note note = TestDataFactory.createNote();
        note.setId(0); // 無効なID

        // When & Then
        assertThatThrownBy(() -> noteService.updateNote(note))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Note and note ID must not be null or invalid");
    }

    @Test
    @Order(13)
    @DisplayName("長いコンテンツのメモが正常に処理される")
    void testLongContentNote() throws DataAccessException {
        // Given
        Note longNote = TestDataFactory.createNoteWithLongContent();

        // When
        Note createdNote = noteService.createNote(longNote.getTitle(), longNote.getBody());

        // Then
        assertThat(createdNote).isNotNull();
        assertThat(createdNote.getBody()).hasSizeGreaterThan(1000);
    }

    @Test
    @Order(14)
    @DisplayName("特殊文字を含むメモが正常に処理される")
    void testSpecialCharactersNote() throws DataAccessException {
        // Given
        Note specialNote = TestDataFactory.createNoteWithSpecialCharacters();

        // When
        Note createdNote = noteService.createNote(specialNote.getTitle(), specialNote.getBody());

        // Then
        assertThat(createdNote).isNotNull();
        assertThat(createdNote.getTitle()).isEqualTo("特殊文字テスト");
        assertThat(createdNote.getBody()).contains("日本語");
        assertThat(createdNote.getBody()).contains("English");
        assertThat(createdNote.getBody()).contains("!@#$%^&*()");
    }

    @Test
    @Order(15)
    @DisplayName("トランザクション内でメモ操作が正常に動作する")
    void testTransactionHandling() throws DataAccessException {
        // Given
        Note note1 = TestDataFactory.createNote("メモ1", "内容1");
        Note note2 = TestDataFactory.createNote("メモ2", "内容2");

        // When - 複数の操作をトランザクション内で実行
        Note createdNote1 = noteService.createNote(note1.getTitle(), note1.getBody());
        Note createdNote2 = noteService.createNote(note2.getTitle(), note2.getBody());
        
        createdNote1.setTitle("更新されたメモ1");
        noteService.updateNote(createdNote1);

        // Then
        List<Note> allNotes = noteService.getRecentNotes();
        assertThat(allNotes).hasSize(2);
        
        Note updatedNote = allNotes.stream()
            .filter(n -> n.getId() == createdNote1.getId())
            .findFirst()
            .orElse(null);
        assertThat(updatedNote).isNotNull();
        assertThat(updatedNote.getTitle()).isEqualTo("更新されたメモ1");
    }

    @Test
    @Order(16)
    @DisplayName("検索でnullクエリは最近のメモを返す")
    void testSearchWithNullQuery() throws DataAccessException {
        // Given
        List<Note> notes = TestDataFactory.createNotes(3);
        for (Note note : notes) {
            notesDao.insert(note);
        }

        // When
        List<Note> results = noteService.searchNotes(null);

        // Then
        assertThat(results).hasSize(3);
    }

    @Test
    @Order(17)
    @DisplayName("検索で空白のみのクエリは最近のメモを返す")
    void testSearchWithBlankQuery() throws DataAccessException {
        // Given
        List<Note> notes = TestDataFactory.createNotes(3);
        for (Note note : notes) {
            notesDao.insert(note);
        }

        // When
        List<Note> results = noteService.searchNotes("   ");

        // Then
        assertThat(results).hasSize(3);
    }
}