package com.example.data.repository

import com.example.data.local.dao.SearchHistoryDao
import com.example.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

class SearchHistoryRepository(private val searchHistoryDao: SearchHistoryDao) {

    val allHistory: Flow<List<SearchHistoryEntity>> = searchHistoryDao.getAllHistory()

    suspend fun saveSearch(query: String, filterType: String, fullUrl: String) {
        if (query.isBlank()) return
        val entity = SearchHistoryEntity(
            query = query.trim(),
            filterType = filterType,
            fullSearchUrl = fullUrl,
            timestamp = System.currentTimeMillis()
        )
        searchHistoryDao.insertHistory(entity)
    }

    suspend fun deleteSearch(item: SearchHistoryEntity) {
        searchHistoryDao.deleteHistory(item)
    }

    suspend fun clearAll() {
        searchHistoryDao.clearAllHistory()
    }
}
