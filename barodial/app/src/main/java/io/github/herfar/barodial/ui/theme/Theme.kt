package io.github.herfar.barodial.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.herfar.barodial.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C51),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8AF7CF),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4C6358),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE9DA),
    onSecondaryContainer = Color(0xFF092016),
    tertiary = Color(0xFF3F6375),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC2E8FD),
    onTertiaryContainer = Color(0xFF001F2A),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF9),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF9),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDBE5DE),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFBFC9C2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F8F4),
    surfaceContainer = Color(0xFFEFF2EE),
    surfaceContainerHigh = Color(0xFFE9ECE8),
    surfaceContainerHighest = Color(0xFFE3E6E2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6DDBB4),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513C),
    onPrimaryContainer = Color(0xFF8AF7CF),
    secondary = Color(0xFFB3CCBF),
    onSecondary = Color(0xFF1F352B),
    secondaryContainer = Color(0xFF354B41),
    onSecondaryContainer = Color(0xFFCEE9DA),
    tertiary = Color(0xFFA6CCE0),
    onTertiary = Color(0xFF0A3445),
    tertiaryContainer = Color(0xFF264B5C),
    onTertiaryContainer = Color(0xFFC2E8FD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111412),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFBFC9C2),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF404944),
    surfaceContainerLowest = Color(0xFF0C0F0D),
    surfaceContainerLow = Color(0xFF191C1A),
    surfaceContainer = Color(0xFF1D201E),
    surfaceContainerHigh = Color(0xFF272B28),
    surfaceContainerHighest = Color(0xFF323633),
)

/** 다이내믹 컬러와 무관하게 의미가 고정된 색 (통화 버튼 초록, 부재중 빨강 등). */
@Immutable
data class CallColors(
    val call: Color,
    val onCall: Color,
    val missed: Color,
    val incoming: Color,
    val outgoing: Color,
    val favorite: Color,
)

private val LightCallColors = CallColors(
    call = Color(0xFF1B9A55),
    onCall = Color.White,
    missed = Color(0xFFD32F2F),
    incoming = Color(0xFF1B7FBF),
    outgoing = Color(0xFF1B9A55),
    favorite = Color(0xFFF2A900),
)

private val DarkCallColors = CallColors(
    call = Color(0xFF2EAF68),
    onCall = Color.White,
    missed = Color(0xFFFF8A80),
    incoming = Color(0xFF7CC4F2),
    outgoing = Color(0xFF7FD6A2),
    favorite = Color(0xFFFFC83D),
)

val LocalCallColors = staticCompositionLocalOf { LightCallColors }

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** 키패드 숫자 등 큰 글자 */
val DialDigitStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Normal, lineHeight = 34.sp)

@Composable
fun BaroDialTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalCallColors provides if (dark) DarkCallColors else LightCallColors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}
