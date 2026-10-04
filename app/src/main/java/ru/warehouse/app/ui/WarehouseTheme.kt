package ru.warehouse.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarehouseLight = lightColorScheme(
    primary = Color(0xFF183B63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFA),
    onPrimaryContainer = Color(0xFF0B2746),
    secondary = Color(0xFFF07818),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE7D2),
    onSecondaryContainer = Color(0xFF3E1C00),
    tertiary = Color(0xFF287A53),
    background = Color(0xFFF3F6FA),
    surface = Color(0xFFFCFDFE),
    surfaceVariant = Color(0xFFE7EDF4),
    outline = Color(0xFFBBC7D5),
)

private val WarehouseDark = darkColorScheme(
    primary = Color(0xFF98C5FA),
    onPrimary = Color(0xFF09345D),
    primaryContainer = Color(0xFF19466F),
    onPrimaryContainer = Color(0xFFD8E9FF),
    secondary = Color(0xFFFFB77A),
    onSecondary = Color(0xFF4C2500),
    secondaryContainer = Color(0xFF683900),
    onSecondaryContainer = Color(0xFFFFDCC1),
    tertiary = Color(0xFF86D6AA),
    background = Color(0xFF0C1723),
    surface = Color(0xFF132233),
    surfaceVariant = Color(0xFF26384A),
    outline = Color(0xFF536477),
)

@Composable
fun WarehouseTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) WarehouseDark else WarehouseLight,
        content = content,
    )
}