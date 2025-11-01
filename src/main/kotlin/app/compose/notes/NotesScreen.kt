package app.compose.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val log = remember { LoggerFactory.getLogger("NotesScreen") }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(listOf<NoteUi>()) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    // Editor state for the selected note
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<NoteUi?>(null) }

    // For debounced search and autosave
    val searchFlow = remember { MutableStateFlow("") }
    var saveJob by remember { mutableStateOf<Job?>(null) }

    fun select(id: Long?) {
        selectedId = id
        current = notes.firstOrNull { it.id == id }
        title = current?.title ?: ""
        body = current?.body ?: ""
    }

    fun newNote() {
        scope.launch {
            val created = runCatching { NoteBackend.create() }.getOrElse { e ->
                log.warn("Create failed: {}", e.message)
                return@launch
            }
            // Prepend new note and select
            notes = listOf(created) + notes
            select(created.id)
        }
    }

    fun saveNow() {
        val c = current ?: return
        val updated = c.copy(title = title, body = body)
        if (updated == c) return
        scope.launch {
            val refreshed = runCatching { NoteBackend.update(updated) }
                .onFailure { log.warn("Save failed: {}", it.message) }
                .getOrNull() ?: return@launch
            // Update list item
            notes = notes.map { if (it.id == refreshed.id) refreshed else it }
            current = refreshed
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        runCatching { NoteBackend.listRecent() }
            .onSuccess {
                notes = it
                if (it.isNotEmpty()) select(it.first().id)
            }
            .onFailure { log.warn("Initial load failed: {}", it.message) }
    }

    // Debounced search
    LaunchedEffect(Unit) {
        searchFlow
            .debounce(200)
            .collectLatest { q ->
                val list = runCatching {
                    if (q.isBlank()) NoteBackend.listRecent() else NoteBackend.search(q)
                }.getOrElse { e ->
                    log.warn("Search failed: {}", e.message)
                    emptyList()
                }
                notes = list
                // Keep selection if possible
                if (selectedId == null || list.none { it.id == selectedId }) {
                    selectedId = list.firstOrNull()?.id
                }
                current = list.firstOrNull { it.id == selectedId }
                title = current?.title ?: ""
                body = current?.body ?: ""
            }
    }

    // Debounced autosave on title/body change
    LaunchedEffect(title, body, current?.id) {
        saveJob?.cancel()
        saveJob = scope.launch {
            kotlinx.coroutines.delay(600)
            saveNow()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
        TopAppBar(
            title = { Text("Notes") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            },
            actions = {
                IconButton(onClick = { newNote() }) { Icon(Icons.Filled.NoteAdd, contentDescription = "New") }
                IconButton(onClick = { saveNow() }) { Icon(Icons.Filled.Save, contentDescription = "Save") }
            }
        )
        Spacer(Modifier.height(8.dp))

        // Search
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                searchFlow.value = it
            },
            placeholder = { Text("Search notes…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter)) {
                        // Focus list (not implemented yet)
                        true
                    } else false
                },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { /* already debounced */ })
        )

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxSize()) {
            // List
            val listState = rememberLazyListState()
            Surface(tonalElevation = 1.dp, modifier = Modifier.width(300.dp).fillMaxHeight()) {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(end = 8.dp)) {
                        items(notes, key = { it.id }) { n ->
                            ListItem(
                                headlineContent = { Text(n.title.ifBlank { "無題のメモ" }) },
                                supportingContent = { Text(n.body.take(80)) },
                                modifier = Modifier.fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .combinedClickable(
                                        onClick = { select(n.id) },
                                        onDoubleClick = { select(n.id) }
                                    )
                            )
                            HorizontalDivider()
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Editor
            Column(Modifier.weight(1f).fillMaxHeight()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                val editorScroll = rememberScrollState()
                Surface(tonalElevation = 1.dp, modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        BasicTextField(
                            value = body,
                            onValueChange = { body = it },
                            textStyle = TextStyle.Default,
                            modifier = Modifier.fillMaxSize().padding(12.dp)
                                .verticalScroll(editorScroll)
                                .onPreviewKeyEvent { e ->
                                    val meta = e.isCtrlOrMetaPressed
                                    if (e.type == KeyEventType.KeyDown && meta && e.key == Key.S) {
                                        saveNow(); true
                                    } else false
                                }
                        )
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(editorScroll),
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

private val KeyEvent.isCtrlOrMetaPressed: Boolean
    get() = (isCtrlPressed || isMetaPressed)
