package com.example.madverse.ui.theme

import androidx.compose.runtime.Composable
import com.example.madverse.core.designsystem.theme.MADVerseTheme as CoreMADVerseTheme

@Composable
fun MADVerseTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CoreMADVerseTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        content = content
    )
}