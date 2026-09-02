package biz.pixelperfectstudios.personaspeak.personas

/**
 * The one incoming message the keyboard is currently replying to.
 *
 * Pure data: no Android types. The message text itself travels to providers as
 * the user turn (parallel to `rewrite`'s draft); this context carries the
 * metadata the suggestion prompt needs — who it came from and in which app.
 * [text] rides along so the strip and the store can show it without a second
 * type; the prompt builder deliberately does not embed it.
 */
data class IncomingMessageContext(
    /** Display name of the sender, or null when the notification carried none. */
    val sender: String?,
    /** Human-readable app label the message arrived in (fallback: package name). */
    val appLabel: String,
    /** The message text. RAM-only by contract (ADR-0011); never logged or persisted. */
    val text: String,
)
