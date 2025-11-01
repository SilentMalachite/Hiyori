package app.compose.events

import app.model.Event
import java.time.*
import java.time.format.DateTimeFormatter

/**
 * UI用のイベントデータクラス
 */
data class EventUi(
    val id: Long,
    val title: String,
    val startEpochSec: Long,
    val endEpochSec: Long
) {
    val startTime: LocalDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochSecond(startEpochSec), ZoneId.systemDefault())
    
    val endTime: LocalDateTime
        get() = LocalDateTime.ofInstant(Instant.ofEpochSecond(endEpochSec), ZoneId.systemDefault())
    
    val durationMinutes: Long
        get() = (endEpochSec - startEpochSec) / 60
    
    fun toJava(): Event {
        val event = Event()
        event.id = id
        event.title = title
        event.startEpochSec = startEpochSec
        event.endEpochSec = endEpochSec
        return event
    }
}

fun Event.toUi() = EventUi(
    id = id,
    title = title,
    startEpochSec = startEpochSec,
    endEpochSec = endEpochSec
)

/**
 * 週の日付範囲
 */
data class WeekRange(
    val start: LocalDate,
    val end: LocalDate
) {
    val days: List<LocalDate>
        get() = (0..6).map { start.plusDays(it.toLong()) }
    
    fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * 週のナビゲーション用のヘルパー
 */
fun getWeekRange(date: LocalDate): WeekRange {
    val monday = date.with(DayOfWeek.MONDAY)
    val sunday = monday.plusDays(6)
    return WeekRange(monday, sunday)
}

fun getThisWeek(): WeekRange = getWeekRange(LocalDate.now())
