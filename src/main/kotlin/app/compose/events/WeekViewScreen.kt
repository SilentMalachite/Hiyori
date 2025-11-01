package app.compose.events

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HOUR_HEIGHT = 60.dp
private val COLUMN_WIDTH = 120.dp
private val TIME_LABEL_WIDTH = 50.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekViewScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val log = remember { LoggerFactory.getLogger("WeekViewScreen") }
    val scope = rememberCoroutineScope()
    
    var currentWeek by remember { mutableStateOf(getThisWeek()) }
    var events by remember { mutableStateOf(listOf<EventUi>()) }
    var selectedEvent by remember { mutableStateOf<EventUi?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    // Load events for the current week
    fun loadEvents() {
        scope.launch {
            runCatching { EventBackend.getEventsForWeek(currentWeek) }
                .onSuccess { events = it }
                .onFailure { log.warn("Failed to load events: {}", it.message) }
        }
    }
    
    // Initial load
    LaunchedEffect(currentWeek) {
        loadEvents()
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("週ビュー") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                }
            },
            actions = {
                IconButton(onClick = { 
                    currentWeek = getWeekRange(currentWeek.start.minusWeeks(1))
                }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "前週")
                }
                IconButton(onClick = {
                    currentWeek = getThisWeek()
                }) {
                    Icon(Icons.Filled.Today, contentDescription = "今週")
                }
                IconButton(onClick = {
                    currentWeek = getWeekRange(currentWeek.start.plusWeeks(1))
                }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "次週")
                }
                IconButton(onClick = {
                    // Create new event at current time
                    scope.launch {
                        val now = LocalDateTime.now()
                        val startEpochSec = now.atZone(ZoneId.systemDefault()).toEpochSecond()
                        val endEpochSec = startEpochSec + (90 * 60) // 90 minutes
                        
                        runCatching {
                            EventBackend.create("新しい予定", startEpochSec, endEpochSec)
                        }
                            .onSuccess { loadEvents() }
                            .onFailure { log.warn("Failed to create event: {}", it.message) }
                    }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "新規予定")
                }
            }
        )
        
        // Week header
        WeekHeader(currentWeek)
        
        HorizontalDivider()
        
        // Week grid
        Box(modifier = Modifier.fillMaxSize()) {
            WeekGrid(
                weekRange = currentWeek,
                events = events,
                onEventClick = { event ->
                    selectedEvent = event
                    showEditDialog = true
                },
                onEventDrag = { event, offsetMinutes ->
                    // ドラッグでイベントを移動
                    scope.launch {
                        val newStartTime = calculateNewStartTime(event, offsetMinutes)
                        val duration = event.durationMinutes
                        val newEndTime = newStartTime.plusMinutes(duration)
                        
                        val updatedEvent = event.copy(
                            startEpochSec = newStartTime.atZone(ZoneId.systemDefault()).toEpochSecond(),
                            endEpochSec = newEndTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                        )
                        
                        runCatching { EventBackend.update(updatedEvent) }
                            .onSuccess { loadEvents() }
                            .onFailure { log.warn("Failed to update event: {}", it.message) }
                    }
                },
                onEventResize = { event, offsetMinutes ->
                    // リサイズで終了時刻を調整
                    scope.launch {
                        val newEndTime = calculateNewEndTime(event, offsetMinutes)
                        
                        val updatedEvent = event.copy(
                            endEpochSec = newEndTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                        )
                        
                        runCatching { EventBackend.update(updatedEvent) }
                            .onSuccess { loadEvents() }
                            .onFailure { log.warn("Failed to resize event: {}", it.message) }
                    }
                }
            )
        }
    }
    
    // Edit dialog
    if (showEditDialog && selectedEvent != null) {
        EventEditDialog(
            event = selectedEvent!!,
            onDismiss = { showEditDialog = false },
            onSave = { updated ->
                scope.launch {
                    runCatching { EventBackend.update(updated) }
                        .onSuccess {
                            showEditDialog = false
                            loadEvents()
                        }
                        .onFailure { log.warn("Failed to update event: {}", it.message) }
                }
            },
            onDelete = {
                scope.launch {
                    runCatching { EventBackend.delete(selectedEvent!!.id) }
                        .onSuccess {
                            showEditDialog = false
                            loadEvents()
                        }
                        .onFailure { log.warn("Failed to delete event: {}", it.message) }
                }
            }
        )
    }
}

@Composable
private fun WeekHeader(weekRange: WeekRange) {
    val dateFormatter = DateTimeFormatter.ofPattern("M/d")
    val dayFormatter = DateTimeFormatter.ofPattern("EEE")
    
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // Time column spacer
        Spacer(Modifier.width(TIME_LABEL_WIDTH))
        
        // Day columns
        weekRange.days.forEach { day ->
            Column(
                modifier = Modifier.width(COLUMN_WIDTH),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = day.format(dayFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (day == LocalDate.now()) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = day.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (day == LocalDate.now()) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeekGrid(
    weekRange: WeekRange,
    events: List<EventUi>,
    onEventClick: (EventUi) -> Unit,
    onEventDrag: (EventUi, Int) -> Unit,
    onEventResize: (EventUi, Int) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Time labels
            Column(
                modifier = Modifier
                    .width(TIME_LABEL_WIDTH)
                    .verticalScroll(scrollState)
            ) {
                (0..23).forEach { hour ->
                    Box(
                        modifier = Modifier.height(HOUR_HEIGHT),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Text(
                            text = String.format("%02d:00", hour),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(end = 4.dp, top = (-6).dp)
                        )
                    }
                }
            }
            
            // Day columns with grid
            Row(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
                weekRange.days.forEach { day ->
                    Box(modifier = Modifier.width(COLUMN_WIDTH).fillMaxHeight()) {
                        // Grid lines
                        Column {
                            (0..23).forEach { hour ->
                                Box(
                                    modifier = Modifier
                                        .height(HOUR_HEIGHT)
                                        .fillMaxWidth()
                                        .border(
                                            width = 0.5.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                )
                            }
                        }
                        
                        // Events for this day
                        EventsForDay(
                            day = day,
                            events = events.filter { event ->
                                event.startTime.toLocalDate() == day
                            },
                            onClick = onEventClick,
                            onDrag = onEventDrag,
                            onResize = onEventResize
                        )
                    }
                }
            }
        }
        
        // Scrollbar
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
        )
    }
}

@Composable
private fun EventsForDay(
    day: LocalDate,
    events: List<EventUi>,
    onClick: (EventUi) -> Unit,
    onDrag: (EventUi, Int) -> Unit,
    onResize: (EventUi, Int) -> Unit
) {
    // 衝突検出と列分割の計算
    val layouts = remember(events) { calculateEventLayouts(events) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        layouts.forEach { layout ->
            EventCard(
                layout = layout,
                onClick = { onClick(layout.event) },
                onDrag = onDrag,
                onResize = onResize
            )
        }
    }
}

@Composable
private fun EventCard(
    layout: EventLayout,
    onClick: () -> Unit,
    onDrag: ((EventUi, Int) -> Unit)? = null,
    onResize: ((EventUi, Int) -> Unit)? = null
) {
    val event = layout.event
    val startTime = event.startTime
    val endTime = event.endTime
    
    // Calculate position and height
    val startMinutes = startTime.hour * 60 + startTime.minute
    val endMinutes = endTime.hour * 60 + endTime.minute
    val durationMinutes = endMinutes - startMinutes
    
    val topOffset = (startMinutes / 60f) * HOUR_HEIGHT.value
    val height = (durationMinutes / 60f) * HOUR_HEIGHT.value
    
    // 列分割の計算
    val columnWidth = 1f / layout.totalColumns
    val columnOffset = layout.column * columnWidth
    
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    
    var dragOffsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .offset(
                x = (columnOffset * COLUMN_WIDTH.value).dp,
                y = (topOffset + if (isDragging) dragOffsetY else 0f).dp
            )
            .height(height.dp)
            .width((columnWidth * COLUMN_WIDTH.value).dp)
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .clickable(onClick = onClick)
            .pointerInput(event.id) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount.y
                    },
                    onDragEnd = {
                        if (onDrag != null) {
                            // ドラッグオフセットを分に変換
                            val offsetMinutes = (dragOffsetY / HOUR_HEIGHT.value * 60).toInt()
                            onDrag(event, offsetMinutes)
                        }
                        dragOffsetY = 0f
                        isDragging = false
                    },
                    onDragCancel = {
                        dragOffsetY = 0f
                        isDragging = false
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                Text(
                    text = event.title.ifBlank { "無題" },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${startTime.format(timeFormatter)} - ${endTime.format(timeFormatter)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            
            // 下部リサイズハンドル
            if (onResize != null && height.dp > 30.dp) {
                var resizeOffsetY by remember { mutableStateOf(0f) }
                var isResizing by remember { mutableStateOf(false) }
                
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(8.dp)
                        .pointerInput(event.id) {
                            detectDragGestures(
                                onDragStart = { isResizing = true },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    resizeOffsetY += dragAmount.y
                                },
                                onDragEnd = {
                                    val offsetMinutes = (resizeOffsetY / HOUR_HEIGHT.value * 60).toInt()
                                    onResize(event, offsetMinutes)
                                    resizeOffsetY = 0f
                                    isResizing = false
                                },
                                onDragCancel = {
                                    resizeOffsetY = 0f
                                    isResizing = false
                                }
                            )
                        }
                        .background(
                            if (isResizing) 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else 
                                Color.Transparent
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(30.dp)
                            .height(3.dp)
                            .background(
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditDialog(
    event: EventUi,
    onDismiss: () -> Unit,
    onSave: (EventUi) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(event.title) }
    var startDateTime by remember { mutableStateOf(event.startTime) }
    var endDateTime by remember { mutableStateOf(event.endTime) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("予定を編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("タイトル") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // 開始時刻
                OutlinedButton(
                    onClick = { showStartTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("開始")
                        Text(startDateTime.format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm")))
                    }
                }
                
                // 終了時刻
                OutlinedButton(
                    onClick = { showEndTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("終了")
                        Text(endDateTime.format(DateTimeFormatter.ofPattern("yyyy/M/d HH:mm")))
                    }
                }
                
                // 時間の長さ表示
                val durationMinutes = java.time.Duration.between(startDateTime, endDateTime).toMinutes()
                Text(
                    "時間: ${durationMinutes}分",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val startEpochSec = startDateTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                    val endEpochSec = endDateTime.atZone(ZoneId.systemDefault()).toEpochSecond()
                    
                    onSave(event.copy(
                        title = title,
                        startEpochSec = startEpochSec,
                        endEpochSec = endEpochSec
                    ))
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )) {
                    Text("削除")
                }
                TextButton(onClick = onDismiss) {
                    Text("キャンセル")
                }
            }
        }
    )
    
    // 開始時刻ピッカー
    if (showStartTimePicker) {
        TimePickerDialog(
            title = "開始時刻",
            initialTime = startDateTime,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { newTime ->
                startDateTime = newTime
                // 終了時刻が開始時刻より前なら調整
                if (endDateTime.isBefore(startDateTime) || endDateTime.isEqual(startDateTime)) {
                    endDateTime = startDateTime.plusMinutes(30)
                }
                showStartTimePicker = false
            }
        )
    }
    
    // 終了時刻ピッカー
    if (showEndTimePicker) {
        TimePickerDialog(
            title = "終了時刻",
            initialTime = endDateTime,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { newTime ->
                // 開始時刻より後であることを保証
                if (newTime.isAfter(startDateTime)) {
                    endDateTime = newTime
                }
                showEndTimePicker = false
            }
        )
    }
}
