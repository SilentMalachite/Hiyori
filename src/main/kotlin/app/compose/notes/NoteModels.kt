package app.compose.notes

import app.model.Note as JNote

// Kotlin-side UI model for Note
data class NoteUi(
    val id: Long = 0L,
    val title: String = "",
    val body: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

fun JNote.toUi(): NoteUi = NoteUi(
    id = id,
    title = title ?: "",
    body = body ?: "",
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun NoteUi.toJava(): JNote = JNote().also {
    it.id = id
    it.title = title
    it.body = body
    it.createdAt = createdAt
    it.updatedAt = updatedAt
}
