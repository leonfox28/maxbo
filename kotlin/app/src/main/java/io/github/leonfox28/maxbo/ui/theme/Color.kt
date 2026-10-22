package io.github.leonfox28.maxbo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The Flutter app's `ColorScheme.fromSeed(Color(0xFF3F51B5))`, printed from
// Flutter 3.47 and copied here, so both apps have the same colours without a
// colour library. The seed is deliberately not Weibo's brand orange: maxbo
// should not look official. `background` and `surfaceVariant`, which Flutter
// has dropped, take the colours Flutter uses in their place.

val LightColors =
    lightColorScheme(
        primary = Color(0xFF515B92),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDEE0FF),
        onPrimaryContainer = Color(0xFF394379),
        primaryFixed = Color(0xFFDEE0FF),
        primaryFixedDim = Color(0xFFBAC3FF),
        onPrimaryFixed = Color(0xFF0B154B),
        onPrimaryFixedVariant = Color(0xFF394379),
        inversePrimary = Color(0xFFBAC3FF),
        secondary = Color(0xFF5B5D72),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E1F9),
        onSecondaryContainer = Color(0xFF434659),
        secondaryFixed = Color(0xFFE0E1F9),
        secondaryFixedDim = Color(0xFFC3C5DD),
        onSecondaryFixed = Color(0xFF181A2C),
        onSecondaryFixedVariant = Color(0xFF434659),
        tertiary = Color(0xFF77536D),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD7F1),
        onTertiaryContainer = Color(0xFF5D3C55),
        tertiaryFixed = Color(0xFFFFD7F1),
        tertiaryFixedDim = Color(0xFFE6BAD7),
        onTertiaryFixed = Color(0xFF2D1228),
        onTertiaryFixedVariant = Color(0xFF5D3C55),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        surface = Color(0xFFFBF8FF),
        onSurface = Color(0xFF1B1B21),
        onSurfaceVariant = Color(0xFF46464F),
        surfaceDim = Color(0xFFDBD9E0),
        surfaceBright = Color(0xFFFBF8FF),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F2FA),
        surfaceContainer = Color(0xFFEFEDF4),
        surfaceContainerHigh = Color(0xFFE9E7EF),
        surfaceContainerHighest = Color(0xFFE4E1E9),
        surfaceTint = Color(0xFF515B92),
        inverseSurface = Color(0xFF303036),
        inverseOnSurface = Color(0xFFF2EFF7),
        outline = Color(0xFF767680),
        outlineVariant = Color(0xFFC7C5D0),
        scrim = Color(0xFF000000),
        background = Color(0xFFFBF8FF),
        onBackground = Color(0xFF1B1B21),
        surfaceVariant = Color(0xFFE4E1E9),
    )

val DarkColors =
    darkColorScheme(
        primary = Color(0xFFBAC3FF),
        onPrimary = Color(0xFF222C61),
        primaryContainer = Color(0xFF394379),
        onPrimaryContainer = Color(0xFFDEE0FF),
        primaryFixed = Color(0xFFDEE0FF),
        primaryFixedDim = Color(0xFFBAC3FF),
        onPrimaryFixed = Color(0xFF0B154B),
        onPrimaryFixedVariant = Color(0xFF394379),
        inversePrimary = Color(0xFF515B92),
        secondary = Color(0xFFC3C5DD),
        onSecondary = Color(0xFF2D2F42),
        secondaryContainer = Color(0xFF434659),
        onSecondaryContainer = Color(0xFFE0E1F9),
        secondaryFixed = Color(0xFFE0E1F9),
        secondaryFixedDim = Color(0xFFC3C5DD),
        onSecondaryFixed = Color(0xFF181A2C),
        onSecondaryFixedVariant = Color(0xFF434659),
        tertiary = Color(0xFFE6BAD7),
        onTertiary = Color(0xFF44263D),
        tertiaryContainer = Color(0xFF5D3C55),
        onTertiaryContainer = Color(0xFFFFD7F1),
        tertiaryFixed = Color(0xFFFFD7F1),
        tertiaryFixedDim = Color(0xFFE6BAD7),
        onTertiaryFixed = Color(0xFF2D1228),
        onTertiaryFixedVariant = Color(0xFF5D3C55),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        surface = Color(0xFF121318),
        onSurface = Color(0xFFE4E1E9),
        onSurfaceVariant = Color(0xFFC7C5D0),
        surfaceDim = Color(0xFF121318),
        surfaceBright = Color(0xFF39393F),
        surfaceContainerLowest = Color(0xFF0D0E13),
        surfaceContainerLow = Color(0xFF1B1B21),
        surfaceContainer = Color(0xFF1F1F25),
        surfaceContainerHigh = Color(0xFF29292F),
        surfaceContainerHighest = Color(0xFF34343A),
        surfaceTint = Color(0xFFBAC3FF),
        inverseSurface = Color(0xFFE4E1E9),
        inverseOnSurface = Color(0xFF303036),
        outline = Color(0xFF90909A),
        outlineVariant = Color(0xFF46464F),
        scrim = Color(0xFF000000),
        background = Color(0xFF121318),
        onBackground = Color(0xFFE4E1E9),
        surfaceVariant = Color(0xFF34343A),
    )

/**
 * Marks what has something new for the user, such as unread notifications. The same red in light
 * and dark themes, unlike the scheme's error colour.
 */
val UnreadColor = Color(0xFFF5222D)
