package biz.pixelperfectstudios.personaspeak.keyboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PillShape
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Secondary

/**
 * The resting strip (mockup set 2.1): persona chip + mood chip on the left,
 * circular transform FAB on the right. 40dp tall, docks above the draft.
 *
 * The FAB is the only transform affordance; tapping it with a non-empty draft
 * triggers [onTransform] (the orchestrator guards blank input). While a
 * transform is in flight the FAB shows a spinner instead of the glyph.
 */
@Composable
fun PersonaStrip(
    persona: KeyboardPersona,
    mood: Mood,
    loading: Boolean,
    onPickPersona: () -> Unit,
    onPickMood: () -> Unit,
    onTransform: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PersonaChip(persona = persona, onClick = onPickPersona)
        MoodChip(mood = mood, onClick = onPickMood)
        Spacer(Modifier.weight(1f))
        TransformFab(loading = loading, onClick = onTransform)
    }
}

@Composable
private fun PersonaChip(persona: KeyboardPersona, onClick: () -> Unit) {
    // Teal left-edge accent, as the mockup renders it (border-l-2 border-primary).
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(width = 1.dp, color = Color.Transparent),
        modifier = Modifier
            .height(32.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = PillShape,
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = persona.emoji, fontSize = 14.sp)
            Text(
                text = persona.displayName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "▾",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MoodChip(mood: Mood, onClick: () -> Unit) {
    Surface(
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        modifier = Modifier
            .height(32.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = mood.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "▾",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransformFab(loading: Boolean, onClick: () -> Unit) {
    // The teal glow from the mockup (`box-shadow: 0 0 12px rgba(79,219,200,0.2)`)
    // is approximated with a soft shadow; real blur isn't available in an IME
    // window (see 2026-07-21-prototype-gap-analysis.md).
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary,
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        Secondary,
                    ),
                ),
                shape = CircleShape,
            )
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = "✦",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
