package biz.pixelperfectstudios.personaspeak.personas

import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.inputStream
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Parity tests pinning [PersonaValidator] to the same shared fixtures used by
 * desktop/test_validate_personas.py. The fixtures live at the repo root under
 * tests/persona-validation/ so both language contracts fail together if they drift.
 */
class PersonaValidatorParityTest {

    private val repoRoot = Paths.get("../..").toAbsolutePath().normalize()
    private val fixtures = repoRoot.resolve("tests/persona-validation")

    @Test
    fun `valid fixture produces source-qualified identity`() {
        val persona = fixtures.resolve("valid-minimal.yaml").inputStream().use(Persona::fromYaml)
        val validated = PersonaValidator.validate(
            id = PersonaId.bundled("test-butler"),
            provenance = PersonaProvenance(
                source = PersonaSourceId("bundled"),
                origin = "PersonaSpeak bundled personas",
                licenseId = "CC-BY",
            ),
            persona = persona,
        ).getOrThrow()
        assertEquals("bundled:test-butler", validated.id.value)
        assertEquals(1, validated.content.schemaVersion)
    }

    @Test
    fun `real person fixture without notes is rejected`() {
        val persona = fixtures.resolve("invalid-real-person-no-notes.yaml")
            .inputStream().use(Persona::fromYaml)
        val result = PersonaValidator.validate(
            PersonaId.bundled("invalid"),
            PersonaProvenance.bundled,
            persona,
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("notes"))
    }

    @Test
    fun `non-string pattern entry fails parsing`() {
        assertFailsWith<IllegalArgumentException> {
            fixtures.resolve("invalid-pattern-entry.yaml").inputStream().use(Persona::fromYaml)
        }
    }

    @Test
    fun `non-boolean real person flag fails parsing`() {
        assertFailsWith<IllegalArgumentException> {
            fixtures.resolve("invalid-real-person-type.yaml")
                .inputStream().use(Persona::fromYaml)
        }
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val persona = fixtures.resolve("unsupported-version.yaml")
            .inputStream().use(Persona::fromYaml)
        val result = PersonaValidator.validate(
            PersonaId.bundled("future"),
            PersonaProvenance.bundled,
            persona,
        )
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("schema_version 2"))
    }

    @Test
    fun `non-integer schema version fails parsing`() {
        val fractional = assertFailsWith<IllegalArgumentException> {
            fixtures.resolve("invalid-fractional-version.yaml")
                .inputStream().use(Persona::fromYaml)
        }
        assertTrue(fractional.message!!.contains("'schema_version' must be an integer"))

        val explicitNull = assertFailsWith<IllegalArgumentException> {
            Persona.fromYaml(EXPLICIT_NULL_SCHEMA_VERSION.byteInputStream())
        }
        assertTrue(explicitNull.message!!.contains("'schema_version' must be an integer"))
    }

    companion object {
        private const val EXPLICIT_NULL_SCHEMA_VERSION =
            "schema_version: null\nname: Test Butler\nspeech_patterns:\n  - Speaks plainly\n"
    }
}
