package biz.pixelperfectstudios.personaspeak.personas

/**
 * Builds the system prompt for a persona rewrite.
 *
 * Must produce byte-identical output to `build_system_prompt()` in
 * desktop/personaspeak.py — the golden tests in tests/golden/ pin this.
 * The construction order is normative: docs/persona-schema.md §Prompt construction.
 */
object PromptBuilder {

    fun build(persona: Persona): String {
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

        lines.add("")
        lines.add(
            "Rewrite the user's message fully in this voice, preserving its original meaning. " +
                "Output only the rewritten text — no preamble, no explanation, no quotation marks around it."
        )
        return lines.joinToString("\n")
    }
}
