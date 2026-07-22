package biz.pixelperfectstudios.personaspeak.ui.personas

import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona

data class PersonaSummary(val id: PersonaId, val displayName: String)

interface PersonaRepository {
    fun list(): Result<List<PersonaSummary>>
    fun load(id: PersonaId): Result<ValidatedPersona>
}
