package com.tvengineer.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tvengineer.pro.ui.TVEngineerApp
import com.tvengineer.pro.ui.TVEngineerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVEngineerTheme {
                TVEngineerApp()
            }
        }
    }
}
