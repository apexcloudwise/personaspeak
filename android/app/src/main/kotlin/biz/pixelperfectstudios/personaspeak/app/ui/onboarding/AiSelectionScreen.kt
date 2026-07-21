package biz.pixelperfectstudios.personaspeak.app.ui.onboarding

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.app.ui.components.PillBadge
import biz.pixelperfectstudios.personaspeak.app.ui.components.PrimaryButton
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Background
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnPrimaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurface
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurfaceVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OutlineVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Primary
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PrimaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainerHigh
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainerLow

private data class ProviderOption(
    val id: String,
    val name: String,
    val description: String,
    val recommended: Boolean = false,
    val enabled: Boolean = true,
    /** Small tag chip ("Free" / "Soon"); null hides the chip. */
    val tag: String? = null,
    val tagTinted: Boolean = true,
)

// Hardcoded display list. No provider registry exists yet (core-providers
// ships the interface, not a catalogue), so the five launch options live here
// until the settings screen grows a real registry-backed picker.
private val PROVIDERS = listOf(
    ProviderOption(
        id = "gemini",
        name = "Gemini",
        description = "Google's free tier. Generous limits, fast, and the easiest way to start.",
        recommended = true,
        tag = "Free",
    ),
    ProviderOption(
        id = "claude",
        name = "Claude",
        description = "Anthropic. Best persona fidelity for nuance and tone.",
    ),
    ProviderOption(
        id = "openai",
        name = "OpenAI",
        description = "GPT models. Widely available, broad model choice.",
    ),
    ProviderOption(
        id = "openrouter",
        name = "OpenRouter",
        description = "One key, many models. Route across providers.",
    ),
    ProviderOption(
        id = "on-device",
        name = "On this phone",
        description = "Runs locally. No cloud, fully offline.",
        enabled = false,
        tag = "Soon",
        tagTinted = false,
    ),
)

/**
 * Onboarding 1.3 — Pick a brain.
 *
 * PersonaSpeak needs a completion backend; this is where the user nominates
 * one. Selection is local UI state for now — there's no provider registry or
 * persistence wired in, so the choice lives until onboarding completes. The
 * five options are the documented launch set; "On this phone" is advertised
 * but inert until on-device inference lands.
 */
@Composable
fun AiSelectionScreen(onContinue: () -> Unit) {
    var selected by remember { mutableStateOf("gemini") }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top row: skip link aligned right.
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onContinue)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    text = "🧠",
                    style = MaterialTheme.typography.headlineLarge,
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Pick a brain",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "PersonaSpeak needs an AI to do the rewriting. Pick one now — you can change it later.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))

                Column(
                    modifier = Modifier.padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PROVIDERS.forEach { provider ->
                        ProviderRadioCard(
                            provider = provider,
                            selected = provider.id == selected,
                            onSelect = { if (provider.enabled) selected = provider.id },
                        )
                    }
                }
            }

            // Fixed bottom CTA.
            PrimaryButton(
                text = "Continue",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun ProviderRadioCard(
    provider: ProviderOption,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor = if (selected) PrimaryContainer else OutlineVariant
    val bg = if (selected) SurfaceContainerHigh.copy(alpha = 0.4f) else SurfaceContainerLow.copy(alpha = 0.4f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (provider.enabled) 1f else 0.6f)
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(2.dp, borderColor, MaterialTheme.shapes.medium)
            .clickable(enabled = provider.enabled, onClick = onSelect)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnSurface,
                )
                if (provider.tag != null) {
                    PillBadge(
                        text = provider.tag,
                        containerColor = if (provider.tagTinted) PrimaryContainer else OutlineVariant,
                        contentColor = if (provider.tagTinted) OnPrimaryContainer else OnSurfaceVariant,
                    )
                }
            }
            Text(
                text = provider.description,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant,
            )
            if (provider.recommended) {
                Text(
                    text = "ⓘ Recommended to start",
                    style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
                    color = Primary,
                )
            }
        }

        RadioCircle(selected = selected, enabled = provider.enabled)
    }
}

@Composable
private fun RadioCircle(selected: Boolean, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) Primary else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = 2.dp,
                color = if (selected) Primary else OutlineVariant,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected && enabled) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color(0xFF003731),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
