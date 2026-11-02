package com.example.appgestin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF196ED0),
    secondary = Color(0xFF3189E5),
    tertiary = Color(0xFF8ABEFF),
    background = Color(0xFFF4F8FF),
    surface = Color.White,
    onSurface = Color(0xFF19364F)
)

@Composable
fun AppGestiónTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
