package biz.pixelperfectstudios.personaspeak.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.ui.personas.emoji

private val MinInteractiveHeight = 48.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    state: SettingsState,
    onNavigateToPersonas: () -> Unit,
    onNavigateToProviderSetup: () -> Unit,
    onSelectDefaultMood: (Mood) -> Unit,
    onOpenAskSettings: () -> Unit,
    onOpenEnableIme: () -> Unit = {},
    onClearNotice: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showMoodDialog by remember { mutableStateOf(false) }
    var onboardingDismissed by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("personaspeak_settings_topbar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PersonaSpeak Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Active Notice Banner (if any)
        state.notice?.let { noticeText ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("personaspeak_settings_notice_banner"),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = noticeText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = onClearNotice,
                        modifier = Modifier
                            .size(MinInteractiveHeight)
                            .semantics { contentDescription = "Dismiss notice" },
                    ) {
                        Text("✕", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Onboarding Card (shown when unconfigured and not dismissed)
        if (state.providerStatus is ProviderStatusSummary.Unconfigured && !onboardingDismissed) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("personaspeak_settings_onboarding_card"),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Get started",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        IconButton(
                            onClick = { onboardingDismissed = true },
                            modifier = Modifier
                                .size(MinInteractiveHeight)
                                .semantics { contentDescription = "Dismiss onboarding" }
                                .testTag("personaspeak_settings_onboarding_dismiss"),
                        ) {
                            Text("✕", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    OnboardingStep(
                        number = "1.",
                        label = "Enable PersonaBoard in system keyboards",
                        onClick = onOpenEnableIme,
                    )
                    OnboardingStep(
                        number = "2.",
                        label = "Set it as your keyboard",
                        onClick = null,
                    )
                    OnboardingStep(
                        number = "3.",
                        label = "Pick your character",
                        onClick = onNavigateToPersonas,
                    )
                    OnboardingStep(
                        number = "4.",
                        label = "Connect a brain",
                        onClick = onNavigateToProviderSetup,
                    )
                }
            }
        }

        // CHARACTERS Group
        SettingsSection(title = "CHARACTERS") {
            // Characters / Personas Row
            SettingsRow(
                title = "Characters",
                subtitle = "${state.activePersona?.emoji ?: "🎭"} ${state.activePersona?.content?.name ?: "Jeeves"} (${state.personas.size} characters available)",
                actionLabel = "Browse →",
                onClick = onNavigateToPersonas,
                modifier = Modifier.testTag("personaspeak_settings_characters_row"),
            )

            // Default Mood Row
            SettingsRow(
                title = "Default Mood",
                subtitle = "${state.defaultMood.label} (default tone for rewrites)",
                actionLabel = "Change",
                onClick = { showMoodDialog = true },
                modifier = Modifier.testTag("personaspeak_settings_mood_row"),
            )

            // Review Before Replace Row (Fixed Product Behavior)
            SettingsInfoRow(
                title = "Review before replacing",
                subtitle = "Always on (fixed product behavior)",
                explanation = "All rewrites must be reviewed in the result card before replacing editor text. Immediate replacement is disabled for safety.",
                modifier = Modifier.testTag("personaspeak_settings_review_row"),
            )

            // Initialization Note
            Text(
                text = "ℹ️ Persona and mood defaults take effect on the next keyboard initialization in this session (not saved to disk).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("personaspeak_settings_init_notice"),
            )
        }

        // THE BRAIN Group
        SettingsSection(title = "THE BRAIN") {
            // AI Brain Provider Row
            SettingsRow(
                title = "AI Brain",
                subtitle = state.providerStatus.describe(),
                actionLabel = if (state.providerStatus is ProviderStatusSummary.Configured) "Manage →" else "Configure →",
                onClick = onNavigateToProviderSetup,
                modifier = Modifier.testTag("personaspeak_settings_provider_row"),
            )

            // Privacy Posture Disclosure
            Text(
                text = "🔒 Privacy: No drafts, prompts, or provider responses are saved to storage.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .testTag("personaspeak_settings_privacy_notice"),
            )
        }

        // TYPING Group
        SettingsSection(title = "TYPING") {
            SettingsRow(
                title = "Keyboard Settings (AnySoftKeyboard)",
                subtitle = "Languages, layouts, autocorrect, and gesture typing",
                actionLabel = "Open →",
                onClick = onOpenAskSettings,
                modifier = Modifier.testTag("personaspeak_settings_typing_row"),
            )
        }
    }

    // Default Mood Selection Dialog
    if (showMoodDialog) {
        AlertDialog(
            onDismissRequest = { showMoodDialog = false },
            modifier = Modifier.testTag("personaspeak_settings_mood_dialog"),
            title = {
                Text(
                    text = "Choose Default Mood",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Select the starting tone applied when rewriting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Mood.ALL.forEach { mood ->
                        val isSelected = mood == state.defaultMood
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinInteractiveHeight)
                                .clickable {
                                    onSelectDefaultMood(mood)
                                    showMoodDialog = false
                                }
                                .semantics {
                                    contentDescription = "Mood ${mood.label}${if (isSelected) ", selected" else ""}"
                                }
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectDefaultMood(mood)
                                    showMoodDialog = false
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mood.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showMoodDialog = false },
                    modifier = Modifier.heightIn(min = MinInteractiveHeight),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveHeight)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title, $subtitle. Tap to $actionLabel" },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun OnboardingStep(
    number: String,
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (onClick != null) FontWeight.SemiBold else FontWeight.Normal,
            color = if (onClick != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    subtitle: String,
    explanation: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveHeight),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
