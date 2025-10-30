package app.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.*;

class LogConfigTest {

    @Test
    @DisplayName("SLF4J の実装が Logback (LoggerContext) であることのスモークテスト")
    void testLogbackBindingPresent() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        // クラス名で判定（依存に Logback がある場合は ch.qos.logback.classic.LoggerContext）
        assertThat(factory.getClass().getName()).isEqualTo("ch.qos.logback.classic.LoggerContext");
    }
}
