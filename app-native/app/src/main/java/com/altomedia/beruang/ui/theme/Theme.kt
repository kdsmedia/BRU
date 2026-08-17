package com.altomedia.beruang.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BeruangColorScheme = lightColorScheme(
    primary = BrandYellow,
    onPrimary = TextMain,
    secondary = BrandRed,
    onSecondary = BgCard,
    background = BgBody,
    onBackground = TextMain,
    surface = BgCard,
    onSurface = TextMain,
    error = ErrorRed,
    onError = BgCard,
    outline = Border,
    surfaceVariant = BgBody,
)

/** App-wide Material3 theme. Light only — matches the original web design. */
@Composable
fun BeruangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BeruangColorScheme,
        typography = BeruangTypography,
        content = content,
    )
}
