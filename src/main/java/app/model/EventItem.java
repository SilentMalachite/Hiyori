package app.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public record EventItem(Event event) implements SearchItem {
    @Override public String display() {
        LocalDateTime sdt = LocalDateTime.ofInstant(Instant.ofEpochSecond(event.getStartEpochSec()), ZoneId.systemDefault());
        String label = String.format("%d/%d %02d:%02d", sdt.getMonthValue(), sdt.getDayOfMonth(), sdt.getHour(), sdt.getMinute());
        return "📅 " + label + "  " + event.getTitle();
    }
}

