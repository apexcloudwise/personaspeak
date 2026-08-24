package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.ui.personas.descriptor
import biz.pixelperfectstudios.personaspeak.ui.personas.emoji

/** Android's touch-target floor. Every interactive control here clears it. */
private val MinInteractiveHeight = 48.dp

/**
 * PersonaSpeak's dedicated keyboard row.
 *
 * Hosts the full state machine for PersonaSpeak:
 * Resting, PersonaPicker, MoodPicker, Loading, Review, Applying, AppliedVerified, and Error.
 */
@Composable
fun RewritePanel(
    state: RewritePanelState,
    onRewrite: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    preExpansionImeHeightPx: () -> Int,
    modifier: Modifier = Modifier,
    onOpenPersonaPicker: () -> Unit = {},
    onSelectPersona: (PersonaId) -> Unit = {},
    onOpenMoodPicker: () -> Unit = {},
    onSelectMood: (Mood) -> Unit = {},
) {
    val density = LocalDensity.current

    val reviewBodyMaxHeightPx = when (state) {
        is RewritePanelState.Review -> {
            remember(state.candidate) {
                resultBodyMaxHeightPx(
                    preExpansionHeightPx = preExpansionImeHeightPx(),
                    density = density.density,
                )
            }
        }
        is RewritePanelState.Applying -> {
            remember(state.candidate) {
                resultBodyMaxHeightPx(
                    preExpansionHeightPx = preExpansionImeHeightPx(),
                    density = density.density,
                )
            }
        }
        is RewritePanelState.AppliedVerified -> {
            remember(state.candidate) {
                resultBodyMaxHeightPx(
                    preExpansionHeightPx = preExpansionImeHeightPx(),
                    density = density.density,
                )
            }
        }
        else -> 0
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        when (state) {
            is RewritePanelState.Resting -> {
                RestingLayout(
                    state = state,
                    onRewrite = onRewrite,
                    onOpenPersonaPicker = onOpenPersonaPicker,
                    onOpenMoodPicker = onOpenMoodPicker,
                    onSettings = onSettings,
                )
            }

            is RewritePanelState.PersonaPicker -> {
                PersonaPickerLayout(
                    state = state,
                    onSelectPersona = onSelectPersona,
                    onDismiss = onDismiss,
                    onSettings = onSettings,
                )
            }

            is RewritePanelState.MoodPicker -> {
                MoodPickerLayout(
                    state = state,
                    onSelectMood = onSelectMood,
                    onDismiss = onDismiss,
                )
            }

            is RewritePanelState.Loading -> {
                LoadingLayout(
                    state = state,
                    onDismiss = onDismiss,
                    onSettings = onSettings,
                )
            }

            is RewritePanelState.Review -> {
                ReviewLayout(
                    state = state,
                    maxBodyHeightPx = reviewBodyMaxHeightPx,
                    onAgain = onRewrite,
                    onApply = onApply,
                    onDismiss = onDismiss,
                    onSettings = onSettings,
                )
            }

            is RewritePanelState.Applying -> {
                ApplyingLayout(
                    state = state,
                    maxBodyHeightPx = reviewBodyMaxHeightPx,
                )
            }

            is RewritePanelState.AppliedVerified -> {
                AppliedVerifiedLayout(
                    state = state,
                    onDismiss = onDismiss,
                )
            }

            is RewritePanelState.Error -> {
                ErrorLayout(
                    state = state,
                    onRetry = onRewrite,
                    onDismiss = onDismiss,
                    onSettings = onSettings,
                )
            }
        }
    }
}

/** Resting state: Persona chip, Mood chip, Rewrite button, Settings button. */
@Composable
private fun RestingLayout(
    state: RewritePanelState.Resting,
    onRewrite: () -> Unit,
    onOpenPersonaPicker: () -> Unit,
    onOpenMoodPicker: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveHeight)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Persona chip
        Surface(
            modifier = Modifier
                .heightIn(min = MinInteractiveHeight)
                .clickable(onClick = onOpenPersonaPicker)
                .testTag("personaspeak_persona_chip"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = state.persona.emoji, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = state.persona.content.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = "⌄", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Mood chip
        Surface(
            modifier = Modifier
                .heightIn(min = MinInteractiveHeight)
                .clickable(onClick = onOpenMoodPicker)
                .testTag("personaspeak_mood_chip"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = state.mood.label,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(text = "⌄", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(
            onClick = onRewrite,
            modifier = Modifier
                .heightIn(min = MinInteractiveHeight)
                .testTag("personaspeak_rewrite"),
        ) {
            Text("Rewrite")
        }

        SettingsButton(onSettings)
    }
}

/** Persona picker: 2x2 grid of character tiles. */
@Composable
private fun PersonaPickerLayout(
    state: RewritePanelState.PersonaPicker,
    onSelectPersona: (PersonaId) -> Unit,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("personaspeak_persona_picker"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "CHOOSE A CHARACTER",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(MinInteractiveHeight)
                    .testTag("personaspeak_picker_close"),
            ) {
                Text("✕", style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Grid of personas
        val personas = state.personas
        val rows = personas.chunked(2)
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (persona in row) {
                    val isSelected = persona.id == state.selectedId
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = MinInteractiveHeight)
                            .clickable { onSelectPersona(persona.id) }
                            .testTag("personaspeak_persona_tile_${persona.id.value.substringAfter(':')}"),
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(text = persona.emoji, style = MaterialTheme.typography.titleMedium)
                            Column {
                                Text(
                                    text = persona.content.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = persona.descriptor,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onSettings,
                modifier = Modifier
                    .heightIn(min = MinInteractiveHeight)
                    .testTag("personaspeak_browse_all"),
            ) {
                Text("+ Browse all characters")
            }
        }
    }
}

/** Mood picker: list of supported mood options. */
@Composable
private fun MoodPickerLayout(
    state: RewritePanelState.MoodPicker,
    onSelectMood: (Mood) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("personaspeak_mood_picker"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "CHOOSE A MOOD",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(MinInteractiveHeight)
                    .testTag("personaspeak_mood_picker_close"),
            ) {
                Text("✕", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (mood in state.moods) {
                val isSelected = mood.id == state.selectedMood.id
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = MinInteractiveHeight)
                        .clickable { onSelectMood(mood) }
                        .testTag("personaspeak_mood_tile_${mood.id.value}"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mood.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Loading state: progress indicator, cancel button. */
@Composable
private fun LoadingLayout(
    state: RewritePanelState.Loading,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveHeight)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(20.dp)
                .testTag("personaspeak_loading"),
            strokeWidth = 2.dp,
        )

        Text(
            text = "${state.persona.emoji} ${state.persona.content.name} · ${state.mood.label}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )

        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .heightIn(min = MinInteractiveHeight)
                .testTag("personaspeak_cancel"),
        ) {
            Text("Cancel")
        }

        SettingsButton(onSettings)
    }
}

/** Review state: candidate body with Use this, Again, Dismiss actions. */
@Composable
private fun ReviewLayout(
    state: RewritePanelState.Review,
    maxBodyHeightPx: Int,
    onAgain: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
) {
    val density = LocalDensity.current
    val bodyModifier = if (maxBodyHeightPx > 0) {
        Modifier.heightIn(max = with(density) { maxBodyHeightPx.toDp() })
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${state.persona.emoji} ${state.persona.content.name} · ${state.mood.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }

        // Candidate body
        Column(
            modifier = bodyModifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("personaspeak_candidate_body"),
        ) {
            Text(
                text = state.candidate.replacement,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("personaspeak_candidate"),
            )
        }

        // Action row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(
                onClick = onApply,
                modifier = Modifier
                    .heightIn(min = MinInteractiveHeight)
                    .testTag("personaspeak_apply"),
            ) {
                Text("Use this")
            }
            TextButton(
                onClick = onAgain,
                modifier = Modifier
                    .heightIn(min = MinInteractiveHeight)
                    .testTag("personaspeak_again"),
            ) {
                Text("↻ Again")
            }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = MinInteractiveHeight)
                    .testTag("personaspeak_dismiss"),
            ) {
                Text("Dismiss")
            }

            Spacer(modifier = Modifier.weight(1f))
            SettingsButton(onSettings)
        }
    }
}

/** Applying state. */
@Composable
private fun ApplyingLayout(
    state: RewritePanelState.Applying,
    maxBodyHeightPx: Int,
) {
    val density = LocalDensity.current
    val bodyModifier = if (maxBodyHeightPx > 0) {
        Modifier.heightIn(max = with(density) { maxBodyHeightPx.toDp() })
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = bodyModifier
                .fillMaxWidth()
                .testTag("personaspeak_candidate_body"),
        ) {
            Text(
                text = state.candidate.replacement,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("personaspeak_candidate"),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = "Applying to editor…",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** Applied verified layout. */
@Composable
private fun AppliedVerifiedLayout(
    state: RewritePanelState.AppliedVerified,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveHeight)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "✓ Applied",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("personaspeak_applied_verified"),
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .heightIn(min = MinInteractiveHeight)
                .testTag("personaspeak_dismiss"),
        ) {
            Text("Done")
        }
    }
}

/** Error state: amber advisory card with clear cause and actions. */
@Composable
private fun ErrorLayout(
    state: RewritePanelState.Error,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("personaspeak_error_card"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "⚠️ ${state.error.title}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            text = state.error.explanation,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("personaspeak_message"),
        )

        if (state.error.editorUntouched) {
            Text(
                text = "Your text in the editor was not modified.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.error.canRetry) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .heightIn(min = MinInteractiveHeight)
                        .testTag("personaspeak_retry"),
                ) {
                    Text("Try again")
                }
            }

            if (state.error.opensSettings) {
                TextButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .heightIn(min = MinInteractiveHeight)
                        .testTag("personaspeak_settings"),
                ) {
                    Text("Open settings")
                }
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = MinInteractiveHeight)
                    .testTag("personaspeak_dismiss"),
            ) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun SettingsButton(onSettings: () -> Unit) {
    IconButton(
        onClick = onSettings,
        modifier = Modifier
            .heightIn(min = MinInteractiveHeight)
            .testTag("personaspeak_settings"),
    ) {
        Icon(
            painter = painterResource(android.R.drawable.ic_menu_manage),
            contentDescription = "Settings",
            modifier = Modifier.size(20.dp),
        )
    }
}
