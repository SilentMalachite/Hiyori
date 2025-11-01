package app.compose.events

import app.config.AppConfig
import app.db.Database
import app.db.EventsDao
import app.db.TransactionManager
import app.exception.DataAccessException
import app.service.EventService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Kotlin-friendly bridge over existing Java EventService/DAO/DB layer.
 */
object EventBackend {
    private val log = LoggerFactory.getLogger("EventBackend")

    // Lazy singletons – reuse across the app lifetime
    private val config: AppConfig by lazy { AppConfig.getInstance() }
    private val database: Database by lazy {
        val path = config.databasePath
        log.info("Initializing Database at {}", path)
        Database(path).also { it.initialize() }
    }
    private val tx: TransactionManager by lazy { TransactionManager(database) }
    private val dao: EventsDao by lazy { EventsDao(database, tx) }
    private val svc: EventService by lazy { EventService(dao, tx) }

    suspend fun getEventsForWeek(weekRange: WeekRange): List<EventUi> = io {
        val startOfDay = weekRange.start.atStartOfDay(ZoneId.systemDefault())
        val endOfDay = weekRange.end.plusDays(1).atStartOfDay(ZoneId.systemDefault())
        
        val startEpochSec = startOfDay.toEpochSecond()
        val endEpochSec = endOfDay.toEpochSecond()
        
        svc.getEventsBetween(startEpochSec, endEpochSec).map { it.toUi() }
    }

    suspend fun create(title: String, startEpochSec: Long, endEpochSec: Long): EventUi = io {
        svc.createEvent(title, startEpochSec, endEpochSec).toUi()
    }

    suspend fun update(event: EventUi): EventUi = io {
        val j = event.toJava()
        svc.updateEvent(j)
        // Return updated event (could fetch from DB for timestamps, but not necessary here)
        event
    }

    suspend fun delete(id: Long) = io {
        svc.deleteEvent(id)
    }

    private suspend fun <T> io(block: () -> T): T = try {
        withContext(Dispatchers.IO) { block() }
    } catch (e: DataAccessException) {
        throw RuntimeException(e)
    }
}
