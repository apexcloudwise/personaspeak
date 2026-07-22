package biz.pixelperfectstudios.personaspeak.personas

import org.yaml.snakeyaml.Yaml
import java.io.InputStream

/**
 * One entry from the personas/ directory. Schema contract: docs/persona-schema.md —
 * change that document (and every consumer) before changing this class.
 */
data class Persona(
    val schemaVersion: Int = 1,
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
                schemaVersion = schemaVersion(data["schema_version"]),
                name = data["name"] as? String
                    ?: throw IllegalArgumentException("persona is missing 'name'"),
                context = stringField("context", data["context"]),
                speechPatterns = stringList("speech_patterns", data["speech_patterns"])
                    .ifEmpty { throw IllegalArgumentException("persona is missing 'speech_patterns'") },
                vocabulary = stringList("vocabulary", data["vocabulary"]),
                sampleLines = stringList("sample_lines", data["sample_lines"]),
                notes = stringField("notes", data["notes"]),
                realPerson = booleanField("real_person", data["real_person"]),
            )
        }

        private fun schemaVersion(value: Any?): Int {
            if (value == null) return 1
            if (value is Int) return value
            throw IllegalArgumentException("'schema_version' must be an integer")
        }

        private fun stringList(field: String, value: Any?): List<String> {
            if (value == null) return emptyList()
            val list = value as? List<*>
                ?: throw IllegalArgumentException("'$field' must be a list of strings")
            if (list.any { it !is String }) {
                throw IllegalArgumentException("every '$field' entry must be a string")
            }
            return list.filterIsInstance<String>().toList()
        }

        private fun stringField(field: String, value: Any?): String {
            if (value == null) return ""
            if (value !is String) throw IllegalArgumentException("'$field' must be a string")
            return value
        }

        private fun booleanField(field: String, value: Any?): Boolean {
            if (value == null) return false
            if (value !is Boolean) throw IllegalArgumentException("'$field' must be a boolean")
            return value
        }
    }
}
