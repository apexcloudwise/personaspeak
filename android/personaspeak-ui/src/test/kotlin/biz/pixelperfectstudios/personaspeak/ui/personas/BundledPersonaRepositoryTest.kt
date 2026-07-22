package biz.pixelperfectstudios.personaspeak.ui.personas

import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledPersonaRepositoryTest {

    private val bundledPersonasDir: File =
        Paths.get("../..").toAbsolutePath().normalize().resolve("personas").toFile()

    @Test
    fun `lists every bundled persona sorted by source-qualified id`() {
        val repository = BundledPersonaRepository(FakePersonaDocumentSource(bundledPersonasDir))

        assertEquals(
            listOf("bundled:amitabh-bachchan", "bundled:dr-schultz", "bundled:jeeves", "bundled:sir-humphrey"),
            repository.list().getOrThrow().map { it.id.value },
        )
    }

    @Test
    fun `summary display name mirrors the yaml name field`() {
        val repository = BundledPersonaRepository(FakePersonaDocumentSource(bundledPersonasDir))

        val names = repository.list().getOrThrow().associate { it.id.value to it.displayName }

        assertEquals("Jeeves", names["bundled:jeeves"])
        assertEquals("Amitabh Bachchan", names["bundled:amitabh-bachchan"])
    }

    @Test
    fun `load returns a validated bundled persona`() {
        val repository = BundledPersonaRepository(FakePersonaDocumentSource(bundledPersonasDir))

        val persona: ValidatedPersona = repository.load(PersonaId.bundled("jeeves")).getOrThrow()

        assertEquals(PersonaId.bundled("jeeves"), persona.id)
        assertEquals(PersonaProvenance.bundled, persona.provenance)
        assertEquals("Jeeves", persona.content.name)
    }

    @Test
    fun `load fails for a bundled id that is not packaged`() {
        val repository = BundledPersonaRepository(FakePersonaDocumentSource(bundledPersonasDir))

        assertTrue(repository.load(PersonaId("bundled:missing")).isFailure)
    }

    @Test
    fun `load rejects a non-bundled id without opening the document`() {
        val source = TrackingPersonaDocumentSource(FakePersonaDocumentSource(bundledPersonasDir))
        val repository = BundledPersonaRepository(source)

        assertTrue(repository.load(PersonaId("other:jeeves")).isFailure)
        assertEquals(0, source.openCalls)
    }

    private class FakePersonaDocumentSource(private val dir: File) : PersonaDocumentSource {
        override fun slugs(): Result<List<String>> = runCatching {
            dir.listFiles { file -> file.isFile && file.extension == "yaml" }
                ?.map { it.nameWithoutExtension }
                ?: emptyList()
        }

        override fun open(slug: String): Result<InputStream> = runCatching {
            File(dir, "$slug.yaml").inputStream()
        }
    }

    private class TrackingPersonaDocumentSource(private val delegate: PersonaDocumentSource) : PersonaDocumentSource {
        var openCalls: Int = 0
            private set

        override fun slugs(): Result<List<String>> = delegate.slugs()

        override fun open(slug: String): Result<InputStream> {
            openCalls += 1
            return delegate.open(slug)
        }
    }
}
