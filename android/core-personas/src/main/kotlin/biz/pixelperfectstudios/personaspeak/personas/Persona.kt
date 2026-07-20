package biz.pixelperfectstudios.personaspeak.personas

import org.yaml.snakeyaml.Yaml
import java.io.InputStream

/**
 * One entry from the personas/ directory. Schema contract: docs/persona-schema.md —
 * change that document (and every consumer) before changing this class.
 */
data class Persona(
    val name: String,
    val context: String = "",
    val speechPatterns: List<String>,
    val vocabulary: List<String> = emptyList(),
    val sampleLines: List<String> = emptyList(),
    val notes: String = "",
    val realPerson: Boolean = false,
) {
    companion object {
        fun fromYaml(input: InputStream): Persona {
            val data: Map<String, Any?> = Yaml().load(input)
                ?: throw IllegalArgumentException("empty persona yaml")
            return Persona(
                name = data["name"] as? String
                    ?: throw IllegalArgumentException("persona is missing 'name'"),
                context = data["context"] as? String ?: "",
                speechPatterns = stringList(data["speech_patterns"])
                    .ifEmpty { throw IllegalArgumentException("persona is missing 'speech_patterns'") },
                vocabulary = stringList(data["vocabulary"]),
                sampleLines = stringList(data["sample_lines"]),
                notes = data["notes"] as? String ?: "",
                realPerson = data["real_person"] as? Boolean ?: false,
            )
        }

        private fun stringList(value: Any?): List<String> =
            (value as? List<*>)?.map { it.toString() } ?: emptyList()
    }
}
