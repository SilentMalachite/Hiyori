package app.compose.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 時刻ピッカーダイアログ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: String,
    initialTime: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    var selectedHour by remember { mutableStateOf(initialTime.hour) }
    var selectedMinute by remember { mutableStateOf(initialTime.minute) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(200.dp)
            ) {
                // 時のピッカー
                TimeColumn(
                    values = (0..23).toList(),
                    selectedValue = selectedHour,
                    onValueSelected = { selectedHour = it },
                    modifier = Modifier.weight(1f)
                )
                
                Text(":", style = MaterialTheme.typography.headlineMedium)
                
                // 分のピッカー（15分刻み）
                TimeColumn(
                    values = listOf(0, 15, 30, 45),
                    selectedValue = selectedMinute,
                    onValueSelected = { selectedMinute = it },
                    modifier = Modifier.weight(1f),
                    formatValue = { "%02d".format(it) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newTime = initialTime
                        .withHour(selectedHour)
                        .withMinute(selectedMinute)
                        .withSecond(0)
                        .withNano(0)
                    onConfirm(newTime)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}

/**
 * 時刻選択用のスクロール可能な列
 */
@Composable
private fun TimeColumn(
    values: List<Int>,
    selectedValue: Int,
    onValueSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    formatValue: (Int) -> String = { "%02d".format(it) }
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = values.indexOfFirst { it == selectedValue }.coerceAtLeast(0)
    )
    
    Surface(
        modifier = modifier,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.small
    ) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxHeight()
        ) {
            items(values) { value ->
                val isSelected = value == selectedValue
                
                TextButton(
                    onClick = { onValueSelected(value) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isSelected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = formatValue(value),
                        style = if (isSelected) 
                            MaterialTheme.typography.headlineSmall 
                        else 
                            MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * 日付と時刻を選択するダイアログ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    title: String,
    initialDateTime: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalDateTime) -> Unit
) {
    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedHour by remember { mutableStateOf(initialDateTime.hour) }
    var selectedMinute by remember { mutableStateOf(initialDateTime.minute) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 日付表示（簡易版、編集不可）
                Text(
                    text = "日付: ${selectedDate}",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                // 時刻ピッカー
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(200.dp)
                ) {
                    // 時のピッカー
                    TimeColumn(
                        values = (0..23).toList(),
                        selectedValue = selectedHour,
                        onValueSelected = { selectedHour = it },
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(":", style = MaterialTheme.typography.headlineMedium)
                    
                    // 分のピッカー（15分刻み）
                    TimeColumn(
                        values = listOf(0, 15, 30, 45),
                        selectedValue = selectedMinute,
                        onValueSelected = { selectedMinute = it },
                        modifier = Modifier.weight(1f),
                        formatValue = { "%02d".format(it) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newDateTime = LocalDateTime.of(
                        selectedDate,
                        LocalTime.of(selectedHour, selectedMinute, 0)
                    )
                    onConfirm(newDateTime)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}
