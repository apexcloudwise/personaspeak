package biz.pixelperfectstudios.personaspeak.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.app.ui.components.PersonaSpeakTopBar
import biz.pixelperfectstudios.personaspeak.app.ui.components.SectionLabel
import biz.pixelperfectstudios.personaspeak.app.ui.data.PersonaDisplay
import biz.pixelperfectstudios.personaspeak.app.ui.data.SamplePersonas
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PillShape

/**
 * Per-persona detail. Reads the [personaId] nav arg, looks it up in the
 * sample set, and renders hero + speech patterns + vocabulary pills + sample
 * lines. Unknown ids render a small not-found panel rather than crashing.
 */
@Composable
fun PersonaDetailScreen(
    personaId: String?,
    onClose: () -> Unit,
) {
    val persona = personaId?.let { SamplePersonas.byId(it) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PersonaSpeakTopBar(
                leading = {
                    Text(
                        text = "✕",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .clickable(onClick = onClose)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                },
            )

            if (persona == null) {
                NotFoundState(personaId)
                return@Column
            }

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                PersonaHero(persona)
                SpeechPatternsSection(persona)
                VocabularySection(persona)
                SampleLinesSection(persona)
                Spacer(Modifier.height(80.dp))
            }

            DetailFooter()
        }
    }
}

@Composable
private fun PersonaHero(persona: PersonaDisplay) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(112.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Primary-tinted glow behind the emoji.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        shape = CircleShape,
                    ),
            )
            Text(
                text = persona.emoji,
                style = MaterialTheme.typography.displayMedium,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = persona.persona.name,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = persona.persona.context,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(280.dp)
                .padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun SpeechPatternsSection(persona: PersonaDisplay) {
    SectionLabel("Speech patterns")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column {
            persona.persona.speechPatterns.forEachIndexed { index, pattern ->
                if (index > 0) {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
                SpeechPatternRow(pattern)
            }
        }
    }
}

@Composable
private fun SpeechPatternRow(pattern: String) {
    // Patterns in the sample data are formatted "Title — description". Split
    // gracefully if the em-dash separator isn't there.
    val (title, desc) = pattern.split(" — ", limit = 2)
        .let { parts ->
            if (parts.size == 2) parts[0] to parts[1] else pattern to ""
        }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.size(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "▸", color = MaterialTheme.colorScheme.primary)
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            if (desc.isNotEmpty()) {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VocabularySection(persona: PersonaDisplay) {
    if (persona.persona.vocabulary.isEmpty()) return
    SectionLabel("Vocabulary")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.width(16.dp))
        persona.persona.vocabulary.forEach { word ->
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = PillShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            ) {
                Text(
                    text = word,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
    }
}

@Composable
private fun SampleLinesSection(persona: PersonaDisplay) {
    if (persona.persona.sampleLines.isEmpty()) return
    SectionLabel("Sample lines")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            persona.persona.sampleLines.forEach { line ->
                Row(modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontStyle = FontStyle.Italic,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailFooter() {
    val tealBrush = Brush.horizontalGradient(
        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondaryContainer),
    )
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { /* try persona — out of scope */ },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "Try this persona",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            OutlinedButton(
                onClick = { /* set default — out of scope */ },
                enabled = false,
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Set as default")
            }
        }
    }
}

@Composable
private fun NotFoundState(personaId: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🤷",
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Persona not found",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (personaId != null) "No persona matches \"$personaId\"." else "No persona selected.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
