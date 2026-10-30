package ru.zhurnal.app.ui.theme

import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF14304A)
val InkDeep = Color(0xFF0B2236)
val Paper = Color(0xFFF7F4ED)
val Ochre = Color(0xFFC08135)
val Sage = Color(0xFF2F6B5B)

val LightColors = androidx.compose.material3.lightColorScheme(
    primary = Ink,
    onPrimary = Color(0xFFFFFBF3),
    primaryContainer = Color(0xFFDCE9F5),
    onPrimaryContainer = InkDeep,
    secondary = Color(0xFF9A641F),
    onSecondary = Color(0xFFFFFBF3),
    secondaryContainer = Color(0xFFF6E7CD),
    onSecondaryContainer = Color(0xFF402B0B),
    tertiary = Sage,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEEE8),
    onTertiaryContainer = Color(0xFF0C2E26),
    background = Paper,
    onBackground = Color(0xFF1B1F24),
    surface = Color(0xFFFFFDF7),
    onSurface = Color(0xFF1B1F24),
    surfaceVariant = Color(0xFFEDE7DA),
    onSurfaceVariant = Color(0xFF5C5B53),
    outline = Color(0xFFD3CCBC),
    outlineVariant = Color(0xFFE5DECF),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surfaceTint = Ink,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = Color(0xFFA9C9E6),
    scrim = Color.Black,
)

val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = Color(0xFFA9C9E6),
    onPrimary = InkDeep,
    primaryContainer = Color(0xFF1C3A55),
    onPrimaryContainer = Color(0xFFD9EAF9),
    secondary = Color(0xFFE8B76C),
    onSecondary = Color(0xFF2C1D06),
    secondaryContainer = Color(0xFF4A3512),
    onSecondaryContainer = Color(0xFFF8E3C2),
    tertiary = Color(0xFF84C7B3),
    onTertiary = Color(0xFF08251E),
    tertiaryContainer = Color(0xFF1C4B3F),
    onTertiaryContainer = Color(0xFFD0EEE5),
    background = Color(0xFF0B1219),
    onBackground = Color(0xFFECE6DA),
    surface = Color(0xFF111B24),
    onSurface = Color(0xFFECE6DA),
    surfaceVariant = Color(0xFF1B2834),
    onSurfaceVariant = Color(0xFFB9C4CE),
    outline = Color(0xFF3E4C59),
    outlineVariant = Color(0xFF2A3742),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    surfaceTint = Color(0xFFA9C9E6),
    inverseSurface = Color(0xFFECE6DA),
    inverseOnSurface = Color(0xFF1B1F24),
    inversePrimary = Ink,
    scrim = Color.Black,
)
