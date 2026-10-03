package com.example.data.repository

import com.example.data.local.dao.NoteDao
import com.example.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {

    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllNotes()

    fun searchNotes(query: String): Flow<List<NoteEntity>> {
        return if (query.isBlank()) {
            noteDao.getAllNotes()
        } else {
            noteDao.searchNotes(query)
        }
    }

    fun getNoteById(id: Long): Flow<NoteEntity?> = noteDao.getNoteById(id)

    suspend fun insertNote(title: String, content: String): Long {
        val now = System.currentTimeMillis()
        val note = NoteEntity(
            title = title.trim(),
            content = content.trim(),
            createdAt = now,
            updatedAt = now
        )
        return noteDao.insertNote(note)
    }

    suspend fun updateNote(note: NoteEntity) {
        val updated = note.copy(updatedAt = System.currentTimeMillis())
        noteDao.updateNote(updated)
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.deleteNote(note)
    }

    suspend fun restoreNote(note: NoteEntity) {
        noteDao.insertNote(note)
    }
}
