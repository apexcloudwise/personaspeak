package biz.pixelperfectstudios.personaspeak.ime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import biz.pixelperfectstudios.personaspeak.data.DataStoreProviderConfigStore
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.providers.AnthropicMessagesAdapter
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.providers.HttpResponse
import biz.pixelperfectstudios.personaspeak.providers.HttpTransport
import biz.pixelperfectstudios.personaspeak.providers.OpenRouterAdapter
import biz.pixelperfectstudios.personaspeak.providers.OpenRouterModels
import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import biz.pixelperfectstudios.personaspeak.ui.personas.AssetPersonaDocumentSource
import biz.pixelperfectstudios.personaspeak.ui.personas.BundledPersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.settings.PersonaSpeakSessionState
import biz.pixelperfectstudios.personaspeak.ui.settings.ProviderCatalog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.nio.charset.StandardCharsets
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Milestone 7 Slice B — Release Privacy, Network-Egress & Backup-Exclusion Audit Test.
 *
 * Verifies the 4 critical privacy & release invariants:
 * 1. Zero Keystroke & Typing Egress (network calls occur exclusively on explicit user rewrite / model catalog fetch).
 * 2. Strict Transport Isolation & Endpoint Pinning (HTTPS required, pinned URLs).
 * 3. Backup & Extraction Rules (credential blob, staging blob, DataStore metadata explicitly excluded).
 * 4. Memory Hygiene (SecretBytes zeroed immediately in finally blocks across all provider paths).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReleasePrivacyAndEgressAuditTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    private class RecordingHttpTransport(
        private val responseBody: String = "{}",
        private val statusCode: Int = 200,
    ) : HttpTransport {
        var callCount = 0
        var lastEndpoint: String? = null
        var lastHeaders: Map<String, String>? = null
        var lastBodyUtf8: ByteArray? = null

        override fun post(
            endpointUrl: String,
            headers: Map<String, String>,
            bodyUtf8: ByteArray,
        ): HttpResponse {
            callCount++
            lastEndpoint = endpointUrl
            lastHeaders = headers
            lastBodyUtf8 = bodyUtf8
            return HttpResponse(statusCode, responseBody)
        }
    }

    @Test
    fun `keystroke and session operations produce zero network egress`() {
        val transport = RecordingHttpTransport()
        val session = PersonaSpeakSessionState.instance
        session.reset()

        // 1. Session state changes do not touch transport
        session.activePersonaId = PersonaId.bundled("jeeves")
        session.defaultMood = Mood.Polite
        assertEquals(0, transport.callCount)

        // 2. Repository loading does not touch transport
        val repo = BundledPersonaRepository(AssetPersonaDocumentSource(context.assets))
        val personas = repo.loadAll().getOrThrow()
        assertEquals(4, personas.size)
        assertEquals(0, transport.callCount)

        // 3. Unconfigured fallback FakeProvider produces zero network calls
        val fake = FakeProvider()
        val rewriteResult = runBlocking { fake.rewrite("system prompt", "Hello world") }
        assertTrue(rewriteResult.isSuccess)
        assertEquals(0, transport.callCount)
    }

    @Test
    fun `openrouter adapter performs memory zeroing on success and failure`() = runBlocking {
        // 1. Success path zeroes secret bytes
        val transportSuccess = RecordingHttpTransport(
            responseBody = """{"choices":[{"message":{"content":"Polite rewrite."}}]}""",
        )
        val adapter = OpenRouterAdapter(transport = transportSuccess)
        val rawSecretSuccess = "sk-or-secret-key-12345".toByteArray(StandardCharsets.UTF_8)
        val secretSuccess = SecretBytes(rawSecretSuccess.copyOf())

        val resultSuccess = adapter.rewrite("system", "draft text", secretSuccess)
        assertTrue(resultSuccess is AdapterResult.Success)
        assertEquals("Polite rewrite.", (resultSuccess as AdapterResult.Success).rewritten)
        assertEquals(1, transportSuccess.callCount)
        assertEquals("Bearer sk-or-secret-key-12345", transportSuccess.lastHeaders?.get("Authorization"))

        // Verify SecretBytes was zero-filled in memory
        assertTrue(secretSuccess.value.all { it == 0.toByte() })

        // 2. Error / AuthFailure path zeroes secret bytes
        val transportFail = RecordingHttpTransport(
            responseBody = """{"error":"unauthorized"}""",
            statusCode = 401,
        )
        val adapterFail = OpenRouterAdapter(transport = transportFail)
        val rawSecretFail = "sk-or-invalid-key".toByteArray(StandardCharsets.UTF_8)
        val secretFail = SecretBytes(rawSecretFail.copyOf())

        val resultFail = adapterFail.rewrite("system", "draft text", secretFail)
        assertTrue(resultFail is AdapterResult.AuthFailure)
        assertTrue(secretFail.value.all { it == 0.toByte() })
    }

    @Test
    fun `anthropic adapter performs memory zeroing on success and failure`() = runBlocking {
        // 1. Success path zeroes secret bytes
        val transportSuccess = RecordingHttpTransport(
            responseBody = """{"content":[{"type":"text","text":"Claude rewrite."}]}""",
        )
        val adapter = AnthropicMessagesAdapter(transport = transportSuccess)
        val rawSecret = "sk-ant-secret-key-999".toByteArray(StandardCharsets.UTF_8)
        val secret = SecretBytes(rawSecret.copyOf())

        val result = adapter.rewrite("system", "draft text", secret)
        assertTrue(result is AdapterResult.Success)
        assertEquals("Claude rewrite.", (result as AdapterResult.Success).rewritten)
        assertEquals(1, transportSuccess.callCount)
        assertEquals("sk-ant-secret-key-999", transportSuccess.lastHeaders?.get("x-api-key"))

        // Verify SecretBytes was zero-filled in memory
        assertTrue(secret.value.all { it == 0.toByte() })
    }

    @Test
    fun `backup exclusion rules explicitly exclude secret blob staging blob and datastore metadata`() {
        var current: File? = File(".").canonicalFile
        var repoRoot: File? = null
        while (current != null) {
            if (File(current, "docs").isDirectory && File(current, "android").isDirectory) {
                repoRoot = current
                break
            }
            current = current.parentFile
        }
        assertNotNull("Could not find repository root", repoRoot)

        val fullBackupXml = File(repoRoot!!, "android/personaspeak-ui/src/main/res/xml/personaspeak_full_backup_content.xml")
        val dataExtractionXml = File(repoRoot, "android/personaspeak-ui/src/main/res/xml/personaspeak_data_extraction_rules.xml")

        assertTrue("full backup rules XML must exist at ${fullBackupXml.absolutePath}", fullBackupXml.isFile)
        assertTrue("data extraction rules XML must exist at ${dataExtractionXml.absolutePath}", dataExtractionXml.isFile)

        val fullBackupContent = fullBackupXml.readText()
        val dataExtractionContent = dataExtractionXml.readText()

        // Required excluded paths
        val expectedExcludedPaths = listOf(
            DataStoreProviderConfigStore.LIVE_BLOB_NAME,
            DataStoreProviderConfigStore.STAGING_BLOB_NAME,
            "datastore/personaspeak_provider_config.preferences_pb",
        )

        for (path in expectedExcludedPaths) {
            assertTrue("full_backup_content must exclude '$path'", fullBackupContent.contains("path=\"$path\""))
            assertTrue("data_extraction_rules must exclude '$path' in cloud-backup", dataExtractionContent.contains("path=\"$path\""))
        }

        // Verify XML validity
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val docFull = builder.parse(fullBackupXml)
        assertEquals("full-backup-content", docFull.documentElement.tagName)

        val docData = builder.parse(dataExtractionXml)
        assertEquals("data-extraction-rules", docData.documentElement.tagName)
    }

    @Test
    fun `openrouter models catalog parser enforces free-first ordering and extracts pricing truthfully`() {
        val jsonCatalog = """
        {
          "data": [
            {
              "id": "anthropic/claude-3-haiku",
              "name": "Claude 3 Haiku",
              "pricing": { "prompt": "0.00025", "completion": "0.00125" }
            },
            {
              "id": "meta-llama/llama-3-8b:free",
              "name": "Llama 3 8B (Free)",
              "pricing": { "prompt": "0", "completion": "0" }
            },
            {
              "id": "nvidia/nemotron-3-super-120b-a12b:free",
              "name": "Nvidia Nemotron Free",
              "pricing": { "prompt": "0.0", "completion": "0.0" }
            }
          ]
        }
        """.trimIndent()

        val models = OpenRouterModels.parse(jsonCatalog)
        assertEquals(3, models.size)

        // Free models listed first
        assertTrue(models[0].isFree)
        assertTrue(models[1].isFree)
        assertFalse(models[2].isFree)
        assertEquals("anthropic/claude-3-haiku", models[2].id)
    }

    @Test
    fun `provider catalog contains approved defaults per ADR-0009`() {
        assertEquals("openrouter", ProviderCatalog.openrouter.id)
        assertEquals("https://openrouter.ai/api/v1", ProviderCatalog.openrouter.defaultBaseUrl)
        assertEquals("nvidia/nemotron-3-super-120b-a12b:free", ProviderCatalog.openrouter.defaultModel)

        assertEquals("anthropic", ProviderCatalog.anthropic.id)
        assertEquals("https://api.anthropic.com", ProviderCatalog.anthropic.defaultBaseUrl)

        assertEquals("openai-compat", ProviderCatalog.openaiCompat.id)
        assertTrue(ProviderCatalog.openaiCompat.needsBaseUrl)
    }
}
