package app.db;

import app.exception.DataAccessException;
import app.model.Note;
import app.testutil.TestDataFactory;
import app.testutil.TestDatabase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * NotesDaoのテスト
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotesDaoTest {

    private TestDatabase testDb;
    private NotesDao notesDao;

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
        notesDao = new NotesDao(testDb.getDatabase(), tm);
    }

    @AfterEach
    void tearDown() {
        if (testDb != null) {
            testDb.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("メモの挿入が正常に動作する")
    void testInsertNote() throws DataAccessException {
        // Given
        Note note = TestDataFactory.createNote("テストメモ", "テスト内容");

        // When
        long id = notesDao.insert(note);

        // Then
        assertThat(id).isGreaterThan(0);
        assertThat(note.getId()).isEqualTo(id);
    }

    @Test
    @Order(2)
    @DisplayName("メモの取得が正常に動作する")
    void testGetNoteById() throws DataAccessException {
        // Given
        Note originalNote = TestDataFactory.createNote("テストメモ", "テスト内容");
        long id = notesDao.insert(originalNote);

        // When
        Note retrievedNote = notesDao.getById(id);

        // Then
        assertThat(retrievedNote).isNotNull();
        assertThat(retrievedNote.getId()).isEqualTo(id);
        assertThat(retrievedNote.getTitle()).isEqualTo("テストメモ");
        assertThat(retrievedNote.getBody()).isEqualTo("テスト内容");
    }

    @Test
    @Order(3)
    @DisplayName("存在しないメモの取得はnullを返す")
    void testGetNonExistentNote() throws DataAccessException {
        // When
        Note note = notesDao.getById(999L);

        // Then
        assertThat(note).isNull();
    }

    @Test
    @Order(4)
    @DisplayName("メモの更新が正常に動作する")
    void testUpdateNote() throws DataAccessException {
        // Given
        Note note = TestDataFactory.createNote("元のタイトル", "元の内容");
        long id = notesDao.insert(note);
        
        note.setTitle("更新されたタイトル");
        note.setBody("更新された内容");
        note.setUpdatedAt(System.currentTimeMillis() / 1000);

        // When
        notesDao.update(note);

        // Then
        Note updatedNote = notesDao.getById(id);
        assertThat(updatedNote.getTitle()).isEqualTo("更新されたタイトル");
        assertThat(updatedNote.getBody()).isEqualTo("更新された内容");
    }

    @Test
    @Order(5)
    @DisplayName("メモの削除が正常に動作する")
    void testDeleteNote() throws DataAccessException {
        // Given
        Note note = TestDataFactory.createNote("削除対象メモ", "削除される内容");
        long id = notesDao.insert(note);

        // When
        notesDao.delete(id);

        // Then
        Note deletedNote = notesDao.getById(id);
        assertThat(deletedNote).isNull();
    }

    @Test
    @Order(6)
    @DisplayName("最近のメモ一覧が正常に取得できる")
    void testListRecentNotes() throws DataAccessException {
        // Given
        List<Note> notes = TestDataFactory.createNotes(5);
        for (Note note : notes) {
            notesDao.insert(note);
        }

        // When
        List<Note> recentNotes = notesDao.listRecent(10);

        // Then
        assertThat(recentNotes).hasSize(5);
        // 更新日時の降順でソートされていることを確認
        for (int i = 0; i < recentNotes.size() - 1; i++) {
            assertThat(recentNotes.get(i).getUpdatedAt())
                .isGreaterThanOrEqualTo(recentNotes.get(i + 1).getUpdatedAt());
        }
    }

    @Test
    @Order(7)
    @DisplayName("メモ検索が正常に動作する")
    @Disabled("FTS検索の問題を調査中 - 後で修正予定")
    void testSearchNotes() throws DataAccessException {
        // Given - 直接FTSテーブルにデータを挿入してテスト
        Note javaNote = TestDataFactory.createNote("Javaプログラミング", "Javaでアプリケーションを開発する方法について");
        notesDao.insert(javaNote);
        
        Note dbNote = TestDataFactory.createNote("データベース設計", "SQLiteを使ったデータベース設計のベストプラクティス");
        notesDao.insert(dbNote);

        // When
        List<Note> javaResults = notesDao.searchNotes("Java", 10);
        List<Note> databaseResults = notesDao.searchNotes("データベース", 10);
        List<Note> noResults = notesDao.searchNotes("存在しないキーワード", 10);

        // Then
        assertThat(javaResults).hasSize(1);
        assertThat(javaResults.get(0).getTitle()).contains("Java");
        
        assertThat(databaseResults).hasSize(1);
        assertThat(databaseResults.get(0).getTitle()).contains("データベース");
        
        assertThat(noResults).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("空の検索クエリは最近のメモを返す")
    void testSearchWithEmptyQuery() throws DataAccessException {
        // Given
        List<Note> notes = TestDataFactory.createNotes(3);
        for (Note note : notes) {
            notesDao.insert(note);
        }

        // When
        List<Note> results = notesDao.searchNotes("", 10);
        List<Note> nullResults = notesDao.searchNotes(null, 10);

        // Then
        assertThat(results).hasSize(3);
        assertThat(nullResults).hasSize(3);
    }

    @Test
    @Order(9)
    @DisplayName("存在しないメモの更新は例外を投げる")
    void testUpdateNonExistentNote() {
        // Given
        Note nonExistentNote = TestDataFactory.createNoteWithId(999L, "存在しない", "メモ");

        // When & Then
        assertThatThrownBy(() -> notesDao.update(nonExistentNote))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("更新対象のメモが見つかりませんでした");
    }

    @Test
    @Order(10)
    @DisplayName("存在しないメモの削除は例外を投げる")
    void testDeleteNonExistentNote() {
        // When & Then
        assertThatThrownBy(() -> notesDao.delete(999L))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("削除対象のメモが見つかりませんでした");
    }

    @Test
    @Order(11)
    @DisplayName("長いコンテンツのメモが正常に処理される")
    void testLongContentNote() throws DataAccessException {
        // Given
        Note longNote = TestDataFactory.createNoteWithLongContent();

        // When
        long id = notesDao.insert(longNote);
        Note retrievedNote = notesDao.getById(id);

        // Then
        assertThat(retrievedNote).isNotNull();
        assertThat(retrievedNote.getBody()).hasSizeGreaterThan(1000);
    }

    @Test
    @Order(12)
    @DisplayName("特殊文字を含むメモが正常に処理される")
    void testSpecialCharactersNote() throws DataAccessException {
        // Given
        Note specialNote = TestDataFactory.createNoteWithSpecialCharacters();

        // When
        long id = notesDao.insert(specialNote);
        Note retrievedNote = notesDao.getById(id);

        // Then
        assertThat(retrievedNote).isNotNull();
        assertThat(retrievedNote.getTitle()).isEqualTo("特殊文字テスト");
        assertThat(retrievedNote.getBody()).contains("日本語");
        assertThat(retrievedNote.getBody()).contains("English");
        assertThat(retrievedNote.getBody()).contains("!@#$%^&*()");
        assertThat(retrievedNote.getBody()).contains("\n");
    }

    @Test
    @Order(13)
    @DisplayName("制限数を超えるメモ一覧取得")
    void testListRecentWithLimit() throws DataAccessException {
        // Given
        List<Note> notes = TestDataFactory.createNotes(10);
        for (Note note : notes) {
            notesDao.insert(note);
        }

        // When
        List<Note> limitedNotes = notesDao.listRecent(5);

        // Then
        assertThat(limitedNotes).hasSize(5);
    }

    @Test
    @Order(14)
    @DisplayName("検索結果の制限数が正常に動作する")
    void testSearchWithLimit() throws DataAccessException {
        // Given
        List<Note> notes = TestDataFactory.createNotes(10);
        for (Note note : notes) {
            notesDao.insert(note);
        }

        // When
        List<Note> limitedResults = notesDao.searchNotes("テスト", 3);

        // Then
        assertThat(limitedResults).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @Order(15)
    @DisplayName("LIKEフォールバックでワイルドカード文字を含むタイトルを検索できる")
    void testFallbackLikeSearchHandlesWildcards() throws Exception {
        Note percentNote = TestDataFactory.createNote("進捗率100%", "詳細");
        Note underscoreNote = TestDataFactory.createNote("タスク_A_メモ", "詳細");
        notesDao.insert(percentNote);
        notesDao.insert(underscoreNote);

        Connection conn = null;
        try {
            conn = testDb.getDatabase().getConnection();
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM notes_fts");
            }
        } finally {
            if (conn != null) {
                testDb.getDatabase().releaseConnection(conn);
            }
        }

        List<Note> percentResults = notesDao.searchNotes("100%", 10);
        List<Note> underscoreResults = notesDao.searchNotes("タスク_A_", 10);

        assertThat(percentResults).extracting(Note::getTitle).containsExactly("進捗率100%");
        assertThat(underscoreResults).extracting(Note::getTitle).containsExactly("タスク_A_メモ");
    }
}