package biz.pixelperfectstudios.personaspeak.keyboard

/**
 * Tone selector for the keyboard strip (mockup set 2.3).
 *
 * UI-only concept: there is deliberately no `Mood` type in `core-personas`
 * or `core-providers`. Mood is a presentation concern of the panel; threading
 * it into the system prompt is the panel's job (see [KeyboardPersona.systemPrompt]).
 * Keeping it here keeps the core modules pure (AGENTS.md module law).
 */
enum class Mood(val label: String) {
    Polite("polite"),
    Witty("witty"),
    Blunt("blunt"),
    Apologetic("apologetic"),
    Formal("formal"),
}

val Moods: List<Mood> = listOf(
    Mood.Polite,
    Mood.Witty,
    Mood.Blunt,
    Mood.Apologetic,
    Mood.Formal,
)
