# Data Directory

This directory contains the SQLite database files for the Hiyori application.

## Files

- `app.db` - Main SQLite database file
- `app.db-wal` - Write-Ahead Logging file (temporary, created during operation)
- `app.db-shm` - Shared memory file (temporary, created during operation)

## WAL Mode

Hiyori uses SQLite's WAL (Write-Ahead Logging) mode for better concurrency and performance:

- Write operations are appended to the WAL file
- The WAL file is automatically checkpointed (merged back into the main database)
- On application shutdown, a `PRAGMA wal_checkpoint(TRUNCATE)` is executed to consolidate files

## Database Location

The database location can be configured in `src/main/resources/app.properties`:

```properties
database.path=data/app.db
```

You can use:
- Relative paths (relative to the application's working directory)
- Absolute paths (e.g., `/Users/username/hiyori/app.db`)

## Backup

To backup your data:

1. **Simple backup** (while app is not running):
   ```bash
   cp data/app.db data/app.db.backup
   ```

2. **Backup while app is running**:
   ```bash
   sqlite3 data/app.db ".backup data/app.db.backup"
   ```

3. **Export to SQL**:
   ```bash
   sqlite3 data/app.db .dump > backup.sql
   ```

## Development

For development, this directory is ignored by Git (see `.gitignore`).
Each developer will have their own local database.

To start with a fresh database:
```bash
rm data/app.db*
./gradlew run
```

The application will automatically create the database schema on first run.

## Database Schema

The database contains the following tables:

- `notes` - Stores note data (id, title, body, created_at, updated_at)
- `notes_fts` - Full-text search index for notes (virtual FTS5 table)
- `events` - Stores event/schedule data (id, title, start_epoch_sec, end_epoch_sec)

Schema is automatically created by the application on first run.
