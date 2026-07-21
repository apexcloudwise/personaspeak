package biz.pixelperfectstudios.personaspeak.keyboard

/**
 * State machine for the transform flow driving the floating card area
 * (mockup set 2.4 / 2.5 / 2.7-2.9, plus the blank-input error from 6.1).
 *
 * The panel holds exactly one of these at a time; [ResultCard] renders the
 * Loading / Success / Error variants. Idle shows no card.
 */
sealed interface TransformState {
    /** Nothing in flight, nothing to show. */
    data object Idle : TransformState

    /** [provider.rewrite] is running; show the shimmer + caption. */
    data object Loading : TransformState

    /** Rewrite returned; show the result card with Use this / Again / dismiss. */
    data class Success(val text: String) : TransformState

    /**
     * In-voice failure or blank-input guard. Rendered inline, not modal
     * (per the gap analysis: snackbar/toast register, never a dialog).
     */
    data class Error(val message: String) : TransformState
}
