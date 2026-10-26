package com.halo.app.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.theme.HaloBrandFonts
import kotlin.math.roundToInt

@Composable
internal fun screenBackgroundColor(appearance: ScreenAppearance): Color = when (appearance.background) {
    ScreenBackground.PLAIN -> MaterialTheme.colorScheme.background
    ScreenBackground.MINT -> androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primaryContainer, .22f)
    ScreenBackground.WARM -> androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.tertiaryContainer, .22f)
    ScreenBackground.COOL -> androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.secondaryContainer, .22f)
}

@Composable
internal fun screenTextStyle(base: TextStyle, appearance: ScreenAppearance, percent: Int): TextStyle {
    val cyrillic = LocalConfiguration.current.locales[0].language in setOf("ru", "uk", "bg")
    val family = when {
        appearance.font == ScreenFont.APP -> base.fontFamily
        appearance.font == ScreenFont.SYSTEM -> FontFamily.Default
        cyrillic -> HaloBrandFonts.cyrillic
        appearance.font == ScreenFont.SORA -> HaloBrandFonts.display
        else -> HaloBrandFonts.reading
    }
    val scale = percent.coerceIn(100, 200) / 100f
    return base.copy(fontFamily = family, fontSize = base.fontSize * scale, lineHeight = base.lineHeight * scale)
}

/** Native sheets keep the surrounding app's text scaling, including ambient scale. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenNativeSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        CompositionLocalProvider(LocalDensity provides density) { content() }
    }
}

@Composable
internal fun ScreenSheetBody(title: String, onDone: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(uiText("Done")) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScreenAppearanceControls(value: ScreenAppearance, onChange: (ScreenAppearance) -> Unit) {
    Text(stringResource(R.string.interface4_dashboard_style), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.interface4_dashboard_clear to ScreenAppearance(ScreenFont.APP, ScreenBackground.PLAIN, false),
            R.string.interface4_tone_paper to ScreenAppearance(ScreenFont.ATKINSON, ScreenBackground.WARM, false),
            R.string.interface4_dashboard_glass to ScreenAppearance(ScreenFont.APP, ScreenBackground.MINT, true),
            R.string.interface4_dashboard_focus to ScreenAppearance(ScreenFont.SORA, ScreenBackground.COOL, true),
        ).forEach { (label, appearance) ->
            FilterChip(value == appearance, { onChange(appearance) }, label = { Text(stringResource(label)) },
                modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    Text(stringResource(R.string.screen_font), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenFont.entries.forEach { font ->
            FilterChip(value.font == font, { onChange(value.copy(font = font)) }, label = { Text(when (font) {
                ScreenFont.APP -> stringResource(R.string.screen_app_font)
                ScreenFont.SYSTEM -> stringResource(R.string.screen_system_font)
                ScreenFont.SORA -> "Sora"
                ScreenFont.ATKINSON -> "Atkinson"
            }) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    Text(stringResource(R.string.screen_bundled_fonts), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.screen_background), style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenBackground.entries.forEach { background ->
            FilterChip(value.background == background, { onChange(value.copy(background = background)) }, label = {
                Text(stringResource(when (background) {
                    ScreenBackground.PLAIN -> R.string.screen_background_plain
                    ScreenBackground.MINT -> R.string.screen_background_mint
                    ScreenBackground.WARM -> R.string.screen_background_warm
                    ScreenBackground.COOL -> R.string.screen_background_cool
                }))
            }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(stringResource(R.string.screen_translucent), Modifier.weight(1f))
        Switch(value.translucent, { onChange(value.copy(translucent = it)) })
    }
    Text(stringResource(R.string.screen_preview_not_saved), style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun ScreenTypographyControls(value: ScreenCardTypography, clock: Boolean, onChange: (ScreenCardTypography) -> Unit) {
    @Composable fun emphasis(label: String, current: Int, max: Int, update: (Int) -> Unit) {
        Text(stringResource(R.string.screen_emphasis_percent, label, current), style = MaterialTheme.typography.labelLarge)
        Slider(current.toFloat(), { update((it / 25).roundToInt() * 25) }, valueRange = 100f..max.toFloat(),
            steps = (max - 100) / 25 - 1, modifier = Modifier.heightIn(min = 48.dp))
    }
    emphasis(stringResource(R.string.screen_title_emphasis), value.titlePercent, 150) { onChange(value.copy(titlePercent = it)) }
    emphasis(stringResource(R.string.screen_value_emphasis), value.valuePercent, 150) { onChange(value.copy(valuePercent = it)) }
    if (clock) emphasis(stringResource(R.string.screen_clock_emphasis), value.clockPercent, 200) { onChange(value.copy(clockPercent = it)) }
}
