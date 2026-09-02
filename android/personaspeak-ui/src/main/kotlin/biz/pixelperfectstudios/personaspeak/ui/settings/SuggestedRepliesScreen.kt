package biz.pixelperfectstudios.personaspeak.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

private val MinInteractiveHeight = 48.dp

/**
 * Suggested replies settings (Phase 2, ADR-0011).
 *
 * The system's notification-access screen is the single switch; this screen
 * explains the feature, shows live status, and runs the prominent-disclosure
 * consent gate BEFORE the deep link (Play User Data policy: the disclosure
 * lives in the feature's flow, not the listing or the privacy policy).
 */
@Composable
fun SuggestedRepliesScreen(
    enabled: Boolean,
    onBack: () -> Unit,
    onGrantNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConsentGate by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header with back action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveHeight)
                .testTag("personaspeak_suggested_replies_topbar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier
                    .heightIn(min = MinInteractiveHeight)
                    .testTag("personaspeak_suggested_replies_back"),
            ) {
                Text("← Back")
            }
        }

        Text(
            text = "Suggested Replies",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Text(
            text = "When a message arrives, your active character drafts three " +
                "replies in the keyboard strip — before you type anything. " +
                "You review, edit, and send them yourself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Live status card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("personaspeak_suggested_replies_status"),
            shape = RoundedCornerShape(12.dp),
            color = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (enabled) {
                        "Notification access: On"
                    } else {
                        "Notification access: Off"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (enabled) {
                        "Messages you receive are drafted for reply in the keyboard strip."
                    } else {
                        "Nothing is read until you grant notification access. Turn it on below."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Consent gate: prominent disclosure BEFORE the system deep link
        Button(
            onClick = { showConsentGate = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveHeight)
                .testTag("personaspeak_suggested_replies_grant"),
        ) {
            Text(
                text = if (enabled) {
                    "Manage notification access"
                } else {
                    "Turn on suggested replies"
                },
            )
        }

        // Privacy copy — the load-bearing claims, stated plainly
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("personaspeak_suggested_replies_privacy_card"),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "What we read",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "The sender, the app, and the text of incoming message " +
                        "notifications — only while notification access is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "What we keep",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "The latest message per conversation, in RAM only. Applying " +
                        "a reply forgets it. Nothing touches disk, logs, or backups.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = "What we never do",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PrivacyNeverLine("Never send a message or reply on your behalf")
                PrivacyNeverLine("Never mark messages as read")
                PrivacyNeverLine("Never store message text anywhere")
                PrivacyNeverLine("Never log message text")
            }
        }
    }

    if (showConsentGate) {
        AlertDialog(
            onDismissRequest = { showConsentGate = false },
            modifier = Modifier.testTag("personaspeak_suggested_replies_consent_dialog"),
            title = {
                Text(
                    text = "Allow PersonaSpeak to read message notifications?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "What we read: the sender, app, and text of incoming " +
                            "message notifications — only while access is on.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "What we keep: the latest message per conversation, in RAM " +
                            "only. Applying a reply forgets it. Nothing touches disk, " +
                            "logs, or backups.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "What we never do: send anything, mark messages read, or " +
                            "reply on your behalf. Drafts go into your editor for you " +
                            "to review.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConsentGate = false
                        onGrantNotificationAccess()
                    },
                    modifier = Modifier
                        .heightIn(min = MinInteractiveHeight)
                        .testTag("personaspeak_suggested_replies_consent_confirm"),
                ) {
                    Text("I understand — open system settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConsentGate = false },
                    modifier = Modifier
                        .heightIn(min = MinInteractiveHeight)
                        .testTag("personaspeak_suggested_replies_consent_cancel"),
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PrivacyNeverLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "✕",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
