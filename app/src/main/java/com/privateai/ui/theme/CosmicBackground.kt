package com.privateai.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TWO_PI = 6.2831855f

private class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val depth: Float,
    val phase: Float
)

/** Live deep-space scene: parallax-drifting stars, breathing nebulae and passing shooting stars. */
@Composable
fun CosmicBackground(modifier: Modifier = Modifier) {
    val stars = remember {
        val random = Random(42)
        List(320) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 0.35f + random.nextFloat() * 0.75f,
                alpha = 0.35f + random.nextFloat() * 0.65f,
                depth = 0.2f + random.nextFloat() * 0.8f,
                phase = random.nextFloat() * TWO_PI
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "cosmos")
    // 0..1 loop; stars drift one screen-height per loop, scaled by depth for parallax.
    val drift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart), label = "drift"
    )
    val pulse by transition.animateFloat(
        0f, TWO_PI, infiniteRepeatable(tween(7_000, easing = LinearEasing), RepeatMode.Restart), label = "pulse"
    )
    val shoot by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart), label = "shoot"
    )

    Canvas(modifier) {
        drawRect(
            Brush.verticalGradient(
                listOf(CosmicColors.Void, CosmicColors.Void, CosmicColors.DeepIndigo.copy(alpha = 0.6f), CosmicColors.Void)
            )
        )
        val breathe = 0.5f + 0.5f * sin(pulse)
        val orbit = size.minDimension * 0.06f
        nebula(
            Offset(size.width * 0.85f + orbit * cos(pulse), size.height * 0.18f + orbit * sin(pulse)),
            size.minDimension * (0.8f + 0.2f * breathe), CosmicColors.Nebula, 0.10f + 0.08f * breathe
        )
        nebula(
            Offset(size.width * 0.1f - orbit * sin(pulse), size.height * 0.75f + orbit * cos(pulse)),
            size.minDimension * (0.7f + 0.2f * (1f - breathe)), CosmicColors.Glow, 0.04f + 0.05f * (1f - breathe)
        )
        stars.forEach { s ->
            val y = (s.y + drift * s.depth) % 1f
            val x = (s.x + 0.02f * sin(pulse + s.phase) * s.depth + 1f) % 1f
            val twinkle = 0.55f + 0.45f * sin(pulse * 3f + s.phase)
            drawCircle(
                Color.White.copy(alpha = (s.alpha * twinkle).coerceIn(0f, 1f)),
                s.radius * density * (0.6f + 0.4f * s.depth),
                Offset(x * size.width, y * size.height)
            )
        }
        shootingStar(shoot, 0.00f, 0.10f, 0.35f)
        shootingStar(shoot, 0.45f, 0.55f, 0.15f)
    }
}

// A streak crossing the screen during a short window of the loop starting at `start`.
private fun DrawScope.shootingStar(t: Float, start: Float, window: Float, yFrac: Float) {
    val p = (t - start) / window
    if (p !in 0f..1f) return
    val head = Offset(size.width * (1.1f - 1.3f * p), size.height * (yFrac + 0.25f * p))
    val tail = Offset(head.x + size.width * 0.18f, head.y - size.height * 0.05f)
    val fade = sin(p * Math.PI.toFloat())
    drawLine(
        brush = Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.9f * fade), Color.Transparent), head, tail
        ),
        start = head,
        end = tail,
        strokeWidth = 2.2f * density,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.nebula(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center, radius),
        radius = radius,
        center = center
    )
}
