package biz.pixelperfectstudios.personaspeak.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfig
import kotlinx.coroutines.flow.first

/** Non-secret metadata side of the two-artifact store. */
data class ProviderMeta(
    val providerId: String,
    val configuredAtEpochMs: Long,
    val schemaVersion: Int,
    val generation: String,
    val model: String? = null,
)

/** Seam over the DataStore half so the recovery matrix is testable on the JVM. */
interface MetaStore {
    suspend fun read(): ProviderMeta?
    suspend fun write(meta: ProviderMeta)
    suspend fun clear()
}

private val Context.providerConfigDataStore by preferencesDataStore(
    name = "personaspeak_provider_config",
)

class DataStoreMetaStore(private val context: Context) : MetaStore {

    override suspend fun read(): ProviderMeta? {
        val prefs = context.providerConfigDataStore.data.first()
        val generation = prefs[KEY_GENERATION] ?: return null
        return ProviderMeta(
            providerId = prefs[KEY_PROVIDER_ID] ?: return null,
            configuredAtEpochMs = prefs[KEY_CONFIGURED_AT] ?: return null,
            schemaVersion = prefs[KEY_SCHEMA_VERSION] ?: ProviderConfig.SCHEMA_VERSION,
            generation = generation,
            model = prefs[KEY_MODEL],
        )
    }

    override suspend fun write(meta: ProviderMeta) {
        context.providerConfigDataStore.edit { prefs ->
            prefs[KEY_PROVIDER_ID] = meta.providerId
            prefs[KEY_CONFIGURED_AT] = meta.configuredAtEpochMs
            prefs[KEY_SCHEMA_VERSION] = meta.schemaVersion
            prefs[KEY_GENERATION] = meta.generation
            val model = meta.model
            if (model == null) prefs.remove(KEY_MODEL) else prefs[KEY_MODEL] = model
        }
    }

    override suspend fun clear() {
        context.providerConfigDataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_PROVIDER_ID = stringPreferencesKey("provider_id")
        val KEY_CONFIGURED_AT = longPreferencesKey("configured_at_epoch_ms")
        val KEY_SCHEMA_VERSION = intPreferencesKey("schema_version")
        val KEY_GENERATION = stringPreferencesKey("generation")
        val KEY_MODEL = stringPreferencesKey("model")
    }
}
