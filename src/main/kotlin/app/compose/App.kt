package app.compose

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.compose.notes.NotesScreen
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.Properties

object AppConfigReader {
    private val log = LoggerFactory.getLogger(AppConfigReader::class.java)

    fun load(): Properties {
        val props = Properties()
        val path = "/app.properties"
        val stream: InputStream? = AppConfigReader::class.java.getResourceAsStream(path)
        if (stream == null) {
            log.warn("app.properties not found on classpath at {}", path)
        } else {
            stream.use { props.load(it) }
            log.info("Loaded app.properties: {} entries", props.size)
        }
        return props
    }
}

@Composable
private fun RootScreen(dbPath: String?, onOpenNotes: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Hiyori (Compose Desktop)", style = MaterialTheme.typography.headlineSmall)
                Text("This is the new Compose Desktop scaffold. Migration in progress.")
                if (!dbPath.isNullOrBlank()) {
                    AssistChip(onClick = {}, label = { Text("DB: $dbPath") })
                } else {
                    AssistChip(onClick = {}, label = { Text("DB: (not set)") })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onOpenNotes) { Text("Open Notes") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { /* TODO: Navigate to Week View */ }) { Text("Open Week View") }
                }
            }
        }
    }
}

enum class Screen { Home, Notes }

fun main() = application {
    val log = LoggerFactory.getLogger("ComposeMain")
    val props = remember { AppConfigReader.load() }
    val dbPath = remember { props.getProperty("database.path") }

    var screen by remember { mutableStateOf(Screen.Home) }

    log.info("Starting Hiyori (Compose Desktop)…")
    Window(onCloseRequest = ::exitApplication, title = "Hiyori (Compose)") {
        when (screen) {
            Screen.Home -> RootScreen(dbPath = dbPath, onOpenNotes = { screen = Screen.Notes })
            Screen.Notes -> NotesScreen(onBack = { screen = Screen.Home })
        }
    }
}
