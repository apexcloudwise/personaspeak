package biz.pixelperfectstudios.personaspeak.keyboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The floating card above the strip — one parameterized composable covering
 * every mockup in set 2.4–2.9. It renders whichever variant [state] asks for:
 *
 * - [TransformState.Loading] → shimmer skeleton + in-voice caption + cancel.
 * - [TransformState.Success] → the rewritten text + Use this / Again / dismiss.
 * - [TransformState.Error]   → the in-voice message inline, not modal.
 *
 * Idle is not rendered (the orchestrator leaves the slot empty).
 *
 * The card floats *inside* the IME's own window. IME windows cannot draw over
 * the host app (see docs/superpowers/specs/2026-07-21-prototype-gap-analysis.md),
 * so this is an inline overlay, not a system-window floater.
 */
@Composable
fun ResultCard(
    state: TransformState,
    persona: KeyboardPersona,
    mood: Mood,
    onCommit: (String) -> Unit,
    onAgain: () -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is TransformState.Loading -> LoadingCard(persona, mood, onCancel, modifier)
        is TransformState.Success -> SuccessCard(state.text, persona, mood, { onCommit(state.text) }, onAgain, onDismiss, modifier)
        is TransformState.Error -> ErrorCard(state.message, onDismiss, modifier)
        TransformState.Idle -> Unit
    }
}

@Composable
private fun CardShell(modifier: Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) { content() }
}

@Composable
private fun CardHeader(persona: KeyboardPersona, mood: Mood) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${persona.emoji} ${persona.displayName.uppercase()}",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "· ${mood.label.uppercase()}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingCard(
    persona: KeyboardPersona,
    mood: Mood,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    CardShell(modifier) {
        Column {
            CardHeader(persona, mood)
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ShimmerBar(widthFraction = 1f)
                ShimmerBar(widthFraction = 0.92f)
                ShimmerBar(widthFraction = 0.78f)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Composing something regrettable…",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(onClick = onCancel),
                )
            }
        }
    }
}

@Composable
private fun ShimmerBar(widthFraction: Float) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerTranslate",
    )
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHigh,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
        MaterialTheme.colorScheme.surfaceContainerHigh,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(14.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = shimmerColors,
                    start = Offset(translate * -300f, 0f),
                    end = Offset(translate * 300f + 300f, 0f),
                ),
                shape = RoundedCornerShape(4.dp),
            ),
    )
}

@Composable
private fun SuccessCard(
    text: String,
    persona: KeyboardPersona,
    mood: Mood,
    onCommit: () -> Unit,
    onAgain: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    CardShell(modifier) {
        Column {
            CardHeader(persona, mood)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(14.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Use this — teal gradient, flex-1, commits the rewrite.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                ),
                            ),
                            shape = MaterialTheme.shapes.small,
                        )
                        .clickable(onClick = onCommit),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓  Use this",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
                // ↻ Again — re-run the transform with the same persona/mood/draft.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.small,
                        )
                        .clickable(onClick = onAgain),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "↻", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                // ✕ dismiss — keep the draft, drop the card.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.small,
                        )
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit, modifier: Modifier) {
    CardShell(modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                text = "Dismiss",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(onClick = onDismiss),
            )
        }
    }
}
