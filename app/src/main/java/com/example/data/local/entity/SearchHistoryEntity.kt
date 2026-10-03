package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Arama geçmişi kaydı varlığı (Room).
 */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val filterType: String = "ALL", // ALL, PDF, WORD, EXCEL, DRIVE, VIDEO
    val fullSearchUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)
