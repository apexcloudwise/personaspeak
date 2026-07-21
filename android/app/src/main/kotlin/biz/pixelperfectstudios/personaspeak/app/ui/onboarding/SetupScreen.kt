package biz.pixelperfectstudios.personaspeak.app.ui.onboarding

import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import biz.pixelperfectstudios.personaspeak.app.ui.components.PersonaSpeakTopAppBar
import biz.pixelperfectstudios.personaspeak.app.ui.components.PrimaryButton
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Background
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnPrimaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurface
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OnSurfaceVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.OutlineVariant
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Primary
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PrimaryContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainer
import biz.pixelperfectstudios.personaspeak.app.ui.theme.SurfaceContainerHigh
import biz.pixelperfectstudios.personaspeak.app.ui.theme.Tertiary
import biz.pixelperfectstudios.personaspeak.app.ui.theme.TertiaryContainer

/**
 * Onboarding 1.2 — Make it your keyboard.
 *
 * Walks the user through actually enabling PersonaSpeak as an IME: step one
 * opens the system input-method settings page, step two (unlocked once the
 * IME is really enabled, checked on every resume) opens the system keyboard
 * picker. Both buttons fire real platform intents — no faking. The bottom
 * CTA stays available regardless, so onboarding never dead-ends even on a
 * build where the :keyboard service isn't wired yet.
 */
@Composable
fun SetupScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val imm = remember(context) {
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    }
    var imeEnabled by remember { mutableStateOf(false) }

    // Re-check on every resume: user leaves to toggle the IME, comes back, and
    // step two reflects reality. Matches the app's own package, so it stays
    // correct once the :keyboard service ships its InputMethodService.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        fun refresh() {
            imeEnabled = imm.getEnabledInputMethodList()
                .any { it.packageName == context.packageName }
        }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PersonaSpeakTopAppBar()

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Make it your keyboard",
                    style = MaterialTheme.typography.headlineLarge,
                    color = OnSurface,
                )
                Text(
                    text = "Two quick steps. You can always switch back.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                StepCard(
                    icon = Icons.Filled.Check,
                    title = "Enable PersonaSpeak",
                    description = "Open keyboard settings and turn on PersonaSpeak in your languages list.",
                    buttonLabel = "Open settings",
                    onButtonClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    },
                )

                Box(modifier = Modifier.alpha(if (imeEnabled) 1f else 0.6f)) {
                    StepCard(
                        icon = null,
                        iconEmoji = "⌨️",
                        title = "Set as default",
                        description = "Switch your active keyboard to PersonaSpeak to begin transformation.",
                        buttonLabel = "Switch keyboard",
                        enabled = imeEnabled,
                        onButtonClick = { imm.showInputMethodPicker() },
                    )
                }

                PrivacyCallout()
            }

            Spacer(Modifier.height(32.dp))

            PrimaryButton(
                text = "Continue",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    description: String,
    buttonLabel: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconEmoji: String? = null,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(SurfaceContainer)
            .border(1.dp, OutlineVariant, MaterialTheme.shapes.medium)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(PrimaryContainer.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = Primary)
                } else {
                    Text(iconEmoji ?: "", style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = OnSurface,
            )
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurfaceVariant,
        )
        PrimaryButton(
            text = buttonLabel,
            onClick = onButtonClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PrivacyCallout() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(TertiaryContainer.copy(alpha = 0.16f))
            .border(1.dp, Tertiary.copy(alpha = 0.4f), MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = Tertiary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "Android will warn that this keyboard can see what you type. Your text is sent only to the AI provider you choose, only when you ask for a rewrite — never in the background.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurface,
        )
    }
}
