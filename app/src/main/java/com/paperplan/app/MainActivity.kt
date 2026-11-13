package com.paperplan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.paperplan.app.core.ui.theme.PaperPlanTheme
import com.paperplan.app.feature.home.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PaperPlanTheme {
                HomeScreen()
            }
        }
    }
}
