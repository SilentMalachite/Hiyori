package app.compose.events

import java.time.LocalDateTime

/**
 * イベントレイアウト情報
 * 衝突を考慮した列配置を保持
 */
data class EventLayout(
    val event: EventUi,
    val column: Int,        // どの列に配置するか（0始まり）
    val totalColumns: Int   // その時間帯の総列数
)

/**
 * 2つのイベントが時間的に重なっているかチェック
 */
fun EventUi.overlapsWith(other: EventUi): Boolean {
    // 同じ日でない場合は重ならない
    if (this.startTime.toLocalDate() != other.startTime.toLocalDate()) {
        return false
    }
    
    // 時間の重なりをチェック
    // A.start < B.end AND A.end > B.start
    return this.startEpochSec < other.endEpochSec && 
           this.endEpochSec > other.startEpochSec
}

/**
 * イベントリストから衝突を考慮したレイアウトを計算
 * 
 * アルゴリズム:
 * 1. 開始時刻でソート
 * 2. 各イベントについて、すでに配置されたイベントと重なりをチェック
 * 3. 重なる場合は、空いている列を探して配置
 * 4. 各時間帯の最大列数を記録
 */
fun calculateEventLayouts(events: List<EventUi>): List<EventLayout> {
    if (events.isEmpty()) return emptyList()
    
    // 開始時刻でソート
    val sortedEvents = events.sortedBy { it.startEpochSec }
    
    // 各イベントのレイアウト情報
    val layouts = mutableListOf<EventLayout>()
    
    // 各イベントに列を割り当て
    sortedEvents.forEach { event ->
        // このイベントと重なる既存のイベントを取得
        val overlapping = layouts.filter { it.event.overlapsWith(event) }
        
        // 使用中の列番号を取得
        val usedColumns = overlapping.map { it.column }.toSet()
        
        // 空いている最小の列番号を見つける
        var column = 0
        while (column in usedColumns) {
            column++
        }
        
        // 暫定的にレイアウトを追加（totalColumnsは後で更新）
        layouts.add(EventLayout(event, column, column + 1))
    }
    
    // 各イベントグループの総列数を計算して更新
    return updateTotalColumns(layouts)
}

/**
 * 各イベントグループの総列数を計算
 * 重なっているイベントグループごとに最大列数を設定
 */
private fun updateTotalColumns(layouts: List<EventLayout>): List<EventLayout> {
    val result = mutableListOf<EventLayout>()
    
    layouts.forEach { layout ->
        // このイベントと重なるすべてのイベントを取得
        val overlappingLayouts = layouts.filter { 
            it.event.overlapsWith(layout.event) || it.event.id == layout.event.id
        }
        
        // 重なっているグループの最大列数を計算
        val maxColumn = overlappingLayouts.maxOfOrNull { it.column } ?: 0
        val totalColumns = maxColumn + 1
        
        result.add(layout.copy(totalColumns = totalColumns))
    }
    
    return result
}

/**
 * 時間（分）を15分単位に丸める
 */
fun roundToMinutes(minutes: Int, interval: Int = 15): Int {
    return (minutes / interval) * interval
}

/**
 * エポック秒を15分単位に丸める
 */
fun roundToInterval(epochSec: Long, intervalMinutes: Int = 15): Long {
    val intervalSeconds = intervalMinutes * 60L
    return (epochSec / intervalSeconds) * intervalSeconds
}

/**
 * ドラッグ中の位置から新しい開始時刻を計算
 */
fun calculateNewStartTime(
    originalEvent: EventUi,
    dragOffsetMinutes: Int,
    snapInterval: Int = 15
): LocalDateTime {
    val originalStartMinutes = originalEvent.startTime.hour * 60 + originalEvent.startTime.minute
    val newStartMinutes = (originalStartMinutes + dragOffsetMinutes).coerceIn(0, 24 * 60 - 1)
    val snappedMinutes = roundToMinutes(newStartMinutes, snapInterval)
    
    val hour = snappedMinutes / 60
    val minute = snappedMinutes % 60
    
    return originalEvent.startTime
        .withHour(hour)
        .withMinute(minute)
        .withSecond(0)
        .withNano(0)
}

/**
 * リサイズ中の位置から新しい終了時刻を計算
 */
fun calculateNewEndTime(
    originalEvent: EventUi,
    resizeOffsetMinutes: Int,
    snapInterval: Int = 15,
    minDurationMinutes: Int = 15
): LocalDateTime {
    val originalEndMinutes = originalEvent.endTime.hour * 60 + originalEvent.endTime.minute
    val newEndMinutes = (originalEndMinutes + resizeOffsetMinutes).coerceIn(0, 24 * 60)
    val snappedMinutes = roundToMinutes(newEndMinutes, snapInterval)
    
    // 最小時間を確保
    val originalStartMinutes = originalEvent.startTime.hour * 60 + originalEvent.startTime.minute
    val finalEndMinutes = (snappedMinutes).coerceAtLeast(originalStartMinutes + minDurationMinutes)
    
    val hour = finalEndMinutes / 60
    val minute = finalEndMinutes % 60
    
    return originalEvent.endTime
        .withHour(hour.coerceIn(0, 23))
        .withMinute(minute)
        .withSecond(0)
        .withNano(0)
}
