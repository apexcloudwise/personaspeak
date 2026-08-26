package biz.pixelperfectstudios.personaspeak.ui.settings

import biz.pixelperfectstudios.personaspeak.personas.PersonaId

/**
 * Supported navigation destinations within PersonaSpeak Settings.
 */
sealed interface SettingsDestination {
    /** Settings home screen displaying all groups (CHARACTERS, THE BRAIN, TYPING). */
    data object Home : SettingsDestination

    /** Full persona library browser displaying all available personas. */
    data object Personas : SettingsDestination

    /** Detailed persona dossier for a single character. */
    data class PersonaDetail(val personaId: PersonaId) : SettingsDestination

    /** Provider setup screen for configuring AI brain provider credentials and model. */
    data object ProviderSetup : SettingsDestination
}
