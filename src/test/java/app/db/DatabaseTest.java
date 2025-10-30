package app.db;

import app.exception.DatabaseException;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.*;

/**
 * Database クラスのユニットテスト（接続プールと基本的な健全性確認）。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseTest {

    private Path tempDir;
    private Path dbFile;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("hiyori-dbtest-");
        dbFile = tempDir.resolve("unit.db");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(dbFile)) Files.deleteIfExists(dbFile);
        if (Files.exists(tempDir)) Files.deleteIfExists(tempDir);
    }

    @Test
    @Order(1)
    @DisplayName("未初期化での getConnection は DatabaseException を投げる")
    void testGetConnectionWithoutInitialize() {
        Database db = new Database(dbFile.toString());
        assertThatThrownBy(db::getConnection)
                .isInstanceOf(DatabaseException.class)
                .hasMessageContaining("not initialized");
    }

    @Test
    @Order(2)
    @DisplayName("initialize→get→簡易クエリ→release→close が正常に動作する")
    void testInitializeGetReleaseClose() throws Exception {
        Database db = new Database(dbFile.toString());
        db.initialize();

        Connection conn = db.getConnection();
        assertThat(conn).isNotNull();

        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }

        db.releaseConnection(conn);
        db.close();
    }

    @Test
    @Order(3)
    @DisplayName("release 時に未コミットがあればロールバックされ autoCommit=true に戻る")
    void testReleaseResetsConnectionState() throws Exception {
        Database db = new Database(dbFile.toString());
        db.initialize();

        Connection conn = db.getConnection();
        assertThat(conn.getAutoCommit()).isTrue();

        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS t(x INTEGER)");
            st.execute("INSERT INTO t(x) VALUES (1)");
        }
        // 未コミットのまま release（Database#resetConnection で rollback されるはず）
        db.releaseConnection(conn);

        // 新たに取得してオートコミットが true に戻っていることを確認
        try (Connection again = db.getConnection()) {
            assertThat(again.getAutoCommit()).isTrue();
        }

        db.close();
    }

    @Test
    @Order(4)
    @DisplayName("閉じた接続を release してもプールが自己修復し、次の getConnection が成功する")
    void testClosedConnectionReplacement() throws Exception {
        Database db = new Database(dbFile.toString());
        db.initialize();

        Connection conn = db.getConnection();
        // 明示的に閉じてから release する（内部で置換が行われる想定）
        conn.close();
        db.releaseConnection(conn);

        // 以降の取得が成功すれば置換できている
        try (Connection healthy = db.getConnection(); Statement st = healthy.createStatement(); ResultSet rs = st.executeQuery("SELECT 1")) {
            assertThat(rs.next()).isTrue();
        }
        db.close();
    }

    @Test
    @Order(5)
    @DisplayName("PRAGMA 設定の間接確認: journal_mode=WAL と busy_timeout が設定されている")
    void testPragmaSettings() throws Exception {
        Database db = new Database(dbFile.toString());
        db.initialize();

        try (Connection conn = db.getConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                assertThat(rs.next()).isTrue();
                String mode = rs.getString(1);
                assertThat(mode).isNotNull();
                // SQLite は小文字で "wal" を返すことが多い
                assertThat(mode.toLowerCase()).isEqualTo("wal");
            }
            try (ResultSet rs2 = st.executeQuery("PRAGMA busy_timeout")) {
                assertThat(rs2.next()).isTrue();
                int timeout = rs2.getInt(1);
                // 既定は AppConfig の database.connection.timeout.ms (=30000)
                assertThat(timeout).isGreaterThanOrEqualTo(1000);
            }
        }
        db.close();
    }
}
