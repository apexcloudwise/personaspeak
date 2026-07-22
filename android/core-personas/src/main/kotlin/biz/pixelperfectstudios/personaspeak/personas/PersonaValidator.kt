package biz.pixelperfectstudios.personaspeak.personas

object PersonaValidator {
    fun validate(
        id: PersonaId,
        provenance: PersonaProvenance,
        persona: Persona,
    ): Result<ValidatedPersona> = runCatching {
        require(persona.schemaVersion == 1) {
            "unsupported schema_version ${persona.schemaVersion}"
        }
        require(persona.name.isNotBlank()) { "name is required" }
        require(persona.speechPatterns.isNotEmpty()) {
            "speech_patterns is required and must be non-empty"
        }
        require(!persona.realPerson || persona.notes.isNotBlank()) {
            "real_person is true, so notes must declare a stylistic homage"
        }
        ValidatedPersona(
            id = id,
            provenance = provenance,
            content = persona.copy(
                speechPatterns = persona.speechPatterns.toList(),
                vocabulary = persona.vocabulary.toList(),
                sampleLines = persona.sampleLines.toList(),
            ),
        )
    }
}
