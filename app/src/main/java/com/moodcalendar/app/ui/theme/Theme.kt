package com.moodcalendar.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.moodcalendar.app.data.model.ThemeStyle

private val MaleBlueLight = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF0277BD),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB3E5FC),
    tertiary = Color(0xFF00838F),
    background = Color(0xFFF3F8FC),
    onBackground = Color(0xFF0D1B2A),
    surface = Color(0xFFF7FBFF),
    onSurface = Color(0xFF0D1B2A),
    surfaceVariant = Color(0xFFD6E4F0),
    onSurfaceVariant = Color(0xFF3D5166),
    outline = Color(0xFF7A90A4)
)

private val MaleBlueDark = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D47A1),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFE3F2FD),
    secondary = Color(0xFF81D4FA),
    background = Color(0xFF0B1520),
    onBackground = Color(0xFFE3EEF8),
    surface = Color(0xFF122033),
    onSurface = Color(0xFFE3EEF8),
    surfaceVariant = Color(0xFF243447),
    onSurfaceVariant = Color(0xFFB0C4D8)
)

private val FemalePinkLight = lightColorScheme(
    primary = Color(0xFFC2185B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF8BBD0),
    onPrimaryContainer = Color(0xFF880E4F),
    secondary = Color(0xFFAD1457),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE4EC),
    tertiary = Color(0xFF8E24AA),
    background = Color(0xFFFFF5F8),
    onBackground = Color(0xFF2A121A),
    surface = Color(0xFFFFF8FA),
    onSurface = Color(0xFF2A121A),
    surfaceVariant = Color(0xFFF3D6E0),
    onSurfaceVariant = Color(0xFF664055),
    outline = Color(0xFFA67C90)
)

private val FemalePinkDark = darkColorScheme(
    primary = Color(0xFFF48FB1),
    onPrimary = Color(0xFF880E4F),
    primaryContainer = Color(0xFFC2185B),
    onPrimaryContainer = Color(0xFFFCE4EC),
    secondary = Color(0xFFF8BBD0),
    background = Color(0xFF1A0F14),
    onBackground = Color(0xFFFCE4EC),
    surface = Color(0xFF261820),
    onSurface = Color(0xFFFCE4EC),
    surfaceVariant = Color(0xFF3D2833),
    onSurfaceVariant = Color(0xFFE0B8C8)
)

@Composable
fun MoodCalendarTheme(
    themeStyle: ThemeStyle = ThemeStyle.MALE_BLUE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeStyle) {
        ThemeStyle.MALE_BLUE -> if (darkTheme) MaleBlueDark else MaleBlueLight
        ThemeStyle.FEMALE_PINK -> if (darkTheme) FemalePinkDark else FemalePinkLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
