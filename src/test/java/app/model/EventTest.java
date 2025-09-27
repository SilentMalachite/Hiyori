package app.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    @Test
    void testEventCreation() {
        Event event = new Event();
        assertNotNull(event);
    }

    @Test
    void testGettersAndSetters() {
        Event event = new Event();
        event.setId(1L);
        event.setTitle("Test Event");
        event.setStartEpochSec(1672538400L); // 2023-01-01 10:00 UTC
        event.setEndEpochSec(1672542000L);   // 2023-01-01 11:00 UTC
        
        assertEquals(1L, event.getId());
        assertEquals("Test Event", event.getTitle());
        assertEquals(1672538400L, event.getStartEpochSec());
        assertEquals(1672542000L, event.getEndEpochSec());
    }
}