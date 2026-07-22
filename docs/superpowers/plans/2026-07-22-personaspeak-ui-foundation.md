# PersonaSpeak UI foundation implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the first-party `:personaspeak-ui` Android library with tested
persona-catalog, rewrite-coordination, and editor-port boundaries while ASK
remains inert and the temporary application baseline stays buildable.

**Architecture:** The dependency direction is
`:personaspeak-ui -> core-personas, core-providers`; the pure core modules do
not import Android, ASK, or the UI library. `:personaspeak-ui` defines the ports
that later ASK and marketplace adapters implement. This slice ships one bundled
persona source, one fakeable editor boundary, and no marketplace, persistence,
provider-key, or ASK implementation.

**Tech Stack:** Kotlin 2.3.10, Android Gradle Plugin 8.13.2, Gradle 9.2.1,
JDK 21, Android SDK 35, JUnit 4, SnakeYAML 2.3.

## Global Constraints

- Start from current `origin/main` after PR #37, currently
  `81835ebd22dad97dd19e4fbef31a240341ba545b`.
- Read `/Users/devkiran/AGENTS.md` and repository `AGENTS.md` before acting.
- Keep `android/` as the only Gradle root.
- ASK stays excluded from `android/settings.gradle.kts`; no ASK task or APK runs
  in this slice.
- Preserve the temporary root `:app` and `:keyboard-stub` unchanged except for
  the root settings and exact CI artifact contract needed to add the new AAR.
- Do not move, copy, or reimplement `MainActivity.Onboarding`, `PersonaPanel`,
  `switchBackToPreviousKeyboard`, the local draft field, or any switch-back
  behavior.
- `core-personas` and `core-providers` remain pure Kotlin with zero
  `android.*` imports.
- The v1 persona YAML schema and prompt construction remain unchanged; all
  existing Python and Kotlin goldens remain byte-identical.
- Persona identity and provenance wrap persona content. They do not enter the
  system prompt.
- `PersonaRepository` is read-only catalog access. It does not own active
  selection, persistence, downloads, network, installation, signatures,
  moderation, trust decisions, or deletion.
- Source identifiers are opaque values, not a closed enum.
- `EditorPort` follows the accepted 2026-07-22 contract. It does not claim
  Android supplies atomic cross-process compare-and-set behavior.
- Drafts, replacements, and provider results are request-scoped in-memory
  values. No code in this slice logs or persists them.
- Do not add a collections, dependency-injection, database, networking, or
  navigation dependency.
- Add one truthful entry to `PATCHNOTES.md`.
- Do not stage `.superpowers/`, the Stitch zip, the exported Stitch directory,
  or `docs/design/stitch-prompt.md` from the protected original checkout.

---

## File structure

- `android/core-personas/.../Persona.kt` — parses the existing
  `schema_version` field and retains prompt content.
- `android/core-personas/.../PersonaIdentity.kt` — source-qualified identity
  and provenance envelope.
- `android/core-personas/.../PersonaValidator.kt` — runtime semantic validation
  and defensive request snapshots.
- `tests/persona-validation/` — malformed/valid YAML fixtures shared by Python
  and Kotlin validator tests.
- `desktop/test_validate_personas.py` — pins Python validation to the shared
  fixtures.
- `android/personaspeak-ui/` — Android library, persona repository, editor port,
  coordinator, and unit tests.
- `android/settings.gradle.kts` — includes `:personaspeak-ui` while leaving ASK
  excluded.
- `.github/workflows/ci.yml` — runs shared validation and the new library tests,
  then requires the current APK plus two AARs exactly.
- `PATCHNOTES.md` — records the shipped boundary.

### Task 1: Make persona parsing and validation a runtime contract

**Files:**

- Modify: `android/core-personas/src/main/kotlin/biz/pixelperfectstudios/personaspeak/personas/Persona.kt`
- Create: `android/core-personas/src/main/kotlin/biz/pixelperfectstudios/personaspeak/personas/PersonaIdentity.kt`
- Create: `android/core-personas/src/main/kotlin/biz/pixelperfectstudios/personaspeak/personas/PersonaValidator.kt`
- Create: `android/core-personas/src/test/kotlin/biz/pixelperfectstudios/personaspeak/personas/PersonaValidatorParityTest.kt`
- Create: `desktop/test_validate_personas.py`
- Create: `tests/persona-validation/valid-minimal.yaml`
- Create: `tests/persona-validation/invalid-real-person-no-notes.yaml`
- Create: `tests/persona-validation/invalid-pattern-entry.yaml`
- Create: `tests/persona-validation/invalid-real-person-type.yaml`
- Create: `tests/persona-validation/unsupported-version.yaml`
- Modify: `desktop/validate_personas.py`

**Interfaces:**

- Produces: `Persona.schemaVersion`, `PersonaId`, `PersonaSourceId`,
  `PersonaProvenance`, `ValidatedPersona`, and `PersonaValidator.validate`.
- Preserves: `PromptBuilder.build(Persona)` and every existing golden byte.

- [ ] **Step 1: Add shared YAML fixtures**

Create `tests/persona-validation/valid-minimal.yaml`:

```yaml
schema_version: 1
name: Test Butler
speech_patterns:
  - Speaks plainly
```

Create `tests/persona-validation/invalid-real-person-no-notes.yaml`:

```yaml
schema_version: 1
name: Unqualified Homage
speech_patterns:
  - Sounds authoritative
real_person: true
```

Create `tests/persona-validation/invalid-pattern-entry.yaml`:

```yaml
schema_version: 1
name: Broken List
speech_patterns:
  - Valid entry
  - 42
```

Create `tests/persona-validation/invalid-real-person-type.yaml`:

```yaml
schema_version: 1
name: Ambiguous Biography
speech_patterns:
  - Speaks ambiguously
real_person: "sometimes"
```

Create `tests/persona-validation/unsupported-version.yaml`:

```yaml
schema_version: 2
name: Future Persona
speech_patterns:
  - Arrived before its parser
```

Create `tests/persona-validation/invalid-fractional-version.yaml`:

```yaml
schema_version: 1.5
name: Fractional Butler
speech_patterns:
  - Arrived between parsers
```

- [ ] **Step 2: Pin Python validation to the shared fixtures**

Create `desktop/test_validate_personas.py`:

```python
import unittest
from pathlib import Path

from desktop.validate_personas import validate


FIXTURES = Path(__file__).parent.parent / "tests" / "persona-validation"


class PersonaValidationTest(unittest.TestCase):
    def test_valid_minimal(self) -> None:
        self.assertEqual([], validate(FIXTURES / "valid-minimal.yaml"))

    def test_real_person_requires_notes(self) -> None:
        errors = validate(FIXTURES / "invalid-real-person-no-notes.yaml")
        self.assertTrue(any("notes" in error for error in errors), errors)

    def test_pattern_entries_are_strings(self) -> None:
        errors = validate(FIXTURES / "invalid-pattern-entry.yaml")
        self.assertTrue(any("entry must be a string" in error for error in errors), errors)

    def test_real_person_is_boolean(self) -> None:
        errors = validate(FIXTURES / "invalid-real-person-type.yaml")
        self.assertTrue(any("real_person" in error for error in errors), errors)

    def test_schema_version_is_rejected(self) -> None:
        errors = validate(FIXTURES / "unsupported-version.yaml")
        self.assertTrue(any("unsupported schema_version 2" in error for error in errors), errors)

    def test_schema_version_must_be_an_integer(self) -> None:
        errors = validate(FIXTURES / "invalid-fractional-version.yaml")
        self.assertTrue(any("schema_version' must be an integer" in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
```

Run from the repository root:

```bash
python3 -m unittest desktop/test_validate_personas.py
```

Before running the test, extend `desktop/validate_personas.py` to reject a
present `real_person` value unless `isinstance(value, bool)`, and reject a
present `schema_version` unless `type(value) is int` so Python booleans and
floating-point values do not masquerade as schema integers. Expected: six tests
pass.

- [ ] **Step 3: Write failing Kotlin parity tests**

Create `PersonaValidatorParityTest.kt` with tests that:

```kotlin
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
fun `fractional schema version fails parsing`() {
    val error = assertFailsWith<IllegalArgumentException> {
        fixtures.resolve("invalid-fractional-version.yaml")
            .inputStream().use(Persona::fromYaml)
    }
    assertTrue(error.message!!.contains("'schema_version' must be an integer"))
}
```

Run:

```bash
cd android
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :core-personas:test --tests '*PersonaValidatorParityTest' --no-daemon
```

Expected: compilation fails because the new identity and validator types do not
exist.

- [ ] **Step 4: Parse `schema_version` and reject malformed list types**

Add `schemaVersion: Int = 1` to `Persona`. In `fromYaml`, parse it from a
`Number` and replace the current coercing `stringList` helper with a strict
helper:

```kotlin
private fun stringList(field: String, value: Any?): List<String> {
    if (value == null) return emptyList()
    val list = value as? List<*>
        ?: throw IllegalArgumentException("'$field' must be a list of strings")
    if (list.any { it !is String }) {
        throw IllegalArgumentException("every '$field' entry must be a string")
    }
    return list.filterIsInstance<String>().toList()
}
```

Use it for `speech_patterns`, `vocabulary`, and `sample_lines`. Reject non-string
`context` and `notes`, reject a present non-boolean `real_person`, and accept
`schema_version` only from a YAML integer scalar that fits exactly in an `Int`.
In particular, reject `Float`/`Double` values such as `1.5`, booleans,
out-of-range values, and non-numbers with `"'schema_version' must be an
integer"`; do not call `Number.toInt()` until those checks pass. Do not add
identity or provenance to the YAML-backed `Persona` content class.

- [ ] **Step 5: Add source-neutral identity and provenance values**

Create `PersonaIdentity.kt`:

```kotlin
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
)
```

These are pure values. Do not add marketplace-specific source constants or
network behavior.

- [ ] **Step 6: Implement runtime semantic validation**

Create `PersonaValidator.kt`:

```kotlin
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
```

The copies detach the request snapshot from SnakeYAML collections. Do not claim
Kotlin `List` is deeply immutable and do not cast it back to a mutable type in a
test.

- [ ] **Step 7: Run validation and prompt regression tests**

```bash
python3 desktop/validate_personas.py
python3 -m unittest desktop/test_validate_personas.py
cd android
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :core-personas:test --no-daemon
```

Expected: four repository personas validate, five Python tests pass, Kotlin
validator tests pass, and existing prompt goldens remain unchanged.

- [ ] **Step 8: Commit the pure persona boundary**

Stage only the Task 1 paths and commit:

```bash
git add \
  android/core-personas/src/main/kotlin/biz/pixelperfectstudios/personaspeak/personas/Persona.kt \
  android/core-personas/src/main/kotlin/biz/pixelperfectstudios/personaspeak/personas/PersonaIdentity.kt \
  android/core-personas/src/main/kotlin/biz/pixelperfectstudios/personaspeak/personas/PersonaValidator.kt \
  android/core-personas/src/test/kotlin/biz/pixelperfectstudios/personaspeak/personas/PersonaValidatorParityTest.kt \
  desktop/validate_personas.py \
  desktop/test_validate_personas.py \
  tests/persona-validation
git diff --cached --check
git commit -m "feat: add validated persona identities"
```

### Task 2: Create the Android library and bundled persona repository

**Files:**

- Modify: `android/settings.gradle.kts`
- Create: `android/personaspeak-ui/build.gradle.kts`
- Create: `android/personaspeak-ui/src/main/AndroidManifest.xml`
- Create: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/personas/PersonaRepository.kt`
- Create: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/personas/PersonaDocumentSource.kt`
- Create: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/personas/AssetPersonaDocumentSource.kt`
- Create: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/personas/BundledPersonaRepository.kt`
- Create: `android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/personas/BundledPersonaRepositoryTest.kt`

**Interfaces:**

- Produces: a read-only `PersonaRepository` and one bundled-assets adapter.
- Does not produce: active selection, persistence, installation, or network
  behavior.

- [ ] **Step 1: Add the library module**

Create `build.gradle.kts` with the Android library and Kotlin Android plugins,
namespace `biz.pixelperfectstudios.personaspeak.ui`, `compileSdk = 35`,
`minSdk = 26`, Java/Kotlin 21, and dependencies on `:core-personas`,
`:core-providers`, JUnit, Kotlin test, and the existing
`libs.coroutines.core` alias as a test dependency. Configure:

```kotlin
sourceSets.getByName("main").assets.srcDir("../../personas")
```

This packages the authoritative root YAML files without copying them. Create a
minimal `<manifest />`, and add `include(":personaspeak-ui")` to root settings.
Do not add Compose dependencies until a later slice creates a Compose surface.

- [ ] **Step 2: Write failing repository tests**

Define a fake `PersonaDocumentSource` backed by the shared repository persona
files. Tests must prove:

```kotlin
assertEquals(
    listOf("bundled:amitabh-bachchan", "bundled:dr-schultz", "bundled:jeeves", "bundled:sir-humphrey"),
    repository.list().getOrThrow().map { it.id.value },
)
assertEquals("Jeeves", repository.load(PersonaId.bundled("jeeves")).getOrThrow().content.name)
assertTrue(repository.load(PersonaId("bundled:missing")).isFailure)
assertTrue(repository.load(PersonaId("other:jeeves")).isFailure)
```

Run the new test task and expect compilation failure because the repository
types do not exist.

- [ ] **Step 3: Implement the read-only catalog port**

Use these signatures:

```kotlin
data class PersonaSummary(val id: PersonaId, val displayName: String)

interface PersonaRepository {
    fun list(): Result<List<PersonaSummary>>
    fun load(id: PersonaId): Result<ValidatedPersona>
}

interface PersonaDocumentSource {
    fun slugs(): Result<List<String>>
    fun open(slug: String): Result<InputStream>
}
```

`AssetPersonaDocumentSource` accepts `AssetManager`, lists root `.yaml` assets,
and opens only a slug found in that list. `BundledPersonaRepository` accepts a
`PersonaDocumentSource`, parses and validates each entry with
`PersonaProvenance.bundled`, sorts by `PersonaId.value`, and rejects non-bundled
IDs without opening a document.

- [ ] **Step 4: Run repository and dependency-direction tests**

```bash
cd android
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :personaspeak-ui:testDebugUnitTest :personaspeak-ui:assembleDebug --no-daemon
! rg -n '^import android\.' core-personas/src core-providers/src
! rg -n 'com\.anysoftkeyboard|com\.menny' personaspeak-ui/src
```

Expected: tests and AAR assembly pass; both searches return no matches.

Also verify the AAR contains exactly the four authoritative YAML assets:

```bash
unzip -Z1 personaspeak-ui/build/outputs/aar/personaspeak-ui-debug.aar \
  | rg '^assets/[^/]+\.yaml$' \
  | sort
```

Expected: `amitabh-bachchan.yaml`, `dr-schultz.yaml`, `jeeves.yaml`, and
`sir-humphrey.yaml`, each under `assets/`, with no copied source directory in
the repository.

- [ ] **Step 5: Commit the catalog boundary**

```bash
git add android/settings.gradle.kts android/personaspeak-ui
git diff --cached --check
git commit -m "feat: add the PersonaSpeak UI library"
```

### Task 3: Define the accepted editor boundary

**Files:**

- Create: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/editor/EditorPort.kt`
- Create: `android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/editor/EditorPortContractTest.kt`

**Interfaces:**

- Produces: `EditorPort.captureSnapshot`, `EditorPort.attemptReplace`, typed
  capture refusals, `EditorSnapshot`, and `ReplaceResult`.
- Consumed later by: the ASK adapter in the atomic unified-build slice.

- [ ] **Step 1: Write the contract test first**

Create a fake port that records calls. Test all capture refusals and replace
results without Android types, including a stale outcome returned from exactly
one replacement attempt. The later ASK adapter tests prove that a stale result
sends no editor mutation command. Run the test and expect compilation failure.

- [ ] **Step 2: Add the editor contract**

Create `EditorPort.kt` with:

```kotlin
@JvmInline value class EditorSessionToken(val value: Long)
@JvmInline value class RequestGeneration(val value: Long)

data class Utf16Selection(val start: Int, val end: Int) {
    init {
        require(start >= 0 && end >= start)
    }
}

data class EditorSnapshot(
    val session: EditorSessionToken,
    val generation: RequestGeneration,
    val draft: String,
    val selection: Utf16Selection,
) {
    init {
        require(draft.codePointCount(0, draft.length) <= 8_000)
        require(selection.end <= draft.length)
    }
}

sealed interface CaptureResult {
    data class Captured(val snapshot: EditorSnapshot) : CaptureResult
    data object EmptyInput : CaptureResult
    data object SensitiveEditor : CaptureResult
    data object UnsupportedEditor : CaptureResult
    data object IncompleteRead : CaptureResult
    data object OversizedInput : CaptureResult
}

sealed interface StaleReason {
    data object SessionChanged : StaleReason
    data object GenerationChanged : StaleReason
    data object TextChanged : StaleReason
    data object SelectionChanged : StaleReason
}

sealed interface ReplaceResult {
    data object AppliedVerified : ReplaceResult
    data class Stale(val reason: StaleReason) : ReplaceResult
    data object WriteRejected : ReplaceResult
    data object WriteUnconfirmed : ReplaceResult
}

interface EditorPort {
    suspend fun captureSnapshot(): CaptureResult
    suspend fun attemptReplace(
        snapshot: EditorSnapshot,
        replacement: String,
    ): ReplaceResult
}
```

The port carries no `InputConnection`, `EditorInfo`, ASK, persistence, logging,
or navigation types.

- [ ] **Step 3: Run and commit the contract tests**

Run `:personaspeak-ui:testDebugUnitTest`, the Android-import scans, and
`git diff --check`, then commit:

```bash
git add android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/editor \
  android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/editor
git diff --cached --check
git commit -m "feat: define the editor port"
```

### Task 4: Coordinate request and user-approved replacement

**Files:**

- Create: `android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewriteCoordinator.kt`
- Create: `android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite/RewriteCoordinatorTest.kt`

**Interfaces:**

- Consumes: `PersonaRepository`, `EditorPort`, `PromptBuilder`, and one injected
  `CompletionProvider`.
- Produces: a two-stage request/apply API so a provider result appears before
  the user taps `Use this`.

- [ ] **Step 1: Write coordinator tests against fakes**

Tests must prove:

- `EmptyInput`, `SensitiveEditor`, `UnsupportedEditor`, `IncompleteRead`, and
  `OversizedInput` do not call the provider;
- unknown or invalid persona does not call the editor or provider;
- a successful request builds the prompt, calls the provider once, and returns
  a candidate containing the original snapshot and replacement;
- provider failure returns a generic `ProviderFailure` without exposing the raw
  exception message;
- requesting a candidate never calls `attemptReplace`;
- applying maps all four `ReplaceResult` variants and never retries.

Run the test and expect compilation failure.

- [ ] **Step 2: Implement the two-stage coordinator**

Use these public outcomes:

```kotlin
data class RewriteCandidate(
    val snapshot: EditorSnapshot,
    val replacement: String,
)

sealed interface RewriteRequestResult {
    data class Ready(val candidate: RewriteCandidate) : RewriteRequestResult
    data object NoPersona : RewriteRequestResult
    data object EmptyInput : RewriteRequestResult
    data object SensitiveEditor : RewriteRequestResult
    data object UnsupportedEditor : RewriteRequestResult
    data object IncompleteRead : RewriteRequestResult
    data object OversizedInput : RewriteRequestResult
    data object ProviderFailure : RewriteRequestResult
}

sealed interface ApplyResult {
    data object AppliedVerified : ApplyResult
    data class Stale(val reason: StaleReason) : ApplyResult
    data object WriteRejected : ApplyResult
    data object WriteUnconfirmed : ApplyResult
}
```

`RewriteCoordinator.request(personaId)` loads the validated persona, captures
the editor, calls `PromptBuilder`, then calls the injected provider only after a
successful capture. It never catches an exception into user-visible raw text.
If provider invocation is guarded by a broad exception handler, catch and
rethrow `CancellationException` before mapping other failures to
`ProviderFailure`; cancellation is control flow, not a provider error.
`RewriteCoordinator.apply(candidate)` calls `attemptReplace` exactly once and
maps the returned type. Neither method stores candidates beyond its call.

- [ ] **Step 3: Run coordinator, golden, and cancellation tests**

Use `runBlocking` from the existing `libs.coroutines.core` test dependency and
a fake provider with no delay. For cancellation, launch the request in the test
scope, have the fake provider signal entry with `CompletableDeferred<Unit>`,
cancel and join the job, assert `job.isCancelled`, and assert its `finally` block
completes a second `CompletableDeferred<Unit>`. The cancellation path must not
produce `ProviderFailure` or call `attemptReplace`.

Run all `:personaspeak-ui` and `:core-*` tests, then commit:

```bash
git add android/personaspeak-ui/src/main/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite \
  android/personaspeak-ui/src/test/kotlin/biz/pixelperfectstudios/personaspeak/ui/rewrite
git diff --cached --check
git commit -m "feat: coordinate guarded rewrites"
```

### Task 5: Make the boundary a CI and review contract

**Files:**

- Modify: `.github/workflows/ci.yml`
- Modify: `PATCHNOTES.md`

**Interfaces:**

- Produces: exact build artifacts and non-regression scans for the temporary
  pre-cutover graph plus the new library.

- [ ] **Step 1: Extend CI tests and exact artifact enumeration**

Add `python -m unittest desktop/test_validate_personas.py`,
`:personaspeak-ui:testDebugUnitTest`, and `:personaspeak-ui:assembleDebug`.
Require exactly these three artifacts, sorted:

```text
./app/build/outputs/apk/debug/app-debug.apk
./keyboard-stub/build/outputs/aar/keyboard-stub-debug.aar
./personaspeak-ui/build/outputs/aar/personaspeak-ui-debug.aar
```

Add first-party scans that fail on Android imports in `core-*`, ASK imports in
`:personaspeak-ui`, and new occurrences of the rejected calls/phrases outside
the quarantined `keyboard-stub` and temporary `app`.

- [ ] **Step 2: Add the patch note**

Add one entry stating that the first-party UI/editor/persona boundaries now
exist and ASK remains inert. Do not claim a working ASK keyboard, marketplace,
provider, persisted selection, or shipping UI.

- [ ] **Step 3: Run the complete deterministic gate**

```bash
python3 desktop/validate_personas.py
python3 desktop/personaspeak.py --list
python3 -m unittest desktop/test_validate_personas.py
cd android
env JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew \
    :core-personas:build \
    :core-providers:build \
    :personaspeak-ui:testDebugUnitTest \
    :personaspeak-ui:assembleDebug \
    :keyboard-stub:assembleDebug \
    :app:assembleDebug \
    :app:testDebugUnitTest \
    --no-daemon
cd ..
git diff --check
! rg -n '\b(TB''D|TO''DO|FIX''ME)\b' \
  android/core-personas android/personaspeak-ui .github/workflows/ci.yml PATCHNOTES.md
! rg -n '^import android\.' android/core-personas/src android/core-providers/src
! rg -n 'com\.anysoftkeyboard|com\.menny' android/personaspeak-ui/src
```

Expected: all commands pass; exactly one current APK and two AARs exist; ASK
produces no output because it remains outside the graph.

- [ ] **Step 4: Commit CI and patch note**

```bash
git add .github/workflows/ci.yml PATCHNOTES.md
git diff --cached --check
git commit -m "ci: verify the PersonaSpeak UI boundary"
```

### Task 6: Prepare immutable review and pull-request evidence

**Files:** No repository changes.

- [ ] **Step 1: Verify final scope**

Record `origin/main` and `HEAD`, the conventional commit list, clean worktree,
`git diff origin/main...HEAD --check`, name-status, and artifact enumeration.
Confirm `android/keyboard/UPSTREAM-MODIFIED.md` and every vendored ASK blob are
unchanged.

- [ ] **Step 2: Request exact-range non-author review**

Send a different model family the immutable base/head SHAs, accepted ADR/spec,
this plan, and deterministic receipts. Require findings grouped as Critical,
Important, and Minor for:

- dependency direction and Android-import purity;
- persona validation parity, identity collisions, provenance separation, and
  unchanged schema/goldens;
- repository single responsibility and marketplace deferrals;
- EditorPort completeness and lack of false atomicity claims;
- request/apply separation, raw-error suppression, and no automatic retry;
- rejected-topology exclusion;
- CI artifact exactness and ASK inertness;
- privacy, logging, persistence, and documentation truthfulness.

Post the verdict on the PR. Do not count a stalled, tool-disabled, author, or
non-evidence-backed response.

- [ ] **Step 3: Open ready-for-review only when clean**

The PR body links issue #41 and tracker #38, records commands and literal
outputs, explains why screenshots/emulator evidence are not applicable to this
non-visual/inert-ASK slice, and names the later atomic cutover. Mark ready only
after CI is green, the different-family review has no Critical or Important
finding, and every actionable thread is resolved.

## Plan self-review

- The plan changes no persona YAML field and preserves every golden.
- Stable identity is source-qualified without making display names keys.
- Provenance is data, not a closed source enum or network API.
- Persona catalog, active selection, downloaded storage, and marketplace
  transport are separate responsibilities.
- The coordinator has a request stage and a user-approved apply stage.
- The accepted editor outcomes and capture refusals have explicit tests.
- ASK remains inert; the rejected app/stub behavior is neither moved nor used as
  evidence.
- Every task has an exact test cycle and independently reviewable commit.
- The final PR meets patch-note, CI, evidence, and non-author grading rules.
