package biz.pixelperfectstudios.personaspeak.ui.rewrite

import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.ui.editor.StaleReason

sealed interface RewritePanelState {
    data class Resting(
        val persona: ValidatedPersona,
        val mood: Mood,
    ) : RewritePanelState

    data class PersonaPicker(
        val personas: List<ValidatedPersona>,
        val selectedId: PersonaId,
        val currentMood: Mood,
    ) : RewritePanelState

    data class MoodPicker(
        val moods: List<Mood>,
        val selectedMood: Mood,
        val currentPersona: ValidatedPersona,
    ) : RewritePanelState

    data class Loading(
        val persona: ValidatedPersona,
        val mood: Mood,
    ) : RewritePanelState

    data class Review(
        val persona: ValidatedPersona,
        val mood: Mood,
        val candidate: RewriteCandidate,
        val outcome: RewriteOutcome? = null,
    ) : RewritePanelState

    data class Applying(
        val persona: ValidatedPersona,
        val mood: Mood,
        val candidate: RewriteCandidate,
    ) : RewritePanelState

    data class AppliedVerified(
        val persona: ValidatedPersona,
        val mood: Mood,
        val candidate: RewriteCandidate,
    ) : RewritePanelState

    data class Error(
        val error: StitchError,
        val persona: ValidatedPersona,
        val mood: Mood,
    ) : RewritePanelState
}

sealed interface RewriteOutcome {
    data object Applied : RewriteOutcome
    data class Stale(val reason: StaleReason) : RewriteOutcome
    data object Rejected : RewriteOutcome
    data object Unconfirmed : RewriteOutcome
}

/**
 * The 14 typed error states from the Stitch screen contract.
 */
sealed interface StitchError {
    val title: String
    val explanation: String
    val canRetry: Boolean
    val opensSettings: Boolean
    val editorUntouched: Boolean

    data object EmptyInput : StitchError {
        override val title = "Nothing to rewrite"
        override val explanation = "Type some text in the editor first."
        override val canRetry = false
        override val opensSettings = false
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object NoProvider : StitchError {
        override val title = "No AI provider"
        override val explanation = "No AI provider configured."
        override val canRetry = false
        override val opensSettings = true
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object MissingOrInvalidKey : StitchError {
        override val title = "Invalid API key"
        override val explanation = "Selected provider rejected or lacks credentials."
        override val canRetry = false
        override val opensSettings = true
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object Offline : StitchError {
        override val title = "No connection"
        override val explanation = "I am afraid the internet has deserted us, sir. Cloud rewrites need a connection."
        override val canRetry = true
        override val opensSettings = false
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object RateLimitedOrQuota : StitchError {
        override val title = "Quota exhausted"
        override val explanation = "Provider limit or quota reached."
        override val canRetry = false
        override val opensSettings = true
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object ProviderFailure : StitchError {
        override val title = "Service unavailable"
        override val explanation = "Rewriting service is unavailable."
        override val canRetry = true
        override val opensSettings = false
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object MalformedResponse : StitchError {
        override val title = "Empty response"
        override val explanation = "The rewrite came back empty or malformed."
        override val canRetry = true
        override val opensSettings = false
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object StaleEditor : StitchError {
        override val title = "Editor text changed"
        override val explanation = "Text changed before the rewrite could be applied."
        override val canRetry = true
        override val opensSettings = false
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object WriteRejected : StitchError {
        override val title = "Write rejected"
        override val explanation = "The editor rejected the rewrite."
        override val canRetry = false
        override val opensSettings = false
        override val editorUntouched = false
        override fun toString() = explanation
    }

    data object WriteUnconfirmed : StitchError {
        override val title = "Write unconfirmed"
        override val explanation = "Rewrite applied but couldn't be confirmed. Please check the text field."
        override val canRetry = false
        override val opensSettings = false
        override val editorUntouched = false
        override fun toString() = explanation
    }

    data object SensitiveEditor : StitchError {
        override val title = "Private field"
        override val explanation = "Can't rewrite password or private fields."
        override val canRetry = false
        override val opensSettings = false
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object UnsupportedEditor : StitchError {
        override val title = "Unsupported field"
        override val explanation = "This field doesn't support rewriting."
        override val canRetry = false
        override val opensSettings = false
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object IncompleteRead : StitchError {
        override val title = "Incomplete read"
        override val explanation = "Couldn't read the complete text."
        override val canRetry = false
        override val opensSettings = false
        override val editorUntouched = true
        override fun toString() = explanation
    }

    data object OversizedInput : StitchError {
        override val title = "Text too long"
        override val explanation = "Text is too long to rewrite (exceeds 8,000 code points)."
        override val canRetry = false
        override val opensSettings = false
        override val editorUntouched = true
        override fun toString() = explanation
    }
}
