package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.SearchHistoryEntity
import com.example.data.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URLEncoder

enum class SearchFilter(val label: String, val chipName: String) {
    ALL("Tümü", "TÜMÜ"),
    PDF("PDF", "PDF"),
    WORD("Word", "WORD"),
    EXCEL("Excel", "EXCEL"),
    DRIVE("Google Drive", "DRIVE"),
    VIDEO("Video", "VİDEO")
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SearchHistoryRepository(
        AppDatabase.getInstance(application).searchHistoryDao()
    )

    val historyList: StateFlow<List<SearchHistoryEntity>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(SearchFilter.ALL)
    val selectedFilter: StateFlow<SearchFilter> = _selectedFilter.asStateFlow()

    private val _activeSearchUrl = MutableStateFlow<String?>(null)
    val activeSearchUrl: StateFlow<String?> = _activeSearchUrl.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterSelect(filter: SearchFilter) {
        _selectedFilter.value = filter
    }

    fun performSearch(queryToSearch: String = _searchQuery.value) {
        val trimmed = queryToSearch.trim()
        if (trimmed.isBlank()) return

        val filter = _selectedFilter.value
        val (formattedQuery, isVideoTab) = when (filter) {
            SearchFilter.ALL -> trimmed to false
            SearchFilter.PDF -> "$trimmed filetype:pdf" to false
            SearchFilter.WORD -> "$trimmed (filetype:doc OR filetype:docx)" to false
            SearchFilter.EXCEL -> "$trimmed (filetype:xls OR filetype:xlsx)" to false
            SearchFilter.DRIVE -> "$trimmed site:drive.google.com" to false
            SearchFilter.VIDEO -> trimmed to true
        }

        val encoded = try {
            URLEncoder.encode(formattedQuery, "UTF-8")
        } catch (_: Exception) {
            formattedQuery
        }

        val fullUrl = if (isVideoTab) {
            "https://www.google.com/search?q=$encoded&tbm=vid"
        } else {
            "https://www.google.com/search?q=$encoded"
        }

        _activeSearchUrl.value = fullUrl

        viewModelScope.launch {
            repository.saveSearch(
                query = trimmed,
                filterType = filter.name,
                fullUrl = fullUrl
            )
        }
    }

    fun searchFromHistory(item: SearchHistoryEntity) {
        _searchQuery.value = item.query
        val filter = try {
            SearchFilter.valueOf(item.filterType)
        } catch (_: Exception) {
            SearchFilter.ALL
        }
        _selectedFilter.value = filter
        _activeSearchUrl.value = item.fullSearchUrl
    }

    fun deleteHistoryItem(item: SearchHistoryEntity) {
        viewModelScope.launch {
            repository.deleteSearch(item)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun closeWebView() {
        _activeSearchUrl.value = null
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
}
