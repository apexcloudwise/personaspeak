package biz.pixelperfectstudios.personaspeak.ui.rewrite

import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PromptBuilder
import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import biz.pixelperfectstudios.personaspeak.ui.editor.CaptureResult
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorPort
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSnapshot
import biz.pixelperfectstudios.personaspeak.ui.editor.ReplaceResult
import biz.pixelperfectstudios.personaspeak.ui.editor.StaleReason
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * A captured editor snapshot paired with the provider's replacement text, ready
 * for the user to approve. The snapshot is the full value captured at request
 * time; the coordinator does not retain any reference to it between calls.
 */
data class RewriteCandidate(
    val snapshot: EditorSnapshot,
    val replacement: String,
)

/**
 * Outcome of [RewriteCoordinator.request]. Exactly one [Ready] means a candidate
 * is available for the user to approve; every other variant is a typed refusal
 * or failure that carries no provider body, exception, or candidate payload.
 */
sealed interface RewriteRequestResult {
    data class Ready(val candidate: RewriteCandidate) : RewriteRequestResult
    data object NoPersona : RewriteRequestResult
    data object EmptyInput : RewriteRequestResult
    data object SensitiveEditor : RewriteRequestResult
    data object UnsupportedEditor : RewriteRequestResult
    data object IncompleteRead : RewriteRequestResult
    data object OversizedInput : RewriteRequestResult
    data object ProviderFailure : RewriteRequestResult
    data object MalformedResponse : RewriteRequestResult
}

/**
 * Outcome of [RewriteCoordinator.apply]. Mirrors the editor port's
 * [ReplaceResult] without retry: [AppliedVerified], a typed [Stale] reason,
 * [WriteRejected], or honest-about-uncertainty [WriteUnconfirmed].
 */
sealed interface ApplyResult {
    data object AppliedVerified : ApplyResult
    data class Stale(val reason: StaleReason) : ApplyResult
    data object WriteRejected : ApplyResult
    data object WriteUnconfirmed : ApplyResult
}

/**
 * Coordinates the two-stage persona rewrite: [request] loads the persona,
 * captures the editor, builds the prompt, and asks the provider — returning a
 * candidate the user can see before anything is written. [apply] performs a
 * single replacement attempt from the candidate the user approved.
 *
 * Neither method stores candidates, provider bodies, or exception messages
 * beyond its call. Provider failure (whether returned via `Result.failure` or
 * thrown) collapses to [RewriteRequestResult.ProviderFailure] with no payload;
 * [CancellationException] is rethrown before that mapping because cancellation
 * is control flow, not a provider error.
 *
 * No Android, ASK, persistence, logging, analytics, or navigation types cross
 * this boundary — only the four pure-Kotlin ports it consumes.
 */
class RewriteCoordinator(
    private val personas: PersonaRepository,
    private val editor: EditorPort,
    private val provider: CompletionProvider,
) {

    suspend fun request(personaId: PersonaId): RewriteRequestResult {
        val persona = personas.load(personaId).getOrNull()
            ?: return RewriteRequestResult.NoPersona

        val snapshot = when (val capture = editor.captureSnapshot()) {
            is CaptureResult.Captured -> capture.snapshot
            CaptureResult.EmptyInput -> return RewriteRequestResult.EmptyInput
            CaptureResult.SensitiveEditor -> return RewriteRequestResult.SensitiveEditor
            CaptureResult.UnsupportedEditor -> return RewriteRequestResult.UnsupportedEditor
            CaptureResult.IncompleteRead -> return RewriteRequestResult.IncompleteRead
            CaptureResult.OversizedInput -> return RewriteRequestResult.OversizedInput
        }

        val system = PromptBuilder.build(persona.content)

        val result = try {
            provider.rewrite(system, snapshot.draft)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            return RewriteRequestResult.ProviderFailure
        }

        val replacement = result.getOrElse { return RewriteRequestResult.ProviderFailure }
        if (replacement.isBlank()) return RewriteRequestResult.MalformedResponse

        return RewriteRequestResult.Ready(RewriteCandidate(snapshot, replacement))
    }

    suspend fun apply(candidate: RewriteCandidate): ApplyResult =
        when (val outcome = editor.attemptReplace(candidate.snapshot, candidate.replacement)) {
            is ReplaceResult.AppliedVerified -> ApplyResult.AppliedVerified
            is ReplaceResult.Stale -> ApplyResult.Stale(outcome.reason)
            is ReplaceResult.WriteRejected -> ApplyResult.WriteRejected
            is ReplaceResult.WriteUnconfirmed -> ApplyResult.WriteUnconfirmed
        }
}
