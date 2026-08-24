package biz.pixelperfectstudios.personaspeak.ui.settings

import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.PersonaId

/**
 * In-memory non-persistent session state holder for PersonaSpeak selections
 * shared across the running IME session and the Settings activity.
 *
 * Persists nothing to disk (storage and Keystore arrive in Milestone 4).
 */
class PersonaSpeakSessionState(
    initialPersonaId: PersonaId = PersonaId.bundled("jeeves"),
    initialMood: Mood = Mood.DEFAULT,
) {
    @Volatile
    var activePersonaId: PersonaId = initialPersonaId

    @Volatile
    var defaultMood: Mood = initialMood

    fun reset(
        personaId: PersonaId = PersonaId.bundled("jeeves"),
        mood: Mood = Mood.DEFAULT,
    ) {
        activePersonaId = personaId
        defaultMood = mood
    }

    companion object {
        val instance = PersonaSpeakSessionState()
    }
}
