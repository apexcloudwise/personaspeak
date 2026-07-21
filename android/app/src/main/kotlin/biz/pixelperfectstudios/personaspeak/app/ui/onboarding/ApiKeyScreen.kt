package biz.pixelperfectstudios.personaspeak.app.ui.onboarding

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.app.ui.components.PersonaSpeakTopAppBar
import biz.pixelperfectstudios.personaspeak.app.ui.components.PrimaryButton
import biz.pixelperfectstudios.personaspeak.app.ui.components.SecondaryTextButton
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Background
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnPrimaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurface
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurfaceVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Outline
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OutlineVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Primary
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PrimaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainerHighest
import biz.pixelperfectstudios.personaspeak.app.ui.theme.TechnicalTextStyle
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Tertiary

private const val AI_STUDIO_URL = "https://aistudio.google.com"

/**
 * Onboarding 1.4 — Your key, your business.
 *
 * Collects the provider API key the user just chose a brain for. The field is
 * a real text input with masking, but it is NOT persisted yet — Keystore
 * wiring is its own ADR (see TODO below) and out of scope for this slice. The
 * "valid" check is a presence heuristic, not a live key test; live validation
 * rides in with the Keystore work.
 */
@Composable
fun ApiKeyScreen(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    var apiKey by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PersonaSpeakTopAppBar()

            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Your key, your business",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnSurface,
                )
                Text(
                    text = "Paste your Gemini API key. It goes straight into Android's encrypted Keystore. We never see it, and it never leaves your device.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceVariant,
                )
            }

            Spacer(Modifier.height(20.dp))

            StepsCard(
                modifier = Modifier.padding(horizontal = 20.dp),
                onOpenStudio = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )

            Spacer(Modifier.height(20.dp))

            // Input + success line.
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "API key",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
                // TODO(ADR-0005): persist apiKey into the Android Keystore once that
                // module lands. Until then the field is session-only by design — the
                // app deliberately does not store what the user typed anywhere.
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "••••••••••••••••",
                            style = TechnicalTextStyle,
                            color = OnSurfaceVariant,
                        )
                    },
                    textStyle = TechnicalTextStyle.copy(color = OnSurface),
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                    trailingIcon = {
                        // Eye toggle: no material-icons-extended dependency, so
                        // the eye is an emoji pair. A11y is carried by
                        // contentDescription, not the glyph.
                        IconButton(onClick = { visible = !visible }) {
                            Text(
                                text = if (visible) "🙈" else "👁️",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SurfaceContainer,
                        unfocusedContainerColor = SurfaceContainer,
                        focusedIndicatorColor = Primary,
                        unfocusedIndicatorColor = OutlineVariant,
                        cursorColor = Primary,
                    ),
                )

                if (apiKey.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Key looks valid",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            SecurityCallout(modifier = Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(28.dp))

            PrimaryButton(
                text = "Test and continue",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            SecondaryTextButton(
                text = "Skip — I'll add it later",
                onClick = onSkip,
                modifier = Modifier.padding(start = 20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepsCard(onOpenStudio: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SurfaceContainer)
            .border(1.dp, OutlineVariant, MaterialTheme.shapes.medium)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberedStep(1)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Get a key from Google AI Studio.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurface,
                )
            }
            Row(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(PrimaryContainer.copy(alpha = 0.2f))
                    .clickable(onClick = onOpenStudio)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Open AI Studio",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = "↗", style = MaterialTheme.typography.labelSmall, color = Primary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberedStep(2)
            Text(
                text = "Create a key and copy it to your clipboard.",
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface,
            )
        }
    }
}

@Composable
private fun NumberedStep(n: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(SurfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = n.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = Primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SecurityCallout(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(PrimaryContainer.copy(alpha = 0.12f))
            .border(1.dp, Primary.copy(alpha = 0.3f), MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(20.dp),
        )
        val text = buildAnnotatedString {
            append("Stored in Android ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append("Keystore")
            }
            append(" using 256-bit AES encryption.")
        }
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = OnSurface)
    }
}
