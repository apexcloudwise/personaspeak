package biz.pixelperfectstudios.personaspeak.ui.personas

import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona

data class PersonaSummary(val id: PersonaId, val displayName: String)

interface PersonaRepository {
    fun list(): Result<List<PersonaSummary>>
    fun loadAll(): Result<List<ValidatedPersona>> = list().map { summaries ->
        summaries.mapNotNull { summary -> load(summary.id).getOrNull() }
    }
    fun load(id: PersonaId): Result<ValidatedPersona>
}
