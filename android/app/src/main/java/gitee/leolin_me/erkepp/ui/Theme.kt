package gitee.leolin_me.erkepp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A5FB4),
    onPrimary = Color.White,
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFF7F8FA),
    onSurface = Color(0xFF1B1C1E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1B33),
    background = Color(0xFF101114),
    surface = Color(0xFF101114),
    onSurface = Color(0xFFE3E4E6),
)

/**
 * [darkTheme] 默认跟随系统；只有 debug 的预览页会传值，用来在同一台机器上看两套主题。
 */
@Composable
fun ErkeppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
