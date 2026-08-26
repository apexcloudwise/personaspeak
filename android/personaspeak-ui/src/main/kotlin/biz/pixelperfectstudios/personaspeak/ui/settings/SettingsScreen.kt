package biz.pixelperfectstudios.personaspeak.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.PersonaId

/**
 * Top-level container for PersonaSpeak Settings.
 *
 * Routes dynamically across Home, Persona Browser, Persona Detail, and
 * Provider Setup destinations.
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onNavigate: (SettingsDestination) -> Unit,
    onBack: () -> Unit,
    onSelectPersona: (PersonaId) -> Unit,
    onSelectDefaultMood: (Mood) -> Unit,
    onOpenAskSettings: () -> Unit,
    onSaveProvider: (providerId: String, apiKey: String, model: String?, onDone: () -> Unit) -> Unit = { _, _, _, _ -> },
    onClearProvider: (onDone: () -> Unit) -> Unit = {},
    onNavigateToProviderSetup: () -> Unit = {},
    onOpenEnableIme: () -> Unit = {},
    onClearNotice: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val dest = state.destination) {
            is SettingsDestination.Home -> {
                SettingsHomeScreen(
                    state = state,
                    onNavigateToPersonas = { onNavigate(SettingsDestination.Personas) },
                    onSelectDefaultMood = onSelectDefaultMood,
                    onOpenAskSettings = onOpenAskSettings,
                    onClearNotice = onClearNotice,
                    onNavigateToProviderSetup = onNavigateToProviderSetup,
                    onOpenEnableIme = onOpenEnableIme,
                )
            }
            is SettingsDestination.Personas -> {
                PersonaBrowserScreen(
                    state = state,
                    onBack = onBack,
                    onSelectPersonaDetail = { personaId ->
                        onNavigate(SettingsDestination.PersonaDetail(personaId))
                    },
                )
            }
            is SettingsDestination.PersonaDetail -> {
                val detailPersona = state.selectedDetailPersona
                    ?: state.personas.find { it.id == dest.personaId }
                if (detailPersona != null) {
                    PersonaDetailScreen(
                        persona = detailPersona,
                        isActive = detailPersona.id == state.activePersonaId,
                        notice = state.notice,
                        onBack = onBack,
                        onSetActive = onSelectPersona,
                    )
                } else {
                    PersonaBrowserScreen(
                        state = state,
                        onBack = onBack,
                        onSelectPersonaDetail = { personaId ->
                            onNavigate(SettingsDestination.PersonaDetail(personaId))
                        },
                    )
                }
            }
            is SettingsDestination.ProviderSetup -> {
                ProviderSetupScreen(
                    state = state,
                    onBack = onBack,
                    onSave = onSaveProvider,
                    onClear = onClearProvider,
                )
            }
        }
    }
}
