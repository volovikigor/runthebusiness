package com.example.runthebusiness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.runthebusiness.ui.cafe.CafeDashboardScreen
import com.example.runthebusiness.ui.debug.DatabaseInspectorScreen
import com.example.runthebusiness.ui.theme.RunthebusinessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunthebusinessTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                icon = { Text("☕") },
                                label = { Text("Café") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                icon = { Text("≡") },
                                label = { Text("Database") }
                            )
                        }
                    }
                ) { padding ->
                    if (selectedTab == 0) {
                        CafeDashboardScreen(modifier = Modifier.padding(padding))
                    } else {
                        DatabaseInspectorScreen(modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}
