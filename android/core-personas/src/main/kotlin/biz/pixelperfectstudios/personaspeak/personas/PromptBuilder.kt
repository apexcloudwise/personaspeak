package biz.pixelperfectstudios.personaspeak.personas

/**
 * Builds the system prompt for a persona rewrite.
 *
 * Must produce byte-identical output to `build_system_prompt()` in
 * desktop/personaspeak.py — the golden tests in tests/golden/ pin this.
 * The construction order is normative: docs/persona-schema.md §Prompt construction.
 */
object PromptBuilder {

    fun build(persona: Persona, mood: Mood? = null): String {
        val lines = mutableListOf(
            "You are a text style-transfer engine. Rewrite the user's message so it " +
                "sounds like it was spoken by ${persona.name}${persona.context}.",
            "",
            "Voice characteristics:",
        )
        persona.speechPatterns.forEach { lines.add("- $it") }

        if (persona.vocabulary.isNotEmpty()) {
            lines.add("")
            lines.add(
                "Characteristic vocabulary/phrases to draw on: " +
                    persona.vocabulary.joinToString(", ")
            )
        }

        if (persona.sampleLines.isNotEmpty()) {
            lines.add("")
            lines.add("Example lines in this voice (for tone/rhythm reference, don't copy them verbatim):")
            persona.sampleLines.forEach { lines.add("- \"$it\"") }
        }

        if (persona.notes.isNotEmpty()) {
            lines.add("")
            lines.add("Notes: ${persona.notes.trim()}")
        }

        if (mood != null) {
            lines.add("")
            lines.add(mood.promptModifier)
        }

        lines.add("")
        lines.add(
            "Rewrite the user's message fully in this voice, preserving its original meaning. " +
                "Output only the rewritten text — no preamble, no explanation, no quotation marks around it."
        )
        return lines.joinToString("\n")
    }

    /**
     * Builds the system prompt for N short suggested replies to an incoming
     * message (Phase 2, ADR-0011).
     *
     * Must produce byte-identical output to `build_suggestion_prompt()` in
     * desktop/personaspeak.py — the `<persona>.suggest.txt` golden fixtures pin
     * this. The message text itself is NOT embedded here: it travels to the
     * provider as the user turn (parallel to `build`/`rewrite`), so this prompt
     * carries only the persona voice, the mood, and the sender/app context.
     */
    fun buildSuggestionPrompt(
        persona: Persona,
        mood: Mood? = null,
        incoming: IncomingMessageContext,
        count: Int = DEFAULT_SUGGESTION_COUNT,
    ): String {
        require(count >= 1) { "suggestion count must be at least 1" }

        val lines = mutableListOf(
            "You are a text style-transfer engine. Draft $count short chat replies to the " +
                "user's most recent incoming message, each sounding like it was spoken by " +
                "${persona.name}${persona.context}.",
            "",
            "Voice characteristics:",
        )
        persona.speechPatterns.forEach { lines.add("- $it") }

        if (persona.vocabulary.isNotEmpty()) {
            lines.add("")
            lines.add(
                "Characteristic vocabulary/phrases to draw on: " +
                    persona.vocabulary.joinToString(", ")
            )
        }

        if (persona.sampleLines.isNotEmpty()) {
            lines.add("")
            lines.add("Example lines in this voice (for tone/rhythm reference, don't copy them verbatim):")
            persona.sampleLines.forEach { lines.add("- \"$it\"") }
        }

        if (persona.notes.isNotEmpty()) {
            lines.add("")
            lines.add("Notes: ${persona.notes.trim()}")
        }

        if (mood != null) {
            lines.add("")
            lines.add(mood.promptModifier)
        }

        lines.add("")
        lines.add(
            if (incoming.sender != null) {
                "The message you are replying to arrived from ${incoming.sender} via ${incoming.appLabel}."
            } else {
                "The message you are replying to arrived via ${incoming.appLabel}."
            }
        )

        lines.add("")
        lines.add(
            "Draft exactly $count distinct short replies in this voice, numbered \"1.\" through " +
                "\"$count.\" — one reply per line, no blank lines between them. Each reply stands " +
                "alone, stays under 200 characters, and is ready to send as-is. Output only the " +
                "numbered replies — no preamble, no explanation, no quotation marks around them."
        )
        return lines.joinToString("\n")
    }

    const val DEFAULT_SUGGESTION_COUNT = 3
}
