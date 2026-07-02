package com.superdash.kiosk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superdash.voice.pipeline.VoiceState

private val Amber = Color(0xFFFFB300)
private val Green = Color(0xFF2E7D32)
private val Blue = Color(0xFF1565C0)
private val Red = Color(0xFFC62828)

private enum class DotAnimation { Slow, Fast, Morph, Breathing }

/** Bottom-anchored Nest-Hub-style overlay surfacing the active voice pipeline state.
 *  Tap cancels the active run; otherwise an accidental wake hangs for 60 s. */
@Composable
fun VoiceOverlay(
    state: VoiceState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = state !is VoiceState.Idle
    val accent =
        when (state) {
            is VoiceState.Idle -> Color.Transparent
            is VoiceState.WakeFired -> Amber
            is VoiceState.Recording -> Green
            is VoiceState.Processing -> Blue
            is VoiceState.ActionComplete -> Green
            is VoiceState.Speaking -> Blue
            is VoiceState.Failed -> Red
        }
    val animation =
        when (state) {
            is VoiceState.Idle -> null
            is VoiceState.WakeFired -> DotAnimation.Slow
            is VoiceState.Recording -> DotAnimation.Fast
            is VoiceState.Processing -> DotAnimation.Morph
            is VoiceState.ActionComplete -> null
            is VoiceState.Speaking -> DotAnimation.Breathing
            is VoiceState.Failed -> null
        }
    val topLine =
        when (state) {
            is VoiceState.Idle -> ""
            is VoiceState.WakeFired -> "Wake word"
            is VoiceState.Recording -> "Listening…"
            is VoiceState.Processing -> "Thinking…"
            is VoiceState.ActionComplete -> "Done"
            is VoiceState.Speaking -> "Speaking…"
            is VoiceState.Failed -> "Voice failed"
        }
    val bottomLine =
        when (state) {
            is VoiceState.Recording -> state.partialTranscript
            is VoiceState.Processing -> state.transcript
            is VoiceState.ActionComplete -> state.transcript
            is VoiceState.Speaking -> state.transcript
            is VoiceState.Failed -> state.reason
            else -> null
        }

    BackHandler(enabled = visible) {
        onCancel()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(animationSpec = tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(260)) { it } + fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, accent.copy(alpha = 0.55f)),
                                    ),
                            ),
                )
                Card(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 56.dp)
                            .widthIn(min = 320.dp, max = 640.dp)
                            .clickable { onCancel() },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BouncingDots(accent = accent, animation = animation)
                        Spacer(Modifier.width(24.dp))
                        Column {
                            Text(
                                text = topLine,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!bottomLine.isNullOrBlank()) {
                                Text(
                                    text = bottomLine,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 15.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BouncingDots(accent: Color, animation: DotAnimation?) {
    if (animation == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) {
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(accent),
                )
            }
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "voice-dots")
    val period =
        when (animation) {
            DotAnimation.Slow -> 700
            DotAnimation.Fast -> 380
            DotAnimation.Morph -> 520
            DotAnimation.Breathing -> 1_400
        }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val phase = (index * period) / 3
            val offset by transition.animateFloat(
                initialValue = 0f,
                targetValue = -10f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = period, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(phase),
                    ),
                label = "dot-$index",
            )
            Box(
                modifier =
                    Modifier
                        .offset(y = offset.dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(accent),
            )
        }
    }
}
