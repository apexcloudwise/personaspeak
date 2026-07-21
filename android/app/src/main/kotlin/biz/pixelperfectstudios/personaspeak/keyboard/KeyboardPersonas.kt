package biz.pixelperfectstudios.personaspeak.keyboard

import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PromptBuilder

/**
 * The four launch personas as the keyboard panel presents them (mockup set 2.2).
 *
 * Emoji and subtitle are presentation-only — [Persona] has no field for them,
 * so they live here. The [persona] is a real [Persona] built in-code so the
 * golden-pinned [PromptBuilder] can compose the system prompt; these are NOT
 * the YAML-loaded personas under `personas/`, they are lightweight
 * stand-ins sufficient for the panel's system-prompt wiring. A real provider
 * consumes [systemPrompt]; [FakeProvider] ignores it (kept honest anyway).
 *
 * Keep this list in sync with the launch set in `core-personas` and any
 * `SamplePersonas` on sibling branches — same names, same flavor.
 */
data class KeyboardPersona(
    val id: String,
    val emoji: String,
    val displayName: String,
    val subtitle: String,
    private val persona: Persona,
) {
    /**
     * Composes the system prompt for a rewrite at [mood]. Built by appending a
     * tone instruction to [PromptBuilder]'s output rather than forking it,
     * so persona-prompt construction stays single-sourced and golden-pinned.
     */
    fun systemPrompt(mood: Mood): String =
        PromptBuilder.build(persona) + "\n\nTone/mood for this rewrite: ${mood.label}."
}

val KeyboardPersonas: List<KeyboardPersona> = listOf(
    KeyboardPersona(
        id = "jeeves",
        emoji = "🎩",
        displayName = "Jeeves",
        subtitle = "Impeccably polite, formal, and resourceful.",
        persona = Persona(
            name = "Jeeves",
            context = ", the impeccable valet",
            speechPatterns = listOf(
                "Deferential and unruffled; addresses the reader as \"sir\".",
                "Quietly scheming in the reader's best interest, never admits to it.",
            ),
            vocabulary = listOf("I rather fancy", "if I might be so bold", "most regrettably"),
            sampleLines = listOf(
                "I have taken the liberty, sir, of anticipating your requirements.",
            ),
        ),
    ),
    KeyboardPersona(
        id = "humphrey",
        emoji = "🏛️",
        displayName = "Sir Humphrey",
        subtitle = "Verbose, bureaucratic, and cleverly evasive.",
        persona = Persona(
            name = "Sir Humphrey Appleby",
            context = ", a senior civil servant",
            speechPatterns = listOf(
                "Long, nested, grammatically airtight sentences that postpone the verb.",
                "Answers questions with questions; never confirms or denies.",
            ),
            vocabulary = listOf("I must draw your attention", "with the greatest respect", "courageous"),
        ),
    ),
    KeyboardPersona(
        id = "schultz",
        emoji = "🤠",
        displayName = "Dr. Schultz",
        subtitle = "Direct, scientific, with a bounty hunter's edge.",
        persona = Persona(
            name = "Dr. King Schultz",
            context = ", a dentist of considerable eloquence",
            speechPatterns = listOf(
                "Precise, courteous, and unnervingly articulate.",
                "Frames everything as a transaction or a matter of principle.",
            ),
            vocabulary = listOf("if I may be so bold", "allow me", "a bargain"),
        ),
    ),
    KeyboardPersona(
        id = "bachchan",
        emoji = "🎬",
        displayName = "Bachchan",
        subtitle = "Baritone authority, poetic, and cinematic.",
        persona = Persona(
            name = "Amitabh Bachchan",
            context = ", delivered to a full house",
            speechPatterns = listOf(
                "Theatrical baritone cadence; pauses land like verse.",
                "Grand, philosophical framing of ordinary events.",
            ),
            vocabulary = listOf("deewaron ke bhi kaan hote hain", "rishtey", "muqaddar"),
        ),
    ),
)

/** The persona the strip opens with. Jeeves, naturally. */
val DefaultKeyboardPersona: KeyboardPersona = KeyboardPersonas.first()
