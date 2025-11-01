package app.compose.notes

import app.config.AppConfig
import app.db.Database
import app.db.NotesDao
import app.db.TransactionManager
import app.exception.DataAccessException
import app.service.NoteService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * Kotlin-friendly bridge over existing Java NoteService/DAO/DB layer.
 */
object NoteBackend {
    private val log = LoggerFactory.getLogger("NoteBackend")

    // Lazy singletons – reuse across the app lifetime
    private val config: AppConfig by lazy { AppConfig.getInstance() }
    private val database: Database by lazy {
        val path = config.databasePath
        log.info("Initializing Database at {}", path)
        Database(path).also { it.initialize() }
    }
    private val tx: TransactionManager by lazy { TransactionManager(database) }
    private val dao: NotesDao by lazy { NotesDao(database, tx) }
    private val svc: NoteService by lazy { NoteService(dao, tx) }

    suspend fun listRecent(): List<NoteUi> = io {
        svc.recentNotes.map { it.toUi() }
    }

    suspend fun search(query: String): List<NoteUi> = io {
        svc.searchNotes(query).map { it.toUi() }
    }

    suspend fun create(title: String = "", body: String = ""): NoteUi = io {
        svc.createNote(title, body).toUi()
    }

    suspend fun update(note: NoteUi): NoteUi = io {
        val j = note.toJava()
        svc.updateNote(j)
        // NoteService updates updatedAt internally; fetch the latest row to reflect timestamps
        try {
            // Use dao.getById for exact refresh
            dao.getById(note.id)?.toUi() ?: note
        } catch (e: DataAccessException) {
            log.warn("Failed to refresh note {} after update: {}", note.id, e.message)
            note
        }
    }

    suspend fun delete(id: Long) = io {
        svc.deleteNote(id)
    }

    private suspend fun <T> io(block: () -> T): T = try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: DataAccessException) {
        throw RuntimeException(e)
    }
}
