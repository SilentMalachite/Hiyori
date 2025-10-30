package app.model;

import org.junit.jupiter.api.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.*;

class EventItemTest {

    @Test
    @DisplayName("display() は systemDefault の時刻で 'M/d HH:mm  タイトル' 形式で表示する")
    void testDisplayFormat() {
        Event e = new Event();
        e.setId(1L);
        e.setTitle("テスト予定");
        // 2023-01-01 10:00:00 UTC → 1672538400
        e.setStartEpochSec(1672538400L);
        // 2023-01-01 11:00:00 UTC
        e.setEndEpochSec(1672542000L);

        EventItem item = new EventItem(e);
        String label = item.display();

        // 期待値を実行環境の systemDefault に合わせて生成
        LocalDateTime sdt = LocalDateTime.ofInstant(Instant.ofEpochSecond(e.getStartEpochSec()), ZoneId.systemDefault());
        String expected = String.format("📅 %d/%d %02d:%02d  %s", sdt.getMonthValue(), sdt.getDayOfMonth(), sdt.getHour(), sdt.getMinute(), e.getTitle());
        assertThat(label).isEqualTo(expected);
    }
}
