package biz.pixelperfectstudios.personaspeak.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.app.ui.components.PillBadge
import biz.pixelperfectstudios.personaspeak.app.ui.components.PrimaryButton
import biz.pixelperfectstudios.personaspeak.app.ui.components.SecondaryTextButton
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Background
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnBackground
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurface
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurfaceVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OutlineVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Primary
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Secondary
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainer

private data class PersonaAvatar(val emoji: String, val name: String)

private val ONBOARDING_PERSONAS = listOf(
    PersonaAvatar("🎩", "Jeeves"),
    PersonaAvatar("🏛️", "Humphrey"),
    PersonaAvatar("🤠", "Schultz"),
    PersonaAvatar("🎬", "Bachchan"),
)

private val REASSURANCE_BADGES = listOf("Autocorrect", "Glide typing", "Works offline")

/**
 * Onboarding 1.1 — Welcome.
 *
 * First screen a fresh install lands on. Hero panel evokes the keyboard + the
 * top hat that triggers a rewrite; below it, the elevator pitch, the cast of
 * launch personas, the "we're still a real keyboard" reassurance row, and the
 * two ways forward: start setup, or skip straight to settings.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onSkipSetup: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Box(modifier = Modifier.fillMaxSize()) {
            AtmosphericGlow()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 64.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HeroPanel()

                Spacer(Modifier.height(32.dp))

                Text(
                    text = "A keyboard with better manners",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnBackground,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                val body = buildAnnotatedString {
                    append("PersonaSpeak is a full keyboard — everything you expect, plus a row of characters who rewrite your words. Type ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = OnSurface)) {
                        append("cant make it sorry")
                    }
                    append(", tap the top hat, and Jeeves declines on your behalf with honour.")
                }
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                PersonaRow(ONBOARDING_PERSONAS)

                Spacer(Modifier.height(28.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    REASSURANCE_BADGES.forEach { label ->
                        GlassReassuranceBadge(label)
                    }
                }

                Spacer(Modifier.height(40.dp))

                PrimaryButton(
                    text = "Get started",
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                SecondaryTextButton(
                    text = "I've done this before — skip setup",
                    onClick = onSkipSetup,
                )
            }
        }
    }
}

@Composable
private fun HeroPanel() {
    Box(contentAlignment = Alignment.Center) {
        // Glass panel evoking the keyboard + trigger hat. Real illustration
        // assets aren't vendored yet (follow-up); this composable is the slot
        // they drop into.
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(SurfaceContainer.copy(alpha = 0.6f))
                .border(1.dp, OutlineVariant, MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = "🎩", style = MaterialTheme.typography.headlineLarge)
                KeyboardGrid()
            }
        }
        // Floating spark/edit accents around the panel.
        Text(
            text = "✨",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.offset(x = 96.dp, y = (-96).dp),
        )
        Text(
            text = "✏️",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.offset(x = (-100).dp, y = 90.dp),
        )
    }
}

@Composable
private fun KeyboardGrid() {
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { ch ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(OnSurface.copy(alpha = 0.12f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonaRow(personas: List<PersonaAvatar>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        personas.forEach { persona ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(SurfaceContainer)
                        .border(1.dp, OutlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = persona.emoji, style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    text = persona.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GlassReassuranceBadge(label: String) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(SurfaceContainer.copy(alpha = 0.6f))
            .border(1.dp, OutlineVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = OnSurface,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        )
    }
}

/**
 * The two soft teal/cyan blobs blurred behind the welcome content. Purely
 * atmospheric; sits behind everything and ignores pointer events.
 */
@Composable
private fun AtmosphericGlow() {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-80).dp, y = 40.dp)
                .background(Primary.copy(alpha = 0.18f), CircleShape)
                .blur(radius = 90.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = 200.dp, y = 500.dp)
                .background(Secondary.copy(alpha = 0.14f), CircleShape)
                .blur(radius = 100.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
        )
    }
}
