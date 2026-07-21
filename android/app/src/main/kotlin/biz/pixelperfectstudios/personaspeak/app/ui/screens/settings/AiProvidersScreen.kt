package biz.pixelperfectstudios.personaspeak.app.ui.screens.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import biz.pixelperfectstudios.personaspeak.app.ui.components.PersonaSpeakTopBar
import biz.pixelperfectstudios.personaspeak.app.ui.components.SectionLabel
import biz.pixelperfectstudios.personaspeak.app.ui.data.SampleProviders
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PillShape
import biz.pixelperfectstudios.personaspeak.app.ui.theme.TechnicalTextStyle

/**
 * AI provider configuration. Lists the active provider, an API-credentials
 * field, the other available providers, and a couple of global toggles. All
 * state here is local and unsaved — wiring to the real provider registry and
 * Keystore is out of scope for the settings-screens slice.
 */
@Composable
fun AiProvidersScreen(onClose: () -> Unit) {
    var autoRetry by remember { mutableStateOf(true) }
    var contextWindow by remember { mutableStateOf(false) }

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
            ) {
                // Breadcrumb
                Text(
                    text = "Settings › AI Providers",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Text(
                    text = "Configuration",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Text(
                    text = "Manage your LLM engines and API credentials.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(Modifier.height(16.dp))

                SampleProviders.active?.let { ActiveProviderCard(it.name) }

                SectionLabel("API credentials")
                ApiCredentialsCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                SectionLabel("Available providers")
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SampleProviders.all.filter { !it.isActive }.forEach { provider ->
                        AvailableProviderRow(provider)
                    }
                }

                SectionLabel("Global controls")
                GlobalControlsCard(
                    autoRetry = autoRetry,
                    onAutoRetryChange = { autoRetry = it },
                    contextWindow = contextWindow,
                    onContextWindowChange = { contextWindow = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ActiveProviderCard(name: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProviderIconBox(name.first().toString())
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = PillShape,
                        ) {
                            Text(
                                text = "Free",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Using Google's most capable multimodal models.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { /* test connection — out of scope */ },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                shape = MaterialTheme.shapes.small,
            ) {
                Text("Test connection")
            }
        }
    }
}

@Composable
private fun ApiCredentialsCard(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val masked = "••••••••••••••••••••"
    Surface(
        color = Color(0xFF2D333B),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "🔑", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (visible) "AIzaSY_sample_key_redacted" else masked,
                style = TechnicalTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (visible) "🙈" else "👁",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { visible = !visible }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                text = "🗑",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .clickable { /* delete — out of scope */ }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun AvailableProviderRow(
    provider: biz.pixelperfectstudios.personaspeak.app.ui.data.ProviderDisplay,
) {
    val isLocal = provider.isLocal
    Surface(
        color = if (isLocal) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (isLocal) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProviderIconBox(provider.name.first().toString())
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "(${provider.shortName})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = provider.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = { /* setup/connect — out of scope */ },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(provider.action)
            }
        }
    }
}

@Composable
private fun GlobalControlsCard(
    autoRetry: Boolean,
    onAutoRetryChange: (Boolean) -> Unit,
    contextWindow: Boolean,
    onContextWindowChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            ToggleRow(
                title = "Auto-retry on failure",
                description = "Switch to backup provider if primary fails.",
                checked = autoRetry,
                onCheckedChange = onAutoRetryChange,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ToggleRow(
                title = "Context window management",
                description = "Optimize token usage for long conversations.",
                checked = contextWindow,
                onCheckedChange = onContextWindowChange,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ProviderIconBox(glyph: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
