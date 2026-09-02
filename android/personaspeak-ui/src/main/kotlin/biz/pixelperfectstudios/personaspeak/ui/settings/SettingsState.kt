package biz.pixelperfectstudios.personaspeak.ui.settings

import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.StoreOutcome

/**
 * State container for the PersonaSpeak Settings surface.
 */
data class SettingsState(
    val destination: SettingsDestination = SettingsDestination.Home,
    val activePersonaId: PersonaId = PersonaId.bundled("jeeves"),
    val personas: List<ValidatedPersona> = emptyList(),
    val defaultMood: Mood = Mood.DEFAULT,
    val selectedDetailPersona: ValidatedPersona? = null,
    val notice: String? = null,
    val providerOutcome: StoreOutcome = StoreOutcome.Unconfigured,
    val lastRewriteResult: AdapterResult? = null,
    val isSavingProvider: Boolean = false,
    val providerStatus: ProviderStatusSummary = ProviderStatusSummary.Unconfigured,
    val suggestedRepliesEnabled: Boolean = false,
) {

    /**
     * Resolves the active [ValidatedPersona] instance from the loaded list if present.
     */
    val activePersona: ValidatedPersona?
        get() = personas.find { it.id == activePersonaId }
}
