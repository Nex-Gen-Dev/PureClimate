package com.midea.pureclimate.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.midea.pureclimate.data.AppTheme

data class Palette(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceBorder: Color,
    val button: Color,
    val buttonPressed: Color,
    val buttonBorder: Color,
    val text: Color,
    val textMuted: Color,
    val lcd: Color,
    val lcdBorder: Color,
    val lcdText: Color,
    val lcdTextDim: Color,
    val accent: Color,
    val onAccent: Color,
    val powerOn: Color,
    val powerOff: Color,
    val ok: Color,
    val bad: Color,
)

val ClassicLcd = Palette(
    isDark = true,
    background = Color(0xFF0B0E14),
    surface = Color(0xFF141924),
    surfaceBorder = Color(0xFF232B38),
    button = Color(0xFF232C3A),
    buttonPressed = Color(0xFF2E3A4C),
    buttonBorder = Color(0xFF323D4E),
    text = Color(0xFFDCE3EC),
    textMuted = Color(0xFF7E8A9B),
    lcd = Color(0xFF98AF9F),
    lcdBorder = Color(0xFF5E7364),
    lcdText = Color(0xFF16231A),
    lcdTextDim = Color(0x4D16231A),
    accent = Color(0xFF98AF9F),
    onAccent = Color(0xFF0B0E14),
    powerOn = Color(0xFFE5484D),
    powerOff = Color(0xFF2A3341),
    ok = Color(0xFF3DD68C),
    bad = Color(0xFFE5484D),
)

val DarkMinimal = Palette(
    isDark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceBorder = Color(0xFF1C1C1C),
    button = Color(0xFF111111),
    buttonPressed = Color(0xFF1C2B29),
    buttonBorder = Color(0xFF242424),
    text = Color(0xFFEDEDED),
    textMuted = Color(0xFF777777),
    lcd = Color(0xFF000000),
    lcdBorder = Color(0xFF14524A),
    lcdText = Color(0xFF2DE1C2),
    lcdTextDim = Color(0x332DE1C2),
    accent = Color(0xFF2DE1C2),
    onAccent = Color(0xFF000000),
    powerOn = Color(0xFFFF4D5E),
    powerOff = Color(0xFF161616),
    ok = Color(0xFF2DE1C2),
    bad = Color(0xFFFF4D5E),
)

val LightClean = Palette(
    isDark = false,
    background = Color(0xFFE9EDF2),
    surface = Color(0xFFFFFFFF),
    surfaceBorder = Color(0xFFD5DCE5),
    button = Color(0xFFFFFFFF),
    buttonPressed = Color(0xFFE3EAF3),
    buttonBorder = Color(0xFFCDD5E0),
    text = Color(0xFF17202C),
    textMuted = Color(0xFF66748A),
    lcd = Color(0xFFDDE6EE),
    lcdBorder = Color(0xFFB9C7D5),
    lcdText = Color(0xFF17202C),
    lcdTextDim = Color(0x2617202C),
    accent = Color(0xFF2563EB),
    onAccent = Color(0xFFFFFFFF),
    powerOn = Color(0xFFDC2626),
    powerOff = Color(0xFFF1F4F8),
    ok = Color(0xFF16A34A),
    bad = Color(0xFFDC2626),
)

fun paletteFor(theme: AppTheme): Palette = when (theme) {
    AppTheme.CLASSIC_LCD -> ClassicLcd
    AppTheme.DARK_MINIMAL -> DarkMinimal
    AppTheme.LIGHT_CLEAN -> LightClean
}

val LocalPalette = staticCompositionLocalOf { ClassicLcd }

@Composable
fun PureClimateTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val p = paletteFor(theme)
    val scheme = if (p.isDark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.background,
            surface = p.surface, onSurface = p.text, onBackground = p.text,
            surfaceVariant = p.button, onSurfaceVariant = p.textMuted, outline = p.buttonBorder,
            error = p.bad,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = p.onAccent, background = p.background,
            surface = p.surface, onSurface = p.text, onBackground = p.text,
            surfaceVariant = p.button, onSurfaceVariant = p.textMuted, outline = p.buttonBorder,
            error = p.bad,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
