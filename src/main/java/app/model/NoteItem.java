package app.model;

public record NoteItem(Note note) implements SearchItem {
    @Override public String display() {
        return "📝 " + note.getTitle();
    }
}

