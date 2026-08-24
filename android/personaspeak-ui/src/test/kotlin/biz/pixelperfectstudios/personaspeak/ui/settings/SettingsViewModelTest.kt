package biz.pixelperfectstudios.personaspeak.ui.settings

import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.personas.PersonaSummary
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    private val jeeves = ValidatedPersona(
        id = PersonaId.bundled("jeeves"),
        provenance = PersonaProvenance.bundled,
        content = Persona(
            name = "Jeeves",
            context = "the valet",
            speechPatterns = listOf("Impeccable"),
            sampleLines = listOf("Very good, sir."),
        ),
    )

    private val schultz = ValidatedPersona(
        id = PersonaId.bundled("dr-schultz"),
        provenance = PersonaProvenance.bundled,
        content = Persona(
            name = "Dr. King Schultz",
            context = "the dentist turned bounty hunter",
            speechPatterns = listOf("Eloquent"),
            sampleLines = listOf("Let's proceed."),
        ),
    )

    private val fakeRepo = object : PersonaRepository {
        override fun list(): Result<List<PersonaSummary>> =
            Result.success(listOf(PersonaSummary(jeeves.id, jeeves.content.name), PersonaSummary(schultz.id, schultz.content.name)))

        override fun loadAll(): Result<List<ValidatedPersona>> =
            Result.success(listOf(jeeves, schultz))

        override fun load(id: PersonaId): Result<ValidatedPersona> =
            when (id) {
                jeeves.id -> Result.success(jeeves)
                schultz.id -> Result.success(schultz)
                else -> Result.failure(IllegalArgumentException("Unknown persona $id"))
            }
    }

    @Before
    @After
    fun resetSession() {
        PersonaSpeakSessionState.instance.reset()
    }

    @Test
    fun `initial state loads personas and defaults to Home`() {
        val vm = SettingsViewModel(personasRepo = fakeRepo)
        val state = vm.state.value

        assertEquals(SettingsDestination.Home, state.destination)
        assertEquals(PersonaId.bundled("jeeves"), state.activePersonaId)
        assertEquals(Mood.DEFAULT, state.defaultMood)
        assertEquals(2, state.personas.size)
        assertEquals("Jeeves", state.activePersona?.content?.name)
    }

    @Test
    fun `navigate to Personas updates destination`() {
        val vm = SettingsViewModel(personasRepo = fakeRepo)
        vm.navigateTo(SettingsDestination.Personas)

        assertEquals(SettingsDestination.Personas, vm.state.value.destination)
    }

    @Test
    fun `navigate to PersonaDetail resolves selected persona`() {
        val vm = SettingsViewModel(personasRepo = fakeRepo)
        vm.navigateTo(SettingsDestination.PersonaDetail(schultz.id))

        val state = vm.state.value
        assertEquals(SettingsDestination.PersonaDetail(schultz.id), state.destination)
        assertNotNull(state.selectedDetailPersona)
        assertEquals("Dr. King Schultz", state.selectedDetailPersona?.content?.name)
    }

    @Test
    fun `selectPersona updates activePersonaId and sets initialization notice`() {
        val vm = SettingsViewModel(personasRepo = fakeRepo)
        vm.selectPersona(schultz.id)

        val state = vm.state.value
        assertEquals(schultz.id, state.activePersonaId)
        assertEquals(schultz.id, PersonaSpeakSessionState.instance.activePersonaId)
        assertTrue(state.notice!!.contains("Takes effect on next keyboard initialization"))
    }

    @Test
    fun `selectDefaultMood updates mood and sets notice`() {
        val vm = SettingsViewModel(personasRepo = fakeRepo)
        vm.selectDefaultMood(Mood.Witty)

        val state = vm.state.value
        assertEquals(Mood.Witty, state.defaultMood)
        assertEquals(Mood.Witty, PersonaSpeakSessionState.instance.defaultMood)
        assertTrue(state.notice!!.contains("Takes effect on next keyboard initialization"))
    }

    @Test
    fun `clearNotice clears active notice message`() {
        val vm = SettingsViewModel(personasRepo = fakeRepo)
        vm.selectPersona(schultz.id)
        assertNotNull(vm.state.value.notice)

        vm.clearNotice()
        assertNull(vm.state.value.notice)
    }
}
