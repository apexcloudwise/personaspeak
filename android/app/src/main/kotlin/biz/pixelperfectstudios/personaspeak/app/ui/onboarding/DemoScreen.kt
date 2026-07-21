package biz.pixelperfectstudios.personaspeak.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import biz.pixelperfectstudios.personaspeak.app.ui.components.PrimaryButton
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnPrimary
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurface
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurfaceVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OutlineVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Primary
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PrimaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PillShape
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SecondaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainerHigh
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainerHighest
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainerLow
import biz.pixelperfectstudios.personaspeak.app.ui.theme.KeyShape

// The canned before/after the demo walks the user through. Real provider
// wiring is a different PR; this screen exists to show the shape of the magic.
private const val DRAFT = "cant make it sorry"
private const val REWRITE =
    "I regret to report, sir, that circumstances have conspired against my attendance this evening."

// The demo masquerades as a different app (a messenger), so it gets its own
// darker background rather than the app surface token.
private val ChatBackground = Color(0xFF0B141A)

/**
 * Onboarding screen 1.5 — a self-contained, fully simulated rewrite.
 *
 * Everything here is canned: the chat, the keyboard, and the result. The FAB
 * toggles the result overlay so the user sees the before/after contract; "Use
 * this" is what completes onboarding. When the real provider/IME pipeline
 * lands, the canned [REWRITE] gets swapped for a live `CompletionProvider`
 * call — search for the TODO below.
 */
@Composable
fun DemoScreen(onComplete: () -> Unit) {
    var showResult by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatBackground),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FakeChatAppBar()
            ChatThread(
                modifier = Modifier.weight(1f),
            )
            PersonaStrip(
                onTransform = { showResult = true },
                // The strip blurs once the result is up, mimicking the real IME
                // handing focus to the result card.
                modifier = Modifier.alpha(if (showResult) 0.4f else 1f),
            )
            FakeKeyboard()
        }

        AnimatedVisibility(
            visible = showResult,
            enter = fadeIn() + scaleIn(initialScale = 0.96f),
            exit = fadeOut() + scaleOut(targetScale = 0.96f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            ResultOverlay(
                onUse = onComplete,
                onAgain = { showResult = false },
                onClose = { showResult = false },
            )
        }
    }
    // TODO(onboarding-demo): replace canned REWRITE with a live
    // CompletionProvider.rewrite() call once the provider registry and the
    // IME→provider bridge land. The overlay's onUse/onAgain wiring stays; only
    // the source of [REWRITE] changes.
}

@Composable
private fun FakeChatAppBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(SurfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowBack,
            contentDescription = "Back",
            tint = OnSurface,
            modifier = Modifier.size(22.dp),
        )
        Surface(
            color = Primary,
            shape = CircleShape,
            modifier = Modifier.size(36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = "A", color = OnPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Alex",
                style = MaterialTheme.typography.labelMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "online",
                style = MaterialTheme.typography.labelSmall,
                color = Primary,
            )
        }
        Text("📹", style = MaterialTheme.typography.labelMedium)
        Text("📞", style = MaterialTheme.typography.labelMedium)
        Text("⋮", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
    }
}

@Composable
private fun ChatThread(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Incoming message, left-aligned.
        ChatBubble(
            text = "Are you coming to dinner tonight?",
            timestamp = "18:42",
            incoming = true,
            modifier = Modifier.align(Alignment.Start),
        )
        Spacer(modifier = Modifier.height(4.dp))
        // The user's draft — the thing the demo transforms.
        ChatBubble(
            text = DRAFT,
            timestamp = "18:43",
            incoming = false,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun ChatBubble(
    text: String,
    timestamp: String,
    incoming: Boolean,
    modifier: Modifier = Modifier,
) {
    val container = if (incoming) SurfaceContainerHigh else PrimaryContainer.copy(alpha = 0.32f)
    val contentColor = if (incoming) OnSurface else OnSurface
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
        )
        Text(
            text = timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun PersonaStrip(onTransform: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Persona chip — 🎩 Jeeves. Dropdown affordance is cosmetic here.
        StripChip(
            leading = "🎩",
            label = "Jeeves",
            italic = false,
            modifier = Modifier.weight(1f),
        )
        // Mood chip — "polite", italicised to mark it as the voice modifier.
        StripChip(
            leading = null,
            label = "polite",
            italic = true,
            modifier = Modifier.weight(1f),
        )
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier.size(44.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Primary, SecondaryContainer)),
                        )
                        .clickable(onClick = onTransform),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "→",
                        color = OnPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun StripChip(
    leading: String?,
    label: String,
    italic: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(SurfaceContainerHigh)
            .border(1.dp, OutlineVariant, PillShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) {
            Text(text = leading, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurface,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        )
        Text(
            text = "▾",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant,
        )
    }
}

@Composable
private fun FakeKeyboard() {
    val rows = remember {
        listOf(
            "qwertyuiop".toList(),
            "asdfghjkl".toList(),
            listOf("⇧") + "zxcvbnm".toList() + listOf("⌫"),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceContainerLow)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                row.forEach { key ->
                    KeyCap(label = key.toString())
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyCap(label = "123", weight = 1f)
            KeyCap(label = "space", weight = 3f)
            KeyCap(label = "⏎", weight = 1f)
        }
    }
}

@Composable
private fun RowScope.KeyCap(label: String, weight: Float = 1f) {
    // Decorative only — the demo's keyboard is here to look like a keyboard,
    // not to input. Keeping it non-interactive on purpose.
    Box(
        modifier = Modifier
            .height(40.dp)
            .weight(weight)
            .clip(KeyShape)
            .background(SurfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = OnSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ResultOverlay(
    onUse: () -> Unit,
    onAgain: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = SurfaceContainer,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🎩", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "JEEVES · POLITE",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(6.dp),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = REWRITE,
                style = MaterialTheme.typography.bodyLarge,
                color = OnSurface,
                fontStyle = FontStyle.Italic,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryButton(text = "Use this", onClick = onUse)
                }
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(SurfaceContainerHighest)
                        .clickable(onClick = onAgain)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = OnSurface,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Again",
                        style = MaterialTheme.typography.labelMedium,
                        color = OnSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
