package com.halo.app.y2027

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * halo 2027 · Trust you can see (board 50). Every answer carries exactly one origin chip.
 * Mint = on this phone · Blue = used online (says what for) · dashed = private chat.
 * Tapping the chip shows one lilac sentence: what was sent, to whom.
 */
sealed interface AnswerOrigin {
    data object OnThisPhone : AnswerOrigin
    data class Online(val forWhat: String, val sentSummary: String) : AnswerOrigin // "weather", "Only “Riga, Saturday 10:00”"
    data object Private : AnswerOrigin
}

private val Mint = Color(0xFF0CF2BA)
private val Blue = Color(0xFFB6DFE2)
private val Cream = Color(0xFFFAFAF4)

@Composable
fun OriginChip(origin: AnswerOrigin, modifier: Modifier = Modifier) {
    val (label, color) = when (origin) {
        AnswerOrigin.OnThisPhone -> "On this phone" to Mint
        is AnswerOrigin.Online -> "Used online · ${origin.forWhat}" to Blue
        AnswerOrigin.Private -> "Private · nothing kept" to Cream
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (origin is AnswerOrigin.Private) Color.Transparent else color.copy(alpha = .13f),
        border = if (origin is AnswerOrigin.Private) BorderStroke(1.5.dp, Cream.copy(alpha = .4f)) else null,
        modifier = modifier.heightIn(min = 24.dp).semantics { contentDescription = label },
    ) {
        Row(Modifier.padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(color, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(label, color = color, fontSize = 11.5.sp)
        }
    }
}

/** Pause halo: nothing listens, learns or runs. Alarms/timers already set still ring. */
data class PauseState(val pausedUntilMillis: Long?) {
    val isPaused get() = pausedUntilMillis != null && pausedUntilMillis > System.currentTimeMillis()
    val ringCaption get() = if (isPaused) "Paused" else "Talk"
}

/** Privacy at a glance — plain weekly numbers. */
data class PrivacyWeek(val onPhone: Int, val online: Int, val shared: Int)
