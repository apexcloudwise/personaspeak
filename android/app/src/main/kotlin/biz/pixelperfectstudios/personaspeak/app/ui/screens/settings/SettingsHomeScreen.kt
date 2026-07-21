package biz.pixelperfectstudios.personaspeak.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import biz.pixelperfectstudios.personaspeak.app.ui.components.GlassCard
import biz.pixelperfectstudios.personaspeak.app.ui.components.PersonaSpeakTopBar
import biz.pixelperfectstudios.personaspeak.app.ui.components.SectionLabel
import biz.pixelperfectstudios.personaspeak.app.ui.components.SettingsListRow
import biz.pixelperfectstudios.personaspeak.app.ui.data.SamplePersonas
import biz.pixelperfectstudios.personaspeak.app.ui.data.SampleProviders

/**
 * Settings root. Per the task scope, only the four rows that map to routes this
 * screen owns are surfaced (Personas, AI provider, Rewrite behaviour, Privacy).
 * The mockup's other rows — default mood, API key, usage, languages, glide
 * typing, autocorrect, theme, height, read-the-source — belong to the keyboard
 * fork (no routes exist for them yet) and are intentionally omitted here.
 */
@Composable
fun SettingsHomeScreen(
    onNavigatePersonas: () -> Unit,
    onNavigateAiProviders: () -> Unit,
    onNavigateRewriteBehaviour: () -> Unit,
    onNavigatePrivacy: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PersonaSpeakTopBar(
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
                    .padding(bottom = 32.dp),
            ) {
                Spacer(Modifier.height(16.dp))

                DefaultKeyboardBanner()

                SectionLabel("Characters")
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column {
                        SettingsListRow(
                            title = "Personas",
                            subtitle = "${SamplePersonas.all.size} installed",
                            glyph = "🎭",
                            onClick = onNavigatePersonas,
                        )
                        Divider()
                        SettingsListRow(
                            title = "Rewrite behaviour",
                            subtitle = "Ask before replacing",
                            glyph = "✍️",
                            onClick = onNavigateRewriteBehaviour,
                        )
                    }
                }

                SectionLabel("The brain")
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingsListRow(
                        title = "AI provider",
                        subtitle = SampleProviders.active?.name ?: "None",
                        glyph = "🧠",
                        onClick = onNavigateAiProviders,
                    )
                }

                SectionLabel("Privacy")
                GlassCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    SettingsListRow(
                        title = "What we store",
                        subtitle = "Read the honest answer",
                        glyph = "🔒",
                        onClick = onNavigatePrivacy,
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultKeyboardBanner() {
    // Mockup reads "PersonaSpeak is your default keyboard" as a confirmed
    // state. We can't verify default-keyboard status from this screen yet, so
    // the banner stays a true brand line rather than an unverifiable claim.
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "✓",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "PersonaSpeak — your persona-driven keyboard.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Divider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
    )
}
