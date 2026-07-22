package biz.pixelperfectstudios.personaspeak.ui.personas

import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.PersonaValidator
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona

class BundledPersonaRepository(private val source: PersonaDocumentSource) : PersonaRepository {

    override fun list(): Result<List<PersonaSummary>> = runCatching {
        source.slugs().getOrThrow()
            .map { slug -> validate(slug) }
            .map { persona -> PersonaSummary(persona.id, persona.content.name) }
            .sortedBy { it.id.value }
    }

    override fun load(id: PersonaId): Result<ValidatedPersona> = runCatching {
        require(id.value.startsWith(BUNDLED_PREFIX)) { "unknown persona source '${id.value.substringBefore(':')}'" }
        val slug = id.value.removePrefix(BUNDLED_PREFIX)
        validate(slug)
    }

    private fun validate(slug: String): ValidatedPersona {
        val id = PersonaId.bundled(slug)
        val persona = source.open(slug).map { stream -> stream.use(Persona::fromYaml) }.getOrThrow()
        return PersonaValidator.validate(id, PersonaProvenance.bundled, persona).getOrThrow()
    }

    private companion object {
        const val BUNDLED_PREFIX = "bundled:"
    }
}
