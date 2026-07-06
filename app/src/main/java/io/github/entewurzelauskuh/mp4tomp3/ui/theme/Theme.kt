package io.github.entewurzelauskuh.mp4tomp3.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(primary = Blue80, secondary = BlueGrey80, tertiary = Teal80)
private val LightColors = lightColorScheme(primary = Blue40, secondary = BlueGrey40, tertiary = Teal40)

/**
 * App-wide Material 3 theme. Follows the system dark mode (N5) and uses Android 12+ dynamic
 * (wallpaper) colours when available — which they always are here, since `minSdk` is 31.
 */
@Composable
fun Mp4ToMp3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors

        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
