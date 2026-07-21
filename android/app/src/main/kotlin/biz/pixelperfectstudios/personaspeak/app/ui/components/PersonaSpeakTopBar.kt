package biz.pixelperfectstudios.personaspeak.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Sticky brand bar that sits across the top of every settings screen.
 * Left slot: the ⌘ glyph + "PersonaSpeak" wordmark in primary teal (matches
 * the mockups' command-key brand treatment). Optional [leading] and [trailing]
 * slots absorb per-screen affordances — close (X) on detail/privacy, search on
 * home/browser, help on AI providers.
 */
@Composable
fun PersonaSpeakTopBar(
    modifier: Modifier = Modifier,
    showWordmark: Boolean = true,
    leading: @Composable (RowScope.() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) leading()
                if (showWordmark) {
                    Text(
                        text = "⌘",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(end = 6.dp, start = 4.dp),
                    )
                    Text(
                        text = "PersonaSpeak",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
            } else {
                androidx.compose.foundation.layout.Spacer(Modifier.size(48.dp))
            }
        }
    }
}

/** Close affordance for sub-screens (detail, privacy). Uses a text glyph so we
 * don't pull material-icons-extended for a single icon. */
@Composable
fun TopBarCloseAction(onClick: () -> Unit) {
    Text(
        text = "✕",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
