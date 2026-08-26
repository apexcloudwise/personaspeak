package biz.pixelperfectstudios.personaspeak.ime

import android.content.Context
import android.os.Build
import biz.pixelperfectstudios.personaspeak.data.DataStoreProviderConfigStore

/**
 * Process-wide brain resolver shared by the IME and the Settings activity, so
 * a key saved in Settings is honored without a process restart once
 * [invalidate] runs.
 */
object PersonaSpeakBrain {

    @Volatile
    private var resolver: ResolvingProvider? = null

    fun provider(context: Context): ResolvingProvider =
        resolver ?: synchronized(this) {
            resolver ?: ResolvingProvider(
                DataStoreProviderConfigStore.create(context.applicationContext, Build.VERSION.SDK_INT),
            ).also { resolver = it }
        }

    fun invalidate() {
        resolver?.invalidate()
    }
}
