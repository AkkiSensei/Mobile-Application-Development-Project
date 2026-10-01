package com.example.madverse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.madverse.core.designsystem.theme.MADVerseTheme
import com.example.madverse.core.navigation.MADVerseAppShell
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MADVerseTheme {
                MADVerseAppShell()
            }
        }
    }
}