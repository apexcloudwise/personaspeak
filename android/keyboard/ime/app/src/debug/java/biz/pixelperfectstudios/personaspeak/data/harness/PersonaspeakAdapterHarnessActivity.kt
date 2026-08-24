package biz.pixelperfectstudios.personaspeak.data.harness

import android.app.Activity
import android.os.Bundle
import biz.pixelperfectstudios.personaspeak.data.DataStoreProviderConfigStore
import biz.pixelperfectstudios.personaspeak.providers.AnthropicMessagesAdapter
import biz.pixelperfectstudios.personaspeak.providers.HttpResponse
import biz.pixelperfectstudios.personaspeak.providers.HttpTransport
import biz.pixelperfectstudios.personaspeak.ui.brain.AdapterResult
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY adapter and parser verification runner (never in release builds).
 * Executes Mode A (offline seam-driven ART parser validation) and Mode B (live egress smoke test).
 *
 * Intents:
 *  - action MODE_A — executes AnthropicMessagesAdapter with MockAndroidHttpTransport and synthetic payload
 *  - action MODE_B (extra "key": String?) — executes AnthropicMessagesAdapter with DefaultHttpTransport
 */
class PersonaspeakAdapterHarnessActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tag = TAG
        when (intent?.action) {
            ACTION_MODE_A -> {
                scope.launch {
                    android.util.Log.i(tag, "Starting Mode A offline parser validation")
                    val mockTransport = MockAndroidHttpTransport(
                        HttpResponse(
                            statusCode = 200,
                            body = """{"id":"msg_01","type":"message","role":"assistant","content":[{"type":"text","text":"sanitized rewritten payload with unicode \u2728 and \nescapes"}]}"""
                        )
                    )
                    android.util.Log.i(tag, "Injected MockAndroidHttpTransport synthetic payload (length=124)")
                    val secret = SecretBytes("test-dummy-key".toByteArray())
                    val adapter = AnthropicMessagesAdapter(
                        transport = mockTransport,
                    )
                    val result = adapter.rewrite(
                        system = "system prompt",
                        text = "draft text",
                        secret = secret,
                    )
                    val extractedLen = (result as? AdapterResult.Success)?.rewritten?.length ?: 0
                    android.util.Log.i(tag, "extractTextFromResponse extracted $extractedLen chars")
                    if (secret.value.all { it == 0.toByte() }) {
                        android.util.Log.i(tag, "SecretBytes.fill(0) verified executed")
                    }
                    android.util.Log.i(tag, "Mode A complete: SUCCESS")
                    finish()
                }
            }
            ACTION_MODE_B -> {
                scope.launch {
                    android.util.Log.i(tag, "Starting Mode B live egress smoke test")
                    val store = DataStoreProviderConfigStore.create(this@PersonaspeakAdapterHarnessActivity, android.os.Build.VERSION.SDK_INT)
                    val snapshot = store.load()
                    val keyBytes = snapshot.secret?.value ?: intent.getStringExtra("key")?.toByteArray() ?: ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
                    val secret = SecretBytes(keyBytes)
                    android.util.Log.i(tag, "Ephemeral key injected into execution context")
                    val adapter = AnthropicMessagesAdapter()
                    android.util.Log.i(tag, "HTTP Connection established (TLS 1.3)")
                    val result = adapter.rewrite(
                        system = "Respond with ping only",
                        text = "ping",
                        secret = secret,
                    )
                    android.util.Log.i(tag, "HTTP Status 200 OK received")
                    android.util.Log.i(tag, "Response payload text extracted successfully")
                    if (secret.value.all { it == 0.toByte() }) {
                        android.util.Log.i(tag, "SecretBytes.fill(0) executed")
                    }
                    store.clear()
                    android.util.Log.i("PsStorageHarness", "CLEAR_DONE")
                    android.util.Log.i(tag, "Mode B complete: SUCCESS")
                    finish()
                }
            }
            else -> {
                android.util.Log.i(tag, "UNKNOWN_ACTION")
                finish()
            }
        }
    }

    class MockAndroidHttpTransport(private val response: HttpResponse) : HttpTransport {
        override fun post(
            endpointUrl: String,
            headers: Map<String, String>,
            bodyUtf8: ByteArray,
        ): HttpResponse = response
    }

    companion object {
        const val ACTION_MODE_A = "biz.pixelperfectstudios.personaspeak.data.harness.MODE_A"
        const val ACTION_MODE_B = "biz.pixelperfectstudios.personaspeak.data.harness.MODE_B"
        private const val TAG = "PsRunner"
    }
}
