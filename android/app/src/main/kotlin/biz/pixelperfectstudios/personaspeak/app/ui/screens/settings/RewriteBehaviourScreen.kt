package biz.pixelperfectstudios.personaspeak.app.ui.screens.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.app.ui.components.PersonaSpeakTopBar

/**
 * Rewrite behaviour radio group. Selection is held in local state only —
 * persisting it alongside the rest of the keyboard prefs is out of scope for
 * this slice and noted as a deviation.
 *
 * No mockup exists for this screen; the two options come straight from the
 * settings-home row subtitle ("Ask before replacing") and its implied opposite.
 */
@Composable
fun RewriteBehaviourScreen(onClose: () -> Unit) {
    // "Ask before replacing" is the documented default.
    var askFirst by remember { mutableStateOf(true) }

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
                    .padding(16.dp),
            ) {
                Text(
                    text = "Rewrite behaviour",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "When a persona rewrites your text, decide how it lands in the field.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))

                RewriteOptionCard(
                    title = "Ask before replacing",
                    description = "Show the rewrite in a preview. You choose whether to insert it.",
                    selected = askFirst,
                    onSelect = { askFirst = true },
                )
                Spacer(Modifier.height(12.dp))
                RewriteOptionCard(
                    title = "Replace immediately",
                    description = "Insert the rewrite straight into the field. Faster, but irreversible without undo.",
                    selected = !askFirst,
                    onSelect = { askFirst = false },
                )
            }
        }
    }
}

@Composable
private fun RewriteOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
