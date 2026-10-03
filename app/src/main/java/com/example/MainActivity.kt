package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.NavTab
import com.example.ui.notes.NotesScreen
import com.example.ui.search.SearchScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.translation.TranslationScreen
import com.example.ui.viewmodel.NotesViewModel
import com.example.ui.viewmodel.SearchViewModel
import com.example.ui.viewmodel.TranslationViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppContainer()
            }
        }
    }
}

@Composable
fun MainAppContainer() {
    var selectedTab by rememberSaveable { mutableStateOf(NavTab.TRANSLATION) }

    val translationViewModel: TranslationViewModel = viewModel()
    val searchViewModel: SearchViewModel = viewModel()
    val notesViewModel: NotesViewModel = viewModel()

    // Geri tuşu: Eğer Arama veya Notlar sekmesindeyse Çeviri sekmesine döner
    BackHandler(enabled = selectedTab != NavTab.TRANSLATION) {
        selectedTab = NavTab.TRANSLATION
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.testTag("main_bottom_nav_bar")
            ) {
                NavTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag(tab.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (selectedTab) {
            NavTab.TRANSLATION -> {
                TranslationScreen(
                    viewModel = translationViewModel,
                    modifier = screenModifier
                )
            }
            NavTab.SEARCH -> {
                SearchScreen(
                    viewModel = searchViewModel,
                    modifier = screenModifier
                )
            }
            NavTab.NOTES -> {
                NotesScreen(
                    viewModel = notesViewModel,
                    modifier = screenModifier
                )
            }
        }
    }
}
