package biz.pixelperfectstudios.personaspeak.personas

@JvmInline
value class PersonaSourceId(val value: String) {
    init {
        require(value.matches(Regex("[a-z0-9][a-z0-9._-]*"))) {
            "invalid persona source id '$value'"
        }
    }
}

@JvmInline
value class PersonaId(val value: String) {
    init {
        require(value.matches(Regex("[a-z0-9][a-z0-9._-]*:[a-z0-9][a-z0-9._-]*"))) {
            "persona id must be source-qualified"
        }
    }

    companion object {
        fun bundled(slug: String): PersonaId = PersonaId("bundled:$slug")
    }
}

data class PersonaProvenance(
    val source: PersonaSourceId,
    val origin: String,
    val licenseId: String?,
) {
    companion object {
        val bundled = PersonaProvenance(
            source = PersonaSourceId("bundled"),
            origin = "PersonaSpeak bundled personas",
            licenseId = "CC-BY",
        )
    }
}

data class ValidatedPersona(
    val id: PersonaId,
    val provenance: PersonaProvenance,
    val content: Persona,
) {
    init {
        val idSource = id.value.substringBefore(':')
        require(idSource == provenance.source.value) {
            "persona id source '$idSource' must match provenance source '${provenance.source.value}'"
        }
    }
}
