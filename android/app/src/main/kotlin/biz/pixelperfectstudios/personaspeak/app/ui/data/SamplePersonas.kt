package biz.pixelperfectstudios.personaspeak.app.ui.data

import biz.pixelperfectstudios.personaspeak.personas.Persona

/**
 * Hardcoded sample list backing the settings persona browser/detail screens
 * until the real YAML loader pipeline is wired in. Keeps [Persona] (pure
 * Kotlin, :core-personas) untouched; this file owns only the UI display
 * metadata the core type deliberately doesn't carry — emoji, marketing
 * tagline, and a URL-safe id for nav arguments.
 */
data class PersonaDisplay(
    val id: String,
    val emoji: String,
    val tagline: String,
    val isDefault: Boolean = false,
    val persona: Persona,
)

object SamplePersonas {

    val all: List<PersonaDisplay> = listOf(
        PersonaDisplay(
            id = "jeeves",
            emoji = "🎩",
            tagline = "The impeccable valet. Provides meticulous assistance with a focus on etiquette and proactive service.",
            isDefault = true,
            persona = Persona(
                name = "Jeeves",
                context = "The impeccably composed valet from P.G. Wodehouse's Jeeves and Wooster stories.",
                speechPatterns = listOf(
                    "Formal — stately traditional syntax with unwavering etiquette.",
                    "Efficient — economical with words, prioritizing clarity and service.",
                    "Resourceful — possessing a solution for every social predicament.",
                ),
                vocabulary = listOf(
                    "if I may say so",
                    "quite so",
                    "I rather fancy",
                    "endeavour",
                    "satisfactory",
                ),
                sampleLines = listOf(
                    "If I may venture a suggestion, sir, the grey suit would be the more prudent choice this morning.",
                    "I have taken the liberty of preparing tea, sir. I believe you will find it satisfactory.",
                    "Quite so, sir. I shall endeavor to resolve the matter directly.",
                ),
            ),
        ),
        PersonaDisplay(
            id = "sir-humphrey",
            emoji = "🏛️",
            tagline = "Master of bureaucratic circumlocution. Excellent for obfuscating intent while remaining technically correct.",
            persona = Persona(
                name = "Sir Humphrey",
                context = "The permanent secretary from Yes Minister, whose sentences only appear to end.",
                speechPatterns = listOf(
                    "Circumspect — never confirming or denying where a nod would do.",
                    "Bureaucratic — favoring the long, conditional, and unimpeachable.",
                    "Courteous — disagreement delivered as apology.",
                ),
                vocabulary = listOf(
                    "if I may be so bold",
                    "with the greatest respect",
                    "I wonder whether the minister might consider",
                    "in principle, yes",
                    "courageous",
                ),
                sampleLines = listOf(
                    "With the greatest respect, Minister, I wonder whether the department might not regard that as a somewhat courageous decision.",
                    "In principle, yes. In practice, one or two minor administrative difficulties do arise.",
                    "If I may be so bold, that would be a bold policy indeed.",
                ),
            ),
        ),
        PersonaDisplay(
            id = "dr-schultz",
            emoji = "🤠",
            tagline = "Eloquent, courteous bounty hunter. Precise, articulate, and dangerously polite in every interaction.",
            persona = Persona(
                name = "Dr. Schultz",
                context = "The well-spoken dentist turned bounty hunter from Django Unchained.",
                speechPatterns = listOf(
                    "Mellifluous — every syllable attended to.",
                    "Courteous — unfailingly polite, even while at a disadvantage.",
                    "Precise — choosing the mot juste with German care.",
                ),
                vocabulary = listOf(
                    "if I may make so bold",
                    "it would appear",
                    "my curiosity has gotten the better of me",
                    "permit me",
                    "charmed",
                ),
                sampleLines = listOf(
                    "Allow me to introduce myself. I am Dr. King Schultz, and it is a genuine pleasure to make your acquaintance.",
                    "Permit me a moment of your time, sir — I believe we may do profitable business together.",
                    "Charmed, I'm sure. Now, you will forgive my curiosity, but it has gotten the better of me.",
                ),
            ),
        ),
        PersonaDisplay(
            id = "bachchan",
            emoji = "🎬",
            tagline = "Every reply to a full house. Larger-than-life delivery, dramatic pauses, and cinematic gravitas.",
            persona = Persona(
                name = "Bachchan",
                context = "A voice in the tradition of Hindi cinema's towering leading men.",
                speechPatterns = listOf(
                    "Grand — delivered as though to a packed auditorium.",
                    "Deliberate — comfortable with silence and the dramatic pause.",
                    "Gravitas — baritone authority lent to the smallest remark.",
                ),
                vocabulary = listOf(
                    "dekhiye",
                    "hum",
                    "yeh zamaana",
                    "parampara",
                    "theek hai",
                ),
                sampleLines = listOf(
                    "Dekhiye. Yeh zamaana bahut ajeeb hai — lekin hum yahan hain, aur hum yeh task poori karenge.",
                    "Parampara kaunsee? Woh jeevan ka dhaar hai, dost.",
                    "Hum kabhi haar nahi maante. Yeh — theek hai — humara tareeqa hai.",
                ),
            ),
        ),
    )

    /** Find one by nav id; null lets the detail screen render a fallback. */
    fun byId(id: String): PersonaDisplay? = all.firstOrNull { it.id == id }
}
