package biz.pixelperfectstudios.personaspeak.personas

@JvmInline
value class MoodId(val value: String) {
    init {
        require(value.matches(Regex("[a-z0-9][a-z0-9._-]*"))) {
            "invalid mood id '$value'"
        }
    }

    companion object {
        val POLITE = MoodId("polite")
        val WITTY = MoodId("witty")
        val BLUNT = MoodId("blunt")
        val APOLOGETIC = MoodId("apologetic")
        val FORMAL = MoodId("formal")
    }
}

data class Mood(
    val id: MoodId,
    val label: String,
    val promptModifier: String,
) {
    companion object {
        val Polite = Mood(
            id = MoodId.POLITE,
            label = "Polite",
            promptModifier = "Tone modifier: keep the rewrite polite, courteous, and respectful.",
        )
        val Witty = Mood(
            id = MoodId.WITTY,
            label = "Witty",
            promptModifier = "Tone modifier: keep the rewrite witty, sharp, and playfully clever.",
        )
        val Blunt = Mood(
            id = MoodId.BLUNT,
            label = "Blunt",
            promptModifier = "Tone modifier: keep the rewrite direct, blunt, and unvarnished.",
        )
        val Apologetic = Mood(
            id = MoodId.APOLOGETIC,
            label = "Apologetic",
            promptModifier = "Tone modifier: keep the rewrite humble, apologetic, and deferential.",
        )
        val Formal = Mood(
            id = MoodId.FORMAL,
            label = "Formal",
            promptModifier = "Tone modifier: keep the rewrite formal, professional, and decorous.",
        )

        val ALL: List<Mood> = listOf(Polite, Witty, Blunt, Apologetic, Formal)
        val DEFAULT: Mood = Polite

        fun fromId(id: MoodId): Mood = ALL.firstOrNull { it.id == id } ?: DEFAULT
        fun fromId(idString: String): Mood = ALL.firstOrNull { it.id.value == idString } ?: DEFAULT
    }
}
