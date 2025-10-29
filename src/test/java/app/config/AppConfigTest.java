package app.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class AppConfigTest {

    private AppConfig config;

    @BeforeEach
    void setUp() {
        // ???????????????
        config = AppConfig.getInstance();
    }

    @AfterEach
    void tearDown() {
        // ?????????
    }

    @Test
    void testWeekViewPaddingSettings() {
        // WeekView????????????????????
        assertThat(config.getWeekViewPaddingTop()).isEqualTo(8);
        assertThat(config.getWeekViewPaddingRight()).isEqualTo(8);
        assertThat(config.getWeekViewPaddingBottom()).isEqualTo(8);
        assertThat(config.getWeekViewPaddingLeft()).isEqualTo(48);
    }

    @Test
    void testWeekViewSizeSettings() {
        // WeekView??????????????????
        assertThat(config.getWeekViewHourHeight()).isEqualTo(48);
        assertThat(config.getWeekViewDayHeaderHeight()).isEqualTo(28);
    }

    @Test
    void testWeekViewColorSettings() {
        // WeekView????????????????
        assertThat(config.getWeekViewBackgroundColor()).isEqualTo("#f7f7f7");
        assertThat(config.getWeekViewGridColor()).isEqualTo("#e0e0e0");
        assertThat(config.getWeekViewHourBoldColor()).isEqualTo("#c8c8c8");
        assertThat(config.getWeekViewEventColor()).isEqualTo("#4a90e2");
        assertThat(config.getWeekViewTextColor()).isEqualTo("#333333");
        assertThat(config.getWeekViewHighlightColor()).isEqualTo("#ff9800");
    }

    @Test
    void testEventEditorPresetSettings() {
        // EventEditor????????????????????
        assertThat(config.getEventEditorFocusPresetDurationMinutes()).isEqualTo(90);
        assertThat(config.getEventEditorFocusPresetTitle()).isEqualTo("?? (90?)");
        assertThat(config.getEventEditorBreakPresetDurationMinutes()).isEqualTo(30);
        assertThat(config.getEventEditorBreakPresetTitle()).isEqualTo("?? (30?)");
    }

    @Test
    void testDefaultValues() {
        // ???????????????????????
        assertThat(config.getWindowWidth()).isEqualTo(1200);
        assertThat(config.getWindowHeight()).isEqualTo(800);
    }
}