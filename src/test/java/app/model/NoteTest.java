package app.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoteTest {

    @Test
    void testNoteCreation() {
        Note note = new Note();
        assertNotNull(note);
    }

    @Test
    void testGettersAndSetters() {
        Note note = new Note();
        note.setId(1L);
        note.setTitle("Test Note");
        note.setBody("Content");
        note.setCreatedAt(1672538400L);
        note.setUpdatedAt(1672542000L);
        
        assertEquals(1L, note.getId());
        assertEquals("Test Note", note.getTitle());
        assertEquals("Content", note.getBody());
        assertEquals(1672538400L, note.getCreatedAt());
        assertEquals(1672542000L, note.getUpdatedAt());
    }
}