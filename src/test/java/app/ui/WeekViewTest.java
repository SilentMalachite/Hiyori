package app.ui;

import app.config.AppConfig;
import app.service.EventService;
import app.testutil.JavaFxExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WeekView????
 */
@ExtendWith({MockitoExtension.class, JavaFxExtension.class})
class WeekViewTest {

    @Mock
    private EventService mockEventService;

    private WeekView weekView;

    @BeforeEach
    void setUp() {
        weekView = new WeekView(() -> mockEventService);
    }

    @Test
    void testWeekViewInitialization() {
        // When & Then
        assertThat(weekView).isNotNull();
        assertThat(weekView.getNode()).isNotNull();
    }

    @Test
    void testUIConstantsFromConfig() {
        // Given
        AppConfig config = AppConfig.getInstance();

        // Then
        assertThat(config.getWeekViewPaddingTop()).isEqualTo(8);
        assertThat(config.getWeekViewPaddingRight()).isEqualTo(8);
        assertThat(config.getWeekViewPaddingBottom()).isEqualTo(8);
        assertThat(config.getWeekViewPaddingLeft()).isEqualTo(48);
        assertThat(config.getWeekViewHourHeight()).isEqualTo(48);
        assertThat(config.getWeekViewDayHeaderHeight()).isEqualTo(28);
        assertThat(config.getWeekViewBackgroundColor()).isEqualTo("#f7f7f7");
        assertThat(config.getWeekViewGridColor()).isEqualTo("#e0e0e0");
        assertThat(config.getWeekViewHourBoldColor()).isEqualTo("#c8c8c8");
        assertThat(config.getWeekViewEventColor()).isEqualTo("#4a90e2");
        assertThat(config.getWeekViewTextColor()).isEqualTo("#333333");
        assertThat(config.getWeekViewHighlightColor()).isEqualTo("#ff9800");
    }

    @Test
    void testNodeConfiguration() {
        // When
        var node = weekView.getNode();

        // Then
        assertThat(node).isNotNull();
        assertThat(node).isInstanceOf(javafx.scene.layout.Pane.class);
        var pane = (javafx.scene.layout.Pane) node;
        assertThat(pane.getChildren()).isNotEmpty();
        // Canvas?????????
        assertThat(pane.getChildren().stream().anyMatch(child -> child instanceof javafx.scene.canvas.Canvas))
            .isTrue();
    }

    @Test
    void testReload() throws Exception {
        // When
        weekView.reload();

        // Then
        // EventService?getEventsBetween???????????
        verify(mockEventService).getEventsBetween(anyLong(), anyLong());
    }

    @Test
    void testQuickAddEventAtNow() throws Exception {
        // When
        weekView.quickAddEventAtNow();

        // Then
        // EventService????????????
        verify(mockEventService).createEventFromNow(anyString());
    }
}