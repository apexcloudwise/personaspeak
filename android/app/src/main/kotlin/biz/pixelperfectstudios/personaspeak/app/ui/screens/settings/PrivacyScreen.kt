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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.app.ui.components.PersonaSpeakTopBar

/**
 * Privacy / data-handling disclosure.
 *
 * The mockup's headline here ("What we store: Nothing.") is deliberately NOT
 * shipped. ADR-0005 (privacy-posture fork-audit, Proposed→Accepted on merge)
 * finds that copy unsafe for the fork: AnySoftKeyboard now ships in the tree,
 * and a predictive keyboard stores data by design. Until the vendored keyboard
 * tree is audited end to end, every claim on this screen stays specific and
 * verifiable — vaguer-but-true beats a crisp line we can't back up. This is
 * load-bearing plain text per VOICE.md rule 6: no jokes, no flourish.
 */
@Composable
fun PrivacyScreen(onClose: () -> Unit) {
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Where your text goes",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "A short, honest account of what this app does with what you type. " +
                        "We will tighten or correct these statements as the keyboard audit (ADR-0005) completes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(20.dp))

                PrivacyRow(
                    glyph = "💬",
                    tint = MaterialTheme.colorScheme.primary,
                    title = "Your text",
                    body = "When you tap rewrite, the selected text is sent to the AI provider " +
                        "you picked. It is not routed through our own servers; we do not run any. " +
                        "What your provider retains after that is governed by their policy — read it.",
                )
                PrivacyRow(
                    glyph = "🔑",
                    tint = MaterialTheme.colorScheme.secondary,
                    title = "Your API key",
                    body = "Stored on this device. We use Android's standard app storage. We have " +
                        "not yet audited whether the hardware-backed keystore is in use, so we will " +
                        "not claim hardware-level security here until we have checked.",
                )
                PrivacyRow(
                    glyph = "🧠",
                    tint = MaterialTheme.colorScheme.tertiary,
                    title = "On-device data",
                    body = "The keyboard keeps what it needs to work: learned words, your selected " +
                        "persona and mood, and these settings. It stays on your phone. You will be " +
                        "able to clear it from this screen once clearing is wired up (it is not in " +
                        "this build).",
                )
                PrivacyRow(
                    glyph = "🚫",
                    tint = MaterialTheme.colorScheme.error,
                    title = "Telemetry & analytics",
                    body = "Off in this build. Because the keyboard codebase is still being audited, " +
                        "we will not make a stronger 'no tracking, ever' claim until that audit is " +
                        "complete.",
                )

                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Note: like any Android app, anything written to the system log " +
                            "(logcat) can be read by other apps that hold the READ_LOGS permission, " +
                            "which Android restricts to system or rooted access. We avoid logging " +
                            "personal data.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { /* open repo — out of scope */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Read the code yourself",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyRow(
    glyph: String,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Surface(
                color = tint.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = glyph, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
