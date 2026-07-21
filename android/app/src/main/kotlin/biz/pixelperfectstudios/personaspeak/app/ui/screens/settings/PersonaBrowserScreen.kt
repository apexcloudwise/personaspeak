package biz.pixelperfectstudios.personaspeak.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.app.ui.components.PersonaSpeakTopBar
import biz.pixelperfectstudios.personaspeak.app.ui.data.PersonaDisplay
import biz.pixelperfectstudios.personaspeak.app.ui.data.SamplePersonas
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PillShape

/**
 * Two-column bento grid of installed personas. Selecting a card navigates to
 * the persona-detail route with that persona's id. The default persona wears
 * a teal "Default" pill badge (Jeeves in the sample set).
 */
@Composable
fun PersonaBrowserScreen(
    onPersonaSelected: (personaId: String) -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PersonaSpeakTopBar(
                leading = { BrowserCloseButton(onClose) },
                trailing = {
                    IconButton(onClick = { /* search — out of scope */ }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "Select Persona",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Choose your AI's conversational archetype to transform your writing style with precision and flair.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))

                // Two-column bento grid, laid out manually so we don't nest a
                // lazy grid inside the vertical scroll.
                SamplePersonas.all.chunked(2).forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowItems.forEach { persona ->
                            PersonaBentoCard(
                                persona = persona,
                                onClick = { onPersonaSelected(persona.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun PersonaBentoCard(
    persona: PersonaDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
        modifier = modifier
            .clickable(onClick = onClick)
            .height(180.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Teal glow blob, top-right — matches the mockup's accent.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = CircleShape,
                    ),
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = persona.emoji,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(6.dp))
                if (persona.isDefault) {
                    DefaultBadge()
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = persona.persona.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = persona.tagline,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DefaultBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        shape = PillShape,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.40f),
        ),
    ) {
        Text(
            text = "Default",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun BrowserCloseButton(onClose: () -> Unit) {
    Text(
        text = "✕",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .clickable(onClick = onClose)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}
