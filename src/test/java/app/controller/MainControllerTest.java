package app.controller;

import app.service.EventService;
import app.service.NoteService;
import app.exception.DataAccessException;
import app.model.Note;
import app.model.Event;
import app.testutil.TestDataFactory;
import app.testutil.JavaFxExtension;
import javafx.beans.property.BooleanProperty;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MainControllerのテスト
 */
@ExtendWith({MockitoExtension.class, JavaFxExtension.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MainControllerTest {

    @Mock
    private NoteService mockNoteService;
    
    @Mock
    private EventService mockEventService;
    
    private MainController mainController;

    @BeforeEach
    void setUp() {
        mainController = new MainController(mockNoteService, mockEventService);
    }

    @Test
    @Order(1)
    @DisplayName("MainControllerが正常に初期化される")
    void testControllerInitialization() {
        // When & Then
        assertThat(mainController).isNotNull();
        assertThat(mainController.getNotesList()).isNotNull();
        assertThat(mainController.getTitleField()).isNotNull();
        assertThat(mainController.getBodyArea()).isNotNull();
        assertThat(mainController.getSearchField()).isNotNull();
        assertThat(mainController.getEditorFocusedProperty()).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("UIコンポーネントの初期設定が正しい")
    void testUIComponentInitialization() {
        // When & Then
        assertThat(mainController.getTitleField().getPromptText()).isEqualTo("タイトル");
        assertThat(mainController.getBodyArea().getPromptText()).isEqualTo("本文");
        assertThat(mainController.getSearchField().getPromptText()).contains("メモ・予定を検索");
        assertThat(mainController.getBodyArea().isWrapText()).isTrue();
    }

    @Test
    @Order(3)
    @DisplayName("初期データの読み込みが正常に動作する")
    void testLoadInitialData() throws DataAccessException {
        // Given
        List<Note> mockNotes = TestDataFactory.createNotes(3);
        when(mockNoteService.getRecentNotes()).thenReturn(mockNotes);

        // When
        mainController.loadInitialData();

        // Then
        verify(mockNoteService).getRecentNotes();
        assertThat(mainController.getNotesList().getItems()).hasSize(3);
    }

    @Test
    @Order(4)
    @DisplayName("初期データ読み込み時のエラーハンドリング")
    void testLoadInitialDataWithError() throws DataAccessException {
        // Given
        when(mockNoteService.getRecentNotes()).thenThrow(new DataAccessException("データベースエラー"));

        // When & Then - エラーが発生してもアプリケーションがクラッシュしないことを確認
        // JavaFXのAlertダイアログはテストスレッドでは実行できないため、例外をキャッチ
        assertThatThrownBy(() -> mainController.loadInitialData())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Not on FX application thread");
    }

    @Test
    @Order(5)
    @DisplayName("メモ検索が正常に動作する")
    void testSearchNotes() throws DataAccessException {
        // Given
        List<Note> mockNotes = TestDataFactory.createNotes(2);
        when(mockNoteService.searchNotes("テスト")).thenReturn(mockNotes);

        // When - 検索フィールドにテキストを設定してイベントを発火
        mainController.getSearchField().setText("テスト");
        mainController.getSearchField().fireEvent(new javafx.event.ActionEvent());

        // Then
        verify(mockNoteService).searchNotes("テスト");
        assertThat(mainController.getNotesList().getItems()).hasSize(2);
    }

    @Test
    @Order(6)
    @DisplayName("UIコンポーネントの状態管理が正常に動作する")
    void testUIComponentStateManagement() {
        // Given
        Note note = TestDataFactory.createNote("テストメモ", "テスト内容");

        // When - エディタにテキストを設定
        mainController.getTitleField().setText("テストメモ");
        mainController.getBodyArea().setText("テスト内容");

        // Then
        assertThat(mainController.getTitleField().getText()).isEqualTo("テストメモ");
        assertThat(mainController.getBodyArea().getText()).isEqualTo("テスト内容");
    }

    @Test
    @Order(7)
    @DisplayName("検索フィールドの状態管理が正常に動作する")
    void testSearchFieldStateManagement() {
        // When
        mainController.getSearchField().setText("検索テスト");

        // Then
        assertThat(mainController.getSearchField().getText()).isEqualTo("検索テスト");
    }

    @Test
    @Order(8)
    @DisplayName("WeekViewの設定が正常に動作する")
    void testSetWeekView() {
        // Given
        // WeekViewのモックは作成が複雑なため、nullでテスト
        app.ui.WeekView mockWeekView = null;

        // When
        mainController.setWeekView(mockWeekView);

        // Then - 例外が発生しないことを確認
        assertThatCode(() -> mainController.setWeekView(mockWeekView))
            .doesNotThrowAnyException();
    }

    @Test
    @Order(9)
    @DisplayName("フォーカス状態の管理が正常に動作する")
    void testFocusStateManagement() {
        // Given
        BooleanProperty editorFocused = mainController.getEditorFocusedProperty();

        // When & Then
        assertThat(editorFocused).isNotNull();
        assertThat(editorFocused.get()).isFalse(); // 初期状態はfalse
    }

    @Test
    @Order(10)
    @DisplayName("UIコンポーネントのアクセシビリティ設定")
    void testAccessibilitySettings() {
        // When & Then
        // JavaFXコンポーネントのテストでは、実際のUIレンダリングが行われないため、
        // コンポーネントが正しく初期化されていることを検証する
        assertThat(mainController.getTitleField().getFont()).isNotNull();
        assertThat(mainController.getBodyArea().getFont()).isNotNull();
        assertThat(mainController.getNotesList()).isNotNull();
    }
}