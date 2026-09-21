package com.halo.app.ui.presence

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun HaloWorkspaceMark(kind: String, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.size(28.dp)) {
        val u = size.minDimension / 28f
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(ink, Offset(x*u,y*u), Offset(x2*u,y2*u), 1.7f*u, StrokeCap.Round)
        fun cell(x: Float, y: Float, w: Float, h: Float) = drawRoundRect(ink, Offset(x*u,y*u), Size(w*u,h*u), CornerRadius(2*u), style = Stroke(1.7f*u))
        when(kind) {
            "glasses", "devices" -> { cell(2f,10f,9f,8f); cell(17f,10f,9f,8f); line(11f,12f,17f,12f); line(2f,10f,4f,6f); line(26f,10f,24f,6f) }
            "robot" -> { cell(6f,7f,16f,14f); cell(2f,11f,3f,10f); cell(23f,11f,3f,10f); line(10f,11f,18f,11f); line(14f,7f,14f,3f) }
            "activity" -> { line(5f,4f,5f,24f); cell(10f,4f,12f,5f); cell(10f,13f,8f,4f); cell(10f,21f,12f,3f) }
            "notebook" -> { cell(5f,3f,18f,22f); line(9f,3f,9f,25f); line(13f,9f,19f,9f); line(13f,14f,19f,14f) }
            else -> { line(3f,7f,25f,7f); line(3f,14f,25f,14f); line(3f,21f,25f,21f); cell(7f,4f,4f,6f); cell(17f,11f,4f,6f); cell(10f,18f,4f,6f) }
        }
    }
}
