package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavTab(
    val title: String,
    val icon: ImageVector,
    val testTag: String
) {
    TRANSLATION("Çeviri", Icons.Default.Translate, "tab_translation"),
    SEARCH("Arama", Icons.Default.Search, "tab_search"),
    NOTES("Notlar", Icons.Default.Description, "tab_notes")
}
