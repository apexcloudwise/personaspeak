package biz.pixelperfectstudios.personaspeak.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Uppercase tracking-wider section label used to head grouped sections across
 * the settings screens. Fills width so the heading aligns to the section's
 * card edge when padded by the same horizontal inset as the content.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * A reusable glass-card container: surface at the design-system container tone
 * with a subtle outline, rounded at the medium radius. Screens put their own
 * content inside.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.padding(0.dp)) {
            content()
        }
    }
}
