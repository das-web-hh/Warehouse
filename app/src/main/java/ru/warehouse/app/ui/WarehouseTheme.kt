package ru.warehouse.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Палитра MD3 из warehouse.html (html[data-theme="light" | "dark"])
private val WarehouseLight = lightColorScheme(
    primary = Color(0xFF2B5CB0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFFF07818),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE3F1),
    onSecondaryContainer = Color(0xFF131B2B),
    tertiary = Color(0xFF146C3E),
    tertiaryContainer = Color(0xFFC9F1D8),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF171B22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171B22),
    surfaceVariant = Color(0xFFE9EDF5),
    onSurfaceVariant = Color(0xFF414853),
    outline = Color(0xFF727A88),
    outlineVariant = Color(0xFFC9CFDB),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFBDAD6),
)

private val WarehouseDark = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF274B85),
    onPrimaryContainer = Color(0xFFD9E4FF),
    secondary = Color(0xFFFFB77A),
    onSecondary = Color(0xFF4C2500),
    secondaryContainer = Color(0xFF2B3242),
    onSecondaryContainer = Color(0xFFDCE3F1),
    tertiary = Color(0xFF6FD39A),
    tertiaryContainer = Color(0xFF123D26),
    background = Color(0xFF0F1114),
    onBackground = Color(0xFFE3E5EA),
    surface = Color(0xFF171A1F),
    onSurface = Color(0xFFE3E5EA),
    surfaceVariant = Color(0xFF1E2229),
    onSurfaceVariant = Color(0xFFA6ACB6),
    outline = Color(0xFF6F7580),
    outlineVariant = Color(0xFF2A2F37),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5C1A16),
)

@Composable
fun WarehouseTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) WarehouseDark else WarehouseLight,
        content = content
    )
}
