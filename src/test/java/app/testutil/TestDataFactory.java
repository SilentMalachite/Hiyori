package app.testutil;

import app.model.Event;
import app.model.Note;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * テスト用のデータファクトリ
 */
public class TestDataFactory {

    /**
     * テスト用のNoteを作成する
     */
    public static Note createNote() {
        return createNote("テストメモ", "テスト用のメモ内容です。");
    }

    public static Note createNote(String title, String body) {
        Note note = new Note();
        note.setTitle(title);
        note.setBody(body);
        long now = Instant.now().getEpochSecond();
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        return note;
    }

    public static Note createNoteWithId(long id, String title, String body) {
        Note note = createNote(title, body);
        note.setId(id);
        return note;
    }

    /**
     * テスト用のEventを作成する
     */
    public static Event createEvent() {
        return createEvent("テスト予定", 1, 2); // 1時間の予定
    }

    public static Event createEvent(String title, int startHour, int endHour) {
        Event event = new Event();
        event.setTitle(title);
        
        LocalDateTime base = LocalDateTime.now()
                .withHour(0)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        LocalDateTime start = base.plusHours(Math.max(0, startHour));
        long durationHours = Math.max(1, endHour - startHour);
        LocalDateTime end = start.plusHours(durationHours);

        event.setStartEpochSec(start.atZone(ZoneId.systemDefault()).toEpochSecond());
        event.setEndEpochSec(end.atZone(ZoneId.systemDefault()).toEpochSecond());
        
        return event;
    }

    public static Event createEventWithId(long id, String title, long startEpoch, long endEpoch) {
        Event event = new Event();
        event.setId(id);
        event.setTitle(title);
        event.setStartEpochSec(startEpoch);
        event.setEndEpochSec(endEpoch);
        return event;
    }

    /**
     * 複数のテスト用Noteを作成する
     */
    public static List<Note> createNotes(int count) {
        List<Note> notes = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            notes.add(createNote("テストメモ" + i, "テスト用のメモ内容" + i + "です。"));
        }
        return notes;
    }

    /**
     * 複数のテスト用Eventを作成する
     */
    public static List<Event> createEvents(int count) {
        List<Event> events = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            events.add(createEvent("テスト予定" + i, i, i + 1));
        }
        return events;
    }

    /**
     * 検索テスト用のNoteを作成する
     */
    public static List<Note> createSearchTestNotes() {
        List<Note> notes = new ArrayList<>();
        
        notes.add(createNote("Javaプログラミング", "Javaでアプリケーションを開発する方法について"));
        notes.add(createNote("データベース設計", "SQLiteを使ったデータベース設計のベストプラクティス"));
        notes.add(createNote("テスト駆動開発", "TDDの重要性と実践方法について"));
        notes.add(createNote("アーキテクチャパターン", "MVC、MVP、MVVMなどのアーキテクチャパターン"));
        notes.add(createNote("パフォーマンス最適化", "アプリケーションのパフォーマンスを向上させる方法"));
        
        return notes;
    }

    /**
     * 検索テスト用のEventを作成する
     */
    public static List<Event> createSearchTestEvents() {
        List<Event> events = new ArrayList<>();
        
        events.add(createEvent("Java勉強会", 9, 11));
        events.add(createEvent("データベース研修", 13, 15));
        events.add(createEvent("テスト設計セミナー", 16, 18));
        events.add(createEvent("アーキテクチャレビュー", 10, 12));
        events.add(createEvent("パフォーマンス測定", 14, 16));
        
        return events;
    }

    /**
     * 境界値テスト用のデータを作成する
     */
    public static Note createNoteWithLongContent() {
        StringBuilder longContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longContent.append("これは非常に長いメモの内容です。").append(i).append(" ");
        }
        return createNote("長いメモ", longContent.toString());
    }

    public static Note createNoteWithSpecialCharacters() {
        return createNote("特殊文字テスト", "日本語、English、123、!@#$%^&*()、\n改行\nテスト");
    }

    public static Event createEventWithLongTitle() {
        StringBuilder longTitle = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longTitle.append("長いタイトル").append(i).append(" ");
        }
        return createEvent(longTitle.toString(), 9, 10);
    }
}
