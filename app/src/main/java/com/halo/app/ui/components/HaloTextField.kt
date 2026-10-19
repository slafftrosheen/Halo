package com.halo.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.icons.HaloIcon

/** Every input declares its privacy and speech semantics at the call site. */
enum class VoiceFieldKind { TEXT, SEARCH, SHORT, NUMBER, CODE, URL_ID, SECRET, STRUCTURED }

/** The shared v1.4 field delegates capture, preview and routed playback to the existing owner. */
@Composable
fun HaloTextField(
    value: String,
    onValueChange: (String) -> Unit,
    kind: VoiceFieldKind,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = kind != VoiceFieldKind.TEXT && kind != VoiceFieldKind.STRUCTURED,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    shape: Shape = RoundedCornerShape(14.dp),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    voiceEnabled: Boolean = enabled,
    guideDictation: Boolean = false,
    dictationRequest: Long = 0,
    onRequestConsumed: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    language: String? = null,
    maxChars: Int = 12000,
    readText: String? = value,
) {
    val secret = kind == VoiceFieldKind.SECRET || visualTransformation is PasswordVisualTransformation ||
        keyboardOptions.keyboardType in setOf(androidx.compose.ui.text.input.KeyboardType.Password,
            androidx.compose.ui.text.input.KeyboardType.NumberPassword)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val input: @Composable (@Composable () -> Unit) -> Unit = { actions ->
            OutlinedTextField(value = value, onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(), enabled = enabled, readOnly = readOnly,
                label = label, placeholder = placeholder, leadingIcon = leadingIcon,
                trailingIcon = {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (secret) HaloIcon(HaloGlyph.SECURITY, uiText("Hidden"))
                        actions()
                        trailingIcon?.invoke()
                    }
                },
                supportingText = supportingText, isError = isError, singleLine = singleLine,
                minLines = minLines, maxLines = maxLines, shape = shape,
                keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
                textStyle = textStyle, colors = colors,
                visualTransformation = if (secret && visualTransformation == VisualTransformation.None)
                    PasswordVisualTransformation() else visualTransformation)
        }
        if (secret || kind == VoiceFieldKind.STRUCTURED) input({})
        // Secrets never instantiate the speech owner or pass their value to a speech callback.
        if (!secret && kind != VoiceFieldKind.STRUCTURED) {
            HaloVoiceTextActions(text = value, onText = onValueChange,
                enabled = voiceEnabled, maxChars = maxChars, language = language,
                allowDictation = !readOnly && kind != VoiceFieldKind.URL_ID,
                allowLongDictation = kind == VoiceFieldKind.TEXT,
                guideDictation = guideDictation,
                dictationRequest = dictationRequest, onRequestConsumed = onRequestConsumed,
                appendDictation = kind == VoiceFieldKind.TEXT,
                applySearchImmediately = kind == VoiceFieldKind.SEARCH,
                previewIsValid = { spoken -> when (kind) {
                    VoiceFieldKind.NUMBER -> spoken.matches(Regex("[+-]?[0-9]+([.,][0-9]+)?"))
                    VoiceFieldKind.CODE -> spoken.matches(Regex("[0-9]+"))
                    else -> true
                } },
                spokenTransform = { spoken -> when (kind) {
                    VoiceFieldKind.NUMBER -> normalizeSpokenDigits(spoken, code = false)
                    VoiceFieldKind.CODE -> normalizeSpokenDigits(spoken, code = true)
                    VoiceFieldKind.SHORT -> spoken.replaceFirstChar { it.titlecase() }
                    else -> spoken
                } },
                readActionLabel = if (kind == VoiceFieldKind.URL_ID) "Spell it" else "Read draft aloud",
                compactRead = true, fieldContent = input,
                readText = when (kind) {
                    VoiceFieldKind.SEARCH -> null // Results are read by their content owner.
                    VoiceFieldKind.CODE -> null // Verification codes stay masked by default.
                    VoiceFieldKind.URL_ID -> readText?.toCharArray()?.joinToString(" ")
                    else -> readText
                })
        }
        if (kind == VoiceFieldKind.STRUCTURED && !secret)
            HaloStructuredInputActions(value, onValueChange, voiceEnabled && !readOnly, maxChars, language)
    }
}

/** Conservative digit conversion: unsupported phrases remain editable, never guessed. */
internal fun normalizeSpokenDigits(spoken: String, code: Boolean): String? {
    val text = spoken.trim()
    if (text.matches(if (code) Regex("[0-9 ]+") else Regex("[+-]?[0-9]+([.,][0-9]+)?")))
        return if (code) text.replace(" ", "") else text.replace(',', '.')
    val digits = listOf(
        listOf("zero", "oh", "nulle", "ноль", "нуль"),
        listOf("one", "viens", "viena", "один", "одна", "jeden"),
        listOf("two", "divi", "divas", "два", "две", "dwa"),
        listOf("three", "trīs", "три", "trzy"),
        listOf("four", "četri", "четыре", "cztery"),
        listOf("five", "pieci", "пять", "pięć"),
        listOf("six", "seši", "шесть", "sześć"),
        listOf("seven", "septiņi", "семь", "siedem"),
        listOf("eight", "astoņi", "восемь", "osiem"),
        listOf("nine", "deviņi", "девять", "dziewięć"))
    val words = text.lowercase().split(Regex("[\\s,.-]+")).filter(String::isNotBlank)
    if (!code) {
        val numbers = mapOf(
            "ten" to 10, "desmit" to 10, "десять" to 10, "dziesięć" to 10,
            "eleven" to 11, "vienpadsmit" to 11, "одиннадцать" to 11, "jedenaście" to 11,
            "twelve" to 12, "divpadsmit" to 12, "двенадцать" to 12, "dwanaście" to 12,
            "thirteen" to 13, "trīspadsmit" to 13, "тринадцать" to 13, "trzynaście" to 13,
            "fourteen" to 14, "četrpadsmit" to 14, "четырнадцать" to 14, "czternaście" to 14,
            "fifteen" to 15, "piecpadsmit" to 15, "пятнадцать" to 15, "piętnaście" to 15,
            "sixteen" to 16, "sešpadsmit" to 16, "шестнадцать" to 16, "szesnaście" to 16,
            "seventeen" to 17, "septiņpadsmit" to 17, "семнадцать" to 17, "siedemnaście" to 17,
            "eighteen" to 18, "astoņpadsmit" to 18, "восемнадцать" to 18, "osiemnaście" to 18,
            "nineteen" to 19, "deviņpadsmit" to 19, "девятнадцать" to 19, "dziewiętnaście" to 19)
        if (words.size == 1) numbers[words.single()]?.let { return it.toString() }
    }
    val converted = words.map { word -> digits.indexOfFirst { word in it }.takeIf { it >= 0 } ?: return null }
    if (converted.isEmpty() || (!code && converted.size != 1)) return null
    return converted.joinToString("")
}
