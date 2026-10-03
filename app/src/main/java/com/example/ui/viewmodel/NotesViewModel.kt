package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.NoteEntity
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NoteRepository(
        AppDatabase.getInstance(application).noteDao()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val notes: StateFlow<List<NoteEntity>> = _searchQuery
        .flatMapLatest { query -> repository.searchNotes(query) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var lastDeletedNote: NoteEntity? = null
    private val _snackBarMessage = MutableStateFlow<String?>(null)
    val snackBarMessage: StateFlow<String?> = _snackBarMessage.asStateFlow()

    private val _editingNote = MutableStateFlow<NoteEntity?>(null)
    val editingNote: StateFlow<NoteEntity?> = _editingNote.asStateFlow()

    private val _isAddDialogOpen = MutableStateFlow(false)
    val isAddDialogOpen: StateFlow<Boolean> = _isAddDialogOpen.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun openAddDialog() {
        _editingNote.value = null
        _isAddDialogOpen.value = true
    }

    fun openEditDialog(note: NoteEntity) {
        _editingNote.value = note
        _isAddDialogOpen.value = true
    }

    fun closeDialog() {
        _isAddDialogOpen.value = false
        _editingNote.value = null
    }

    fun saveNote(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return
        viewModelScope.launch {
            val current = _editingNote.value
            if (current != null) {
                repository.updateNote(current.copy(title = title.trim(), content = content.trim()))
                _snackBarMessage.value = "Not güncellendi"
            } else {
                repository.insertNote(title.trim(), content.trim())
                _snackBarMessage.value = "Yeni not kaydedildi"
            }
            closeDialog()
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            lastDeletedNote = note
            repository.deleteNote(note)
            _snackBarMessage.value = "Not silindi"
        }
    }

    fun undoDelete() {
        val toRestore = lastDeletedNote ?: return
        viewModelScope.launch {
            repository.restoreNote(toRestore)
            lastDeletedNote = null
            _snackBarMessage.value = "Not geri yüklendi"
        }
    }

    fun clearSnackBarMessage() {
        _snackBarMessage.value = null
    }

    fun shareNote(context: Context, note: NoteEntity) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n${note.content}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val chooser = Intent.createChooser(shareIntent, "Notu Paylaş").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(chooser)
    }

    fun exportNoteToUri(uri: Uri, note: NoteEntity, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream).use { writer ->
                        writer.write("BAŞLIK: ${note.title}\n")
                        writer.write("TARİH: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(note.createdAt))}\n\n")
                        writer.write("İÇERİK:\n${note.content}\n")
                    }
                }
                _snackBarMessage.value = "Not dosyası başarıyla dışa aktarıldı."
                onComplete(true)
            } catch (e: Exception) {
                _snackBarMessage.value = "Dışa aktarma başarısız: ${e.localizedMessage}"
                onComplete(false)
            }
        }
    }
}
