package biz.pixelperfectstudios.personaspeak.data.harness

import android.app.Activity
import android.os.Bundle
import biz.pixelperfectstudios.personaspeak.data.DataStoreProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfig
import biz.pixelperfectstudios.personaspeak.ui.brain.SecretBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY storage verification harness (never in release builds): drives
 * the provider-config store so the disposable-AVD pass can seed, query, clear,
 * and write the backup canary via adb intents. Not a product surface; no UI.
 *
 * Intents:
 *  - action SEED  (extra "key": String)   — save config with the given credential
 *  - action QUERY                          — load and report outcome to logcat
 *  - action CLEAR                          — wipe artifacts
 *  - action CANARY                         — write files/personaspeak_backup_canary.txt
 */
class PersonaspeakStorageHarnessActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = DataStoreProviderConfigStore.create(this, android.os.Build.VERSION.SDK_INT)
        val tag = TAG
        when (intent?.action) {
            ACTION_SEED -> {
                val key = intent.getStringExtra("key") ?: "harness-seeded-credential"
                scope.launch {
                    val outcome = store.save(
                        ProviderConfig("gemini", System.currentTimeMillis()),
                        SecretBytes(key.toByteArray()),
                    )
                    android.util.Log.i(tag, "SEED_DONE $outcome")
                    finish()
                }
            }
            ACTION_QUERY -> scope.launch {
                val snapshot = store.load()
                val secretLen = snapshot.secret?.value?.size ?: 0
                android.util.Log.i(tag, "QUERY_OUTCOME ${snapshot.outcome} secret_len=$secretLen")
                finish()
            }
            ACTION_CLEAR -> scope.launch {
                store.clear()
                android.util.Log.i(tag, "CLEAR_DONE")
                finish()
            }
            ACTION_CANARY -> {
                // Deliberately NOT excluded from backup: positive control for
                // the restore-based exclusion proof.
                val canary = java.io.File(filesDir, CANARY_NAME)
                canary.writeText("backup-canary-${System.currentTimeMillis()}")
                android.util.Log.i(tag, "CANARY_WRITTEN")
                finish()
            }
            else -> {
                android.util.Log.i(tag, "UNKNOWN_ACTION")
                finish()
            }
        }
    }

    companion object {
        const val ACTION_SEED = "biz.pixelperfectstudios.personaspeak.data.harness.SEED"
        const val ACTION_QUERY = "biz.pixelperfectstudios.personaspeak.data.harness.QUERY"
        const val ACTION_CLEAR = "biz.pixelperfectstudios.personaspeak.data.harness.CLEAR"
        const val ACTION_CANARY = "biz.pixelperfectstudios.personaspeak.data.harness.CANARY"
        const val CANARY_NAME = "personaspeak_backup_canary.txt"
        private const val TAG = "PsStorageHarness"
    }
}
