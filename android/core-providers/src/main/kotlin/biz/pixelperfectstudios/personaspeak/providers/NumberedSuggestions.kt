package biz.pixelperfectstudios.personaspeak.providers

/**
 * Pure parser for the N-numbered-replies contract carried inside a suggestion
 * prompt (ADR-0011): one completion in, up to [NumberedSuggestions.parse]'s
 * [count] reply strings out.
 *
 * Lenient by design — real models decorate numbered lists in the wild:
 * - Leading list markers `1.` / `1)` / `-` / `*` / `•` are stripped.
 * - Blank lines and lines that are only a marker are dropped.
 * - Lines beyond [count] are ignored; fewer than [count] parseable lines is a
 *   success carrying what came back.
 * - Zero parseable lines is a failure — never fabricate a reply.
 */
object NumberedSuggestions {

    private val LIST_MARKER = Regex("""^(?:\d{1,2}[.)]|[-*•])\s*""")

    fun parse(completion: String, count: Int): Result<List<String>> {
        require(count >= 1) { "suggestion count must be at least 1" }

        val replies = completion.lines()
            .map { it.trim().replaceFirst(LIST_MARKER, "") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(count)

        return if (replies.isEmpty()) {
            Result.failure(IllegalStateException("no parseable suggestions in completion"))
        } else {
            Result.success(replies)
        }
    }
}
